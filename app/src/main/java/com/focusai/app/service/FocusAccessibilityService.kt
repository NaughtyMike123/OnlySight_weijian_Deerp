package com.focusai.app.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusai.app.FocusAiApplication
import com.focusai.app.data.api.ApiClientFactory
import com.focusai.app.data.api.ChatCompletionRequest
import com.focusai.app.data.api.ChatMessage
import com.focusai.app.data.prefs.AppCategory
import com.focusai.app.data.prefs.AppSettings
import com.focusai.app.data.prefs.DEFAULT_PROMPT_TEMPLATE
import com.focusai.app.data.prefs.UserAppLists
import com.focusai.app.util.NotificationHelper
import com.focusai.app.util.PromptTemplateRenderer
import com.focusai.app.util.ScreenScene
import com.focusai.app.util.TextExtractor
import com.focusai.app.util.ToastHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI 自律监督无障碍服务。
 *
 * 路由优先级（高 → 低）：
 *  1. 用户自定义名单（黑/白/灰）
 *  2. 内置默认名单
 *  3. 系统 `ApplicationInfo.category` 自动归类（VIDEO/SOCIAL/AUDIO → 灰）
 *  4. 包名关键词启发式（video/tv/live/stream/short → 灰）
 *  5. 未知 → 白名单（隐私安全默认）
 */
class FocusAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var settingsCache: AppSettings = AppSettings()
    @Volatile private var userListsCache: UserAppLists = UserAppLists()

    private var debounceJob: Job? = null
    @Volatile private var lastBlackTriggerAt: Long = 0L
    /** 上次进入灰名单分析的包名，用于在切换 App 时重置指纹缓存。 */
    @Volatile private var lastAnalyzedPackage: String? = null
    /** 上次实际跑过 AI 分析的屏幕指纹，用于去重。 */
    @Volatile private var lastAnalyzedFingerprint: Int = 0
    /** 上次"决定要发起 AI 调用"的时间。仅在强制触发(delay=0)或真正进入 AI 阶段时刷新。 */
    @Volatile private var lastAnalysisAt: Long = 0L
    /** 同一时间是否已有 AI 请求在飞。NonCancellable 段中置 true，结束后置 false。 */
    private val aiCallInFlight = AtomicBoolean(false)
    /** 用于事件入口日志去重 + 切换 App 时取消挂起分析。 */
    @Volatile private var lastForegroundPackage: String? = null
    /** 同包名 + 同事件类型的高频节流时间戳。 */
    @Volatile private var lastEventTimestamp: Long = 0L
    @Volatile private var lastEventType: Int = 0
    @Volatile private var lastEventPackage: String? = null

    /** packageName → category 的轻量缓存，避免反复读 PackageManager。 */
    private val classifyCache = HashMap<String, PackageCategory>()

    private lateinit var apiClientFactory: ApiClientFactory

    // ────────────────────────────── 生命周期 ──────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as FocusAiApplication
        apiClientFactory = app.apiClientFactory

        serviceScope.launch {
            app.settingsRepository.settingsFlow.collectLatest { settingsCache = it }
        }
        serviceScope.launch {
            app.appListRepository.listsFlow.collectLatest {
                userListsCache = it
                classifyCache.clear()
            }
        }

        Log.d(TAG, "无障碍服务已连接 (onServiceConnected)，开始监听屏幕事件。")
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: 系统中断信号，取消防抖任务。")
        debounceJob?.cancel()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: 服务销毁，取消所有协程与 Handler 回调。")
        debounceJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        aiCallInFlight.set(false)
        super.onDestroy()
    }

    // ────────────────────────────── 事件入口 ──────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val settings = settingsCache
        if (!settings.supervisionEnabled) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty()) return

        // 系统覆盖层（状态栏 / 系统弹窗 / ANR 等）：直接忽略，不参与名单路由，
        // 也不取消挂起的灰名单分析。这是修复"systemui 一直推 CONTENT_CHANGED → 防抖永远被取消"的关键。
        if (pkg in TRANSIENT_OVERLAY_PACKAGES) return

        // 命中本应用：只有"真正从其它前台切回本应用"才取消挂起分析。
        // 本应用内部滚动产生的 CONTENT_CHANGED 不再频繁打断 debounce。
        if (pkg == packageName) {
            if (pkg != lastForegroundPackage) {
                if (debounceJob?.isActive == true) {
                    Log.d(TAG, "切回本应用 ($pkg)，取消挂起的灰名单分析")
                    debounceJob?.cancel()
                }
                lastForegroundPackage = pkg
            }
            return
        }

        // 高频事件节流：同 pkg + 同 eventType + 距离上次 < 500ms 直接丢弃
        val now = System.currentTimeMillis()
        val eventType = event.eventType
        if (pkg == lastEventPackage && eventType == lastEventType &&
            now - lastEventTimestamp < SAME_EVENT_THROTTLE_MS
        ) {
            return
        }
        lastEventTimestamp = now
        lastEventType = eventType
        lastEventPackage = pkg

        when (val category = classify(pkg)) {
            PackageCategory.WHITELIST -> {
                // 只有真正切到另一个前台 App（包名变化）才取消挂起分析。
                // 否则系统通知 / 输入法浮窗等白名单事件会反复打断灰名单的 2s 防抖。
                // 即使这里没取消，scheduleAnalysis 的 delay 结束后还会用 root.packageName 做 TOCTOU 校验。
                if (pkg != lastForegroundPackage) {
                    if (debounceJob?.isActive == true) {
                        Log.d(TAG, "切到白名单 ($pkg)，取消挂起的灰名单分析")
                        debounceJob?.cancel()
                    }
                    Log.d(TAG, "触发包名: $pkg -> 属于 ${category.label}（忽略，事件类型=$eventType）")
                    lastForegroundPackage = pkg
                }
            }
            PackageCategory.BLACKLIST -> {
                if (pkg != lastForegroundPackage) {
                    Log.d(TAG, "触发包名: $pkg -> 属于 ${category.label}（直接打断，事件类型=$eventType）")
                    lastForegroundPackage = pkg
                }
                handleBlacklist(pkg)
            }
            PackageCategory.GREYLIST -> {
                if (pkg != lastForegroundPackage) {
                    Log.d(TAG, "触发包名: $pkg -> 属于 ${category.label}（进入防抖分析，事件类型=$eventType）")
                    lastForegroundPackage = pkg
                }
                scheduleAnalysis(pkg)
            }
        }
    }

    // ────────────────────────────── 黑名单处理 ──────────────────────────────

    private fun handleBlacklist(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlackTriggerAt < BLACK_COOLDOWN_MS) return
        lastBlackTriggerAt = now

        debounceJob?.cancel()

        // 立即把当前包名锁进事件节流缓存，避免黑名单冷却期间反复打日志
        lastForegroundPackage = pkg

        triggerHomeIntercept(
            packageName = pkg,
            reasonType = "BLACKLIST",
            reasonDetail = "命中黑名单包名",
            screenTextExcerpt = "",
            aiReply = ""
        )
    }

    // ────────────────────────────── 灰名单防抖 + 分析 ──────────────────────────────

    private fun scheduleAnalysis(pkg: String) {
        if (settingsCache.apiKey.isBlank()) return
        if (pkg != lastAnalyzedPackage) {
            lastAnalyzedPackage = pkg
            lastAnalyzedFingerprint = 0
        }

        val now = System.currentTimeMillis()
        val elapsedSinceLast = now - lastAnalysisAt
        val dynamicDelay = if (elapsedSinceLast >= MAX_ANALYSIS_WAIT_MS) 0L else DEBOUNCE_MS

        // 【方案 A】只在"强制触发"(delay=0) 时把 lastAnalysisAt 同步前移到现在。
        // 这样后续 100~500ms 内的新事件会因为 elapsed 很小 → dynamicDelay=2000，
        // 不会再因为读到旧 lastAnalysisAt 而以 0 延迟疯狂启动新协程。
        // 普通 2s debounce 不更新 lastAnalysisAt，确保 8s 强制触发机制仍然有效。
        if (dynamicDelay == 0L) {
            lastAnalysisAt = now
        }

        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(dynamicDelay)
            if (!isActive) return@launch

            // 防抖结束前先在主线程拿 root，并立刻校验当前前台包名是否还是 pkg
            val root = withContext(Dispatchers.Main) { rootInActiveWindow } ?: return@launch
            val rootPkg = root.packageName?.toString().orEmpty()

            // 切到本应用或其他包：丢弃，防止 AI 看到 FocusAI 自己的界面把账算到原 App 头上
            if (rootPkg == packageName) {
                Log.d(TAG, "防抖结束但当前已是本应用界面，丢弃本次分析（原包名=$pkg）")
                withContext(Dispatchers.Main) { runCatching { root.recycle() } }
                return@launch
            }
            if (rootPkg.isNotEmpty() && rootPkg != pkg) {
                Log.d(TAG, "防抖结束但前台已切换：$pkg -> $rootPkg，丢弃本次分析")
                withContext(Dispatchers.Main) { runCatching { root.recycle() } }
                return@launch
            }

            // 把 DFS 放在 Default 线程跑，避免阻塞主线程导致系统 UI 卡顿
            val snapshot = try {
                TextExtractor.extractSnapshot(root)
            } finally {
                withContext(Dispatchers.Main) { runCatching { root.recycle() } }
            }

            if (snapshot.promptText.length < MIN_TEXT_LENGTH) return@launch

            val fingerprint = snapshot.promptText.hashCode()
            if (fingerprint == lastAnalyzedFingerprint) return@launch

            // 【并发去重】同一时间只允许一个 AI 请求在飞。
            // 防止在 8s 强制触发窗口的临界点，前一个 NonCancellable 中的 AI 还没完成，
            // 又有新协程跑到这里并同时调用 AI，造成账单浪费。
            if (!aiCallInFlight.compareAndSet(false, true)) {
                Log.d(TAG, "上一次 AI 请求仍在飞，跳过本次分析（fingerprint=$fingerprint）")
                return@launch
            }

            try {
                // 【NonCancellable】一旦决定要发 AI 请求，就让它跑完。
                // 哪怕用户继续滑屏导致 debounceJob 被新事件 cancel，
                // 这个 AI 请求和后续打断动作仍会执行到底，绝不浪费 API 配额。
                withContext(NonCancellable) {
                    lastAnalysisAt = System.currentTimeMillis()
                    lastAnalyzedFingerprint = fingerprint

                    Log.d(
                        TAG,
                        "防抖结束，准备发送文本（实际包名=$rootPkg），最终提取的有效文本: " +
                            snapshot.promptText.take(100).replace('\n', ' ') + "..."
                    )

                    val aiReply = runCatching {
                        withTimeout(REQUEST_TIMEOUT_MS) {
                            callAi(snapshot.promptText, snapshot.scene)
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) {
                            // NonCancellable 内理论上不会被外部 cancel；
                            // 这里仅捕获 withTimeout 自身的超时取消。
                            Log.w(TAG, "AI 请求超时取消: ${e.message}")
                        } else {
                            Log.w(TAG, "AI 请求异常: ${e.javaClass.simpleName} - ${e.message}")
                        }
                    }.getOrNull() ?: return@withContext

                    Log.d(TAG, "AI 网络请求成功，原始返回字符串: $aiReply")
                    if (aiReply.isBlank()) {
                        Log.w(TAG, "AI 返回空字符串，可能是模型不可用。当前模型: ${settingsCache.model}")
                        return@withContext
                    }

                    if (isDistracted(aiReply)) {
                        Log.d(TAG, "判定为娱乐，执行强制回桌面操作！(packageName=$rootPkg)")
                        triggerHomeIntercept(
                            packageName = rootPkg,
                            reasonType = "AI_DISTRACTED",
                            reasonDetail = buildReasonDetail(snapshot, rootPkg),
                            screenTextExcerpt = snapshot.promptText.take(300),
                            aiReply = aiReply
                        )
                    }
                }
            } finally {
                aiCallInFlight.set(false)
            }
        }
    }

    // ────────────────────────────── AI 调用 ──────────────────────────────

    private suspend fun callAi(screenText: String, scene: ScreenScene): String? =
        withContext(Dispatchers.IO) {
            val settings = settingsCache
            val api = apiClientFactory.createApi()
            val request = ChatCompletionRequest(
                model = settings.model.ifBlank { DEFAULT_MODEL },
                messages = listOf(
                    ChatMessage(role = "system", content = buildSystemPrompt(settings)),
                    ChatMessage(role = "user", content = buildUserPrompt(scene, screenText))
                )
            )
            api.chatCompletions(request).choices?.firstOrNull()?.message?.content?.trim()
        }

    /**
     * System Prompt 动态生成。
     * 用户在首页填的 focusGoal / forbiddenTags 在这里被拼成完整的角色设定。
     */
    private fun buildSystemPrompt(settings: AppSettings): String {
        val template = if (settings.useCustomPromptTemplate) {
            settings.customPromptTemplate
        } else {
            DEFAULT_PROMPT_TEMPLATE
        }
        return PromptTemplateRenderer.render(
            template = template,
            focusGoal = settings.focusGoal,
            forbiddenTags = settings.forbiddenTags
        )
    }

    private fun buildUserPrompt(scene: ScreenScene, screenText: String): String {
        val tag = when (scene) {
            ScreenScene.WATCHING -> "[当前场景: 视频播放中]"
            ScreenScene.BROWSING -> "[当前场景: 浏览/搜索/推荐列表]"
        }
        return "$tag\n$screenText"
    }

    private fun isDistracted(reply: String): Boolean {
        if (reply.isBlank()) return false
        val firstDigit = reply.firstOrNull { it.isDigit() } ?: return false
        return firstDigit == '1'
    }

    // ────────────────────────────── 强制打断 ──────────────────────────────

    private fun triggerHomeIntercept(
        packageName: String,
        reasonType: String,
        reasonDetail: String,
        screenTextExcerpt: String,
        aiReply: String
    ) {
        mainHandler.post { performGlobalAction(GLOBAL_ACTION_HOME) }
        serviceScope.launch {
            val app = application as FocusAiApplication
            val todayCount = runCatching {
                app.statsRepository.recordInterception(
                    packageName = packageName,
                    appLabel = appLabelOf(packageName),
                    reasonType = reasonType,
                    reasonDetail = reasonDetail,
                    screenTextExcerpt = screenTextExcerpt,
                    aiReply = aiReply
                )
            }
                .getOrDefault(0)
            withContext(Dispatchers.Main) {
                ToastHelper.showInterceptToast(this@FocusAccessibilityService)
                NotificationHelper.showInterceptNotification(
                    this@FocusAccessibilityService, todayCount
                )
            }
        }
    }

    // ────────────────────────────── 三色名单分类（核心路由） ──────────────────────────────

    private fun classify(pkg: String): PackageCategory {
        classifyCache[pkg]?.let { return it }
        val resolved = resolveCategory(pkg)
        classifyCache[pkg] = resolved
        return resolved
    }

    private fun resolveCategory(pkg: String): PackageCategory {
        // 1. 用户自定义名单优先级最高
        when (userListsCache.categoryOf(pkg)) {
            AppCategory.BLACKLIST -> return PackageCategory.BLACKLIST
            AppCategory.WHITELIST -> return PackageCategory.WHITELIST
            AppCategory.GREYLIST -> return PackageCategory.GREYLIST
            AppCategory.UNMANAGED -> Unit
        }

        // 2. 内置默认名单
        if (pkg in BUILT_IN_BLACKLIST) return PackageCategory.BLACKLIST
        if (pkg in BUILT_IN_WHITELIST) return PackageCategory.WHITELIST
        if (pkg in BUILT_IN_GREYLIST) return PackageCategory.GREYLIST

        // 3. 系统组件 / 桌面 / 输入法 一律白
        if (pkg.contains("launcher", ignoreCase = true) ||
            pkg.endsWith(".ime") ||
            pkg.contains(".inputmethod") ||
            pkg.startsWith("com.android.") ||
            pkg.startsWith("com.google.android.gms")
        ) return PackageCategory.WHITELIST

        // 4. ApplicationInfo.category 自动归类（API 26+）
        readApplicationCategory(pkg)?.let { return it }

        // 5. 包名关键词启发式（兜底）
        if (PACKAGE_HEURISTIC_GREY.any { pkg.contains(it, ignoreCase = true) }) {
            return PackageCategory.GREYLIST
        }

        // 6. 未知 App → 默认白名单（隐私安全）
        return PackageCategory.WHITELIST
    }

    /**
     * 读取系统标记的 App 类别（开发者在 manifest 里声明的）。
     * 大多数国内 App 不会填，但能填的我们就利用上。
     */
    private fun readApplicationCategory(pkg: String): PackageCategory? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val info: ApplicationInfo = runCatching {
            packageManager.getApplicationInfo(pkg, 0)
        }.getOrNull() ?: return null

        return when (info.category) {
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_GAME -> PackageCategory.GREYLIST
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_NEWS -> PackageCategory.WHITELIST
            else -> null
        }
    }

    private fun appLabelOf(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun buildReasonDetail(snapshot: com.focusai.app.util.ScreenSnapshot, pkg: String): String {
        val sceneLabel = when (snapshot.scene) {
            ScreenScene.WATCHING -> "视频播放中"
            ScreenScene.BROWSING -> "浏览/搜索/推荐列表"
        }
        val title = snapshot.primaryTitle ?: "未识别标题"
        return "App=$pkg; 场景=$sceneLabel; 标题=$title; 标签=${snapshot.prominentTags.joinToString(",")}"
    }

    private enum class PackageCategory(val label: String) {
        BLACKLIST("黑名单"), WHITELIST("白名单"), GREYLIST("灰名单")
    }

    // ────────────────────────────── 常量 ──────────────────────────────

    companion object {
        private const val TAG = "FocusAI"

        private const val DEBOUNCE_MS = 2_000L
        private const val BLACK_COOLDOWN_MS = 5_000L
        private const val MAX_ANALYSIS_WAIT_MS = 8_000L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val MIN_TEXT_LENGTH = 10
        /** 同包名 + 同事件类型的高频节流时长，毫秒。 */
        private const val SAME_EVENT_THROTTLE_MS = 500L
        private const val DEFAULT_MODEL = "deepseek-chat"

        /**
         * 瞬时覆盖层包名：状态栏、系统弹窗等会在不切换前台 App 的情况下
         * 持续推送 CONTENT_CHANGED。直接在入口忽略，不参与名单路由、
         * 也不取消挂起的灰名单分析。
         */
        private val TRANSIENT_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "android"
        )

        /** 内置黑名单：纯娱乐 App，命中即 HOME。 */
        private val BUILT_IN_BLACKLIST = setOf(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.smile.gifmaker",
            "com.kuaishou.nebula",
            "com.sina.weibo",
            "com.tencent.qqlive",
            "com.qiyi.video",
            "com.youku.phone",
            "com.taobao.taobao",
            "com.jingdong.app.mall",
            "com.tmall.wireless",
            "com.xunmeng.pinduoduo"
        )

        /** 内置白名单：办公 / 通讯 / 系统类，直接忽略。 */
        private val BUILT_IN_WHITELIST = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.android.calendar",
            "com.google.android.calendar",
            "com.android.deskclock",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.mms",
            "com.google.android.gm",
            "com.bbk.launcher2",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.oneplus.launcher",
            "com.oppo.launcher",
            "com.realme.launcher"
        )

        /** 内置灰名单：图文+视频混合，需 AI 判断。 */
        private val BUILT_IN_GREYLIST = setOf(
            "tv.danmaku.bili",
            "tv.danmaku.bilibilihd",
            "com.xingin.xhs",
            "com.zhihu.android",
            "com.ss.android.article.news",
            "com.tencent.news",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.netease.cloudmusic"
        )

        /**
         * 包名关键词启发式 → 灰名单。
         * 不知名视频 App 包名通常含有这些英文词。
         */
        private val PACKAGE_HEURISTIC_GREY = listOf(
            "video", "vedio", "vlog", "tv", "live", "stream",
            "short", "feed", "news", "play", "tube"
        )
    }
}
