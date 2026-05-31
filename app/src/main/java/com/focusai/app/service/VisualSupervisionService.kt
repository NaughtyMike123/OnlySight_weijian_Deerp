package com.focusai.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.focusai.app.FocusAiApplication
import com.focusai.app.MainActivity
import com.focusai.app.R
import com.focusai.app.data.prefs.InterceptionMode
import com.focusai.app.data.api.VisionDecision
import com.focusai.app.ui.intercept.MetaInterceptionActivity
import com.focusai.app.util.BitmapCompressUtil
import com.focusai.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视觉监督前台服务（OnlySight 核心）。
 *
 * 职责：
 *  1. 持有 [MediaProjection] 凭据，把整块屏幕镜像到一个 [VirtualDisplay] +
 *     [ImageReader] 组合中。
 *  2. 根据 [ForegroundCapturePolicy] 动态决定抓帧频率（4s/30s/0）。
 *  3. 每轮抓取最新一帧，缩放至最长边 800px、JPEG(50) 压缩、Base64 编码。
 *  4. 把图片塞进多模态 Chat Completion 请求发给豆包视觉模型。
 *  5. 模型命中娱乐后，按用户模式执行「直接阻断 / 冷静倒计时 / AI 劝导」。
 *
 * 注意事项：
 *  - 必须以 startForegroundService 启动，并在 [onStartCommand] 的最早期调用
 *    [startForeground]，否则系统会在 5s 内崩掉应用（ANR 类异常）。
 *  - Android 14+ 要求 startForeground 时显式声明 FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION，
 *    且 manifest 必须配 android:foregroundServiceType="mediaProjection"。
 *  - Android 14+ 还要求在调用 getMediaProjection 之前先 startForeground，否则会抛
 *    SecurityException。
 *  - 每次 acquireLatestImage 拿到的 [Image] 必须 close()，否则 ImageReader 队列耗尽。
 */
class VisualSupervisionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureJob: Job? = null

    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    /** 是否已经走过 startCapture：避免被多次 startService 重复初始化 VirtualDisplay。 */
    private val captureStarted = AtomicBoolean(false)

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection 被系统/用户回收，停止服务。")
            stopSelf()
        }
    }

    // ────────────────────────────── Service 生命周期 ──────────────────────────────

    override fun onCreate() {
        super.onCreate()
        ensureForegroundChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须先 startForeground 再做任何 MediaProjection 操作。
        startInForeground()

        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode != Int.MIN_VALUE && data != null) {
                    startCapture(resultCode, data)
                } else {
                    Log.w(TAG, "ACTION_START 缺少 MediaProjection 凭据，停止服务。")
                    stopSelf()
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: 释放截屏管线与协程。")
        captureJob?.cancel()
        serviceScope.cancel()
        releaseCapturePipeline()
        super.onDestroy()
    }

    // ────────────────────────────── 截屏管线 ──────────────────────────────

    private fun startCapture(resultCode: Int, data: Intent) {
        if (!captureStarted.compareAndSet(false, true)) {
            Log.d(TAG, "startCapture 重复调用，已忽略。")
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { mpm.getMediaProjection(resultCode, data) }
            .onFailure {
                Log.e(TAG, "getMediaProjection 失败：${it.message}")
            }
            .getOrNull()
            ?: run {
                stopSelf()
                return
            }
        mediaProjection = projection

        // Android 14+ 强制在 createVirtualDisplay 之前注册一个回调，
        // 否则会抛 IllegalStateException。回调要落在一个非主线程更安全。
        imageReaderThread = HandlerThread("OnlySightCapture").apply { start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
        projection.registerCallback(mediaProjectionCallback, imageReaderHandler)

        // 抓真实屏幕尺寸（虚拟显示分辨率应与物理屏一致，否则切屏会拉伸）。
        readScreenMetrics()

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            IMAGE_READER_QUEUE_DEPTH
        )

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            imageReaderHandler
        )

        running = true
        Log.d(TAG, "截屏管线就绪：${screenWidth}x${screenHeight}@${screenDensity}dpi")

        startCaptureLoop()
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            // 首帧前等一拍，让 VirtualDisplay 完成首屏渲染，避免拿到全黑画面。
            delay(FIRST_FRAME_WARMUP_MS)
            while (isActive) {
                val policy = ForegroundCapturePolicy.current()
                if (!policy.tier.shouldCapture) {
                    delay(POLICY_RECHECK_DELAY_MS)
                    continue
                }
                runCatching { tickOnce(policy) }
                    .onFailure { Log.w(TAG, "本轮截屏分析异常：${it.javaClass.simpleName}: ${it.message}") }
                delay(policy.tier.intervalMs)
            }
        }
    }

    /**
     * 一次完整的"抓帧 → 压缩 → 调用 VLM → 决策"。
     * 任意一步失败都不会拖死后续循环。
     *
     * VLM 调用、模板渲染、JSON 拼装统统委托给 [com.focusai.app.data.api.VisionRepository]；
     * 本服务只关心"屏幕截图 → 是否需要踢桌面"这层抽象。
     */
    private suspend fun tickOnce(policy: CapturePolicyState) {
        val snapshot = captureFrameSnapshot() ?: return
        val app = application as FocusAiApplication

        val settings = app.settingsRepository.getSettingsSnapshot()
        val decision = runCatching {
            withTimeout(REQUEST_TIMEOUT_MS) {
                app.visionRepository.judgeBase64Image(snapshot.base64Image)
            }
        }.getOrElse { e ->
            Log.w(TAG, "VLM 调用整体异常：${e.javaClass.simpleName} - ${e.message}")
            VisionDecision()
        }

        if (decision.isEntertainment) {
            val displayText = when (settings.interceptionMode) {
                InterceptionMode.AI_PERSUASION -> {
                    val persuasion = runCatching {
                        withTimeout(PERSUASION_TIMEOUT_MS) {
                            app.visionRepository.generatePersuasionMessage(snapshot.base64Image)
                        }
                    }.getOrElse { e ->
                        Log.w(TAG, "劝导文案生成异常：${e.message}")
                        ""
                    }
                    persuasion.ifBlank {
                        decision.reason.ifBlank { "你正在浪费时间刷娱乐内容，现在立刻停下。" }
                    }
                }
                else -> decision.reason.ifBlank { "AI 判定当前内容为娱乐内容。" }
            }
            val intercepted = triggerInterception(
                mode = settings.interceptionMode,
                screenshotBytes = snapshot.jpegBytes,
                reason = displayText,
                customCooldownText = settings.customCooldownText,
                customCooldownSeconds = settings.customCooldownSeconds
            )
            if (intercepted) {
                Log.d(TAG, "判定为娱乐，已执行拦截。pkg=${policy.packageName} mode=${settings.interceptionMode}")
                val todayCount = runCatching {
                    app.statsRepository.recordInterception(
                        packageName = policy.packageName,
                        aiReason = displayText
                    )
                }.getOrDefault(0)
                NotificationHelper.showInterceptNotification(this@VisualSupervisionService, todayCount)
                delay(POST_TRIGGER_COOLDOWN_MS)
            } else {
                Log.w(TAG, "判定为娱乐，但拦截动作未成功执行。pkg=${policy.packageName}")
            }
        }
    }

    private suspend fun triggerInterception(
        mode: InterceptionMode,
        screenshotBytes: ByteArray,
        reason: String,
        customCooldownText: String,
        customCooldownSeconds: Int
    ): Boolean {
        return when (mode) {
            InterceptionMode.INSTANT_KILL -> forceReturnHome()
            InterceptionMode.CUSTOM_TIMEOUT,
            InterceptionMode.AI_PERSUASION -> {
                // 先退回桌面，避免用户仍停留在娱乐 App 内且后台 Activity 被系统拦截。
                forceReturnHome()

                val payload = InterceptionOverlayPayload(
                    screenshotJpeg = screenshotBytes,
                    reason = reason,
                    mode = mode,
                    customCooldownText = customCooldownText,
                    customCooldownSeconds = customCooldownSeconds
                )
                InterceptionOverlayPayloadStore.save(payload)

                if (InterceptionOverlayController.show(this, payload)) {
                    return true
                }

                Log.w(TAG, "悬浮窗展示失败，尝试全屏通知/Activity 兜底。")
                if (NotificationHelper.launchInterceptionFullScreen(this)) {
                    return true
                }

                launchInterceptionActivity()
            }
        }
    }

    private suspend fun launchInterceptionActivity(): Boolean {
        return withContext(Dispatchers.Main.immediate) {
            runCatching {
                startActivity(
                    Intent(this@VisualSupervisionService, MetaInterceptionActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                    }
                )
            }.onFailure {
                Log.w(TAG, "拉起拦截 Activity 失败：${it.message}")
            }.isSuccess
        }
    }

    private suspend fun forceReturnHome(): Boolean {
        val byAccessibility = withContext(Dispatchers.Main.immediate) {
            FocusAccessibilityService.requestHome()
        }
        if (byAccessibility) return true

        // 兜底：如果无障碍服务短暂失联，直接拉起系统桌面。
        return runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            Log.e(TAG, "HOME 兜底失败：${it.message}")
        }.isSuccess
    }

    /**
     * 从 ImageReader 取最新一帧 → 转 Bitmap → 缩放 → JPEG → Base64。
     * 全程同步执行；调用方位于 IO 协程，因此可以放心做位图操作。
     */
    private fun captureFrameSnapshot(): FrameSnapshot? {
        val reader = imageReader ?: return null
        val image: Image = runCatching { reader.acquireLatestImage() }
            .getOrNull() ?: return null

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            // ImageReader 返回的行可能比实际宽度多几个像素，因此先按 row stride 还原成
            // 真实矩形，再裁掉右侧 padding。
            val paddedWidth = screenWidth + rowPadding / pixelStride
            val raw = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)

            val cropped = if (rowPadding == 0) raw else {
                val c = Bitmap.createBitmap(raw, 0, 0, screenWidth, screenHeight)
                raw.recycle()
                c
            }

            // 最长边 800px、JPEG quality 50，控制上传体积。
            val jpegBytes = BitmapCompressUtil.compressToJpegBytes(cropped)
            cropped.recycle()
            return FrameSnapshot(
                base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP),
                jpegBytes = jpegBytes
            )
        } catch (t: Throwable) {
            Log.w(TAG, "捕获/压缩帧异常：${t.message}")
            return null
        } finally {
            runCatching { image.close() }
        }
    }

    private fun readScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    // ────────────────────────────── 前台通知 ──────────────────────────────

    private fun startInForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.visual_supervision_notification_title))
            .setContentText(getString(R.string.visual_supervision_notification_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureForegroundChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(FOREGROUND_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            getString(R.string.visual_supervision_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.visual_supervision_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    // ────────────────────────────── 资源释放 ──────────────────────────────

    private fun releaseCapturePipeline() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        runCatching {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null

        runCatching {
            imageReaderThread?.quitSafely()
        }
        imageReaderThread = null
        imageReaderHandler = null

        captureStarted.set(false)
        running = false
    }

    companion object {
        private const val TAG = "OnlySight-Vision"

        const val ACTION_START = "com.focusai.app.action.START_VISUAL_SUPERVISION"
        const val ACTION_STOP = "com.focusai.app.action.STOP_VISUAL_SUPERVISION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val FOREGROUND_CHANNEL_ID = "onlysight_visual_supervision"
        private const val FOREGROUND_NOTIFICATION_ID = 2001

        private const val VIRTUAL_DISPLAY_NAME = "OnlySightVirtualDisplay"
        private const val IMAGE_READER_QUEUE_DEPTH = 2

        /** 首次抓帧前的暖机时间，避免拿到全黑首屏。 */
        private const val FIRST_FRAME_WARMUP_MS = 1_500L

        /** 白名单/安全窗口下不抓帧，循环仅用于刷新策略。 */
        private const val POLICY_RECHECK_DELAY_MS = 1_500L

        /** 触发 HOME 后等待一会儿再继续，防止刚返桌面又把桌面图截给 VLM 形成抖动。 */
        private const val POST_TRIGGER_COOLDOWN_MS = 5_000L

        /** 单次 VLM 请求超时。 */
        private const val REQUEST_TIMEOUT_MS = 30_000L

        /** AI 劝导文案生成超时。 */
        private const val PERSUASION_TIMEOUT_MS = 30_000L

        /**
         * 当前服务是否在运行。UI 层可用作开关状态显示。
         * 注意：这是粗粒度判断（最终决定权仍在 startService / stopSelf）。
         */
        @Volatile
        var running: Boolean = false
            private set

        /**
         * 由 Activity 在拿到 MediaProjection 授权回调后调用，启动截屏服务。
         *
         * 这里立即把 [running] 置为 true，避免 UI 端 persist 开关后、
         * service onStartCommand 还没跑起来时被自愈逻辑误判为"未运行"。
         */
        fun start(context: Context, resultCode: Int, data: Intent) {
            running = true
            val intent = Intent(context, VisualSupervisionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 关闭服务。UI 关闭监督开关时调用。 */
        fun stop(context: Context) {
            running = false
            // 直接 stopService 即可触发 onDestroy → 释放 ImageReader / MediaProjection。
            runCatching { context.stopService(Intent(context, VisualSupervisionService::class.java)) }
        }
    }
}

private data class FrameSnapshot(
    val base64Image: String,
    val jpegBytes: ByteArray
)
