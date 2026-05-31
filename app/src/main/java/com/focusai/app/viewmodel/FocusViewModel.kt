package com.focusai.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.FocusAiApplication
import com.focusai.app.data.prefs.DEFAULT_CUSTOM_COOLDOWN_SECONDS
import com.focusai.app.data.prefs.DEFAULT_CUSTOM_COOLDOWN_TEXT
import com.focusai.app.data.prefs.InterceptionMode
import com.focusai.app.service.VisualSupervisionService
import com.focusai.app.util.AccessibilityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页 ViewModel：负责"监督开关、无障碍权限状态、监督规则草稿"三件事。
 *
 * 注意：真正的"启停截屏服务"动作在 UI 层完成，因为申请 MediaProjection 必须有
 * Activity 上下文 + ActivityResult API。这里只负责持久化开关状态与自愈状态对齐。
 */
data class FocusUiState(
    val supervisionEnabled: Boolean = false,
    val accessibilityGranted: Boolean = false,
    /** 用户在输入框内当前编辑的「专注目标」（未保存）。 */
    val focusGoalDraft: String = "",
    /** 用户在输入框内当前编辑的「禁止标签」（未保存）。 */
    val forbiddenTagsDraft: String = "",
    /** 草稿是否与已存值不同，控制「保存」按钮高亮。 */
    val rulesDirty: Boolean = false,
    val rulesSavedMessage: String? = null,
    val appMonitorBlacklistDraft: Set<String> = emptySet(),
    val appMonitorWhitelistDraft: Set<String> = emptySet(),
    val appMonitorDialogVisible: Boolean = false,
    val appMonitorSearchQuery: String = "",
    val monitorableApps: List<MonitorableApp> = emptyList(),
    val selectedInterceptionMode: InterceptionMode = InterceptionMode.INSTANT_KILL,
    val customCooldownTextDraft: String = DEFAULT_CUSTOM_COOLDOWN_TEXT,
    val customCooldownSecondsDraft: String = DEFAULT_CUSTOM_COOLDOWN_SECONDS.toString(),
    val modeDirty: Boolean = false,
    val modeSavedMessage: String? = null
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusAiApplication
    private val settingsRepository = app.settingsRepository

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    /** 已存的专注目标，用于和草稿对比是否 dirty。 */
    private var savedFocusGoal: String = ""
    private var savedForbiddenTags: String = ""
    private var savedMode: InterceptionMode = InterceptionMode.INSTANT_KILL
    private var savedCooldownText: String = DEFAULT_CUSTOM_COOLDOWN_TEXT
    private var savedCooldownSeconds: Int = DEFAULT_CUSTOM_COOLDOWN_SECONDS
    private var draftsInitialized = false
    private var systemVisiblePackages: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                savedFocusGoal = settings.focusGoal
                savedForbiddenTags = settings.forbiddenTags
                savedMode = settings.interceptionMode
                savedCooldownText = settings.customCooldownText
                savedCooldownSeconds = settings.customCooldownSeconds

                // 关键自愈逻辑：若 prefs 里残留 supervisionEnabled=true 但前台服务
                // 实际未运行（典型场景：用户上次启用后杀了进程 / 重启过手机），
                // 把开关同步回 false，强制用户重新申请 MediaProjection。
                val effectiveEnabled = settings.supervisionEnabled && VisualSupervisionService.running
                if (settings.supervisionEnabled && !VisualSupervisionService.running) {
                    settingsRepository.setSupervisionEnabled(false)
                }

                _uiState.update { current ->
                    val shouldInitDrafts = !draftsInitialized
                    current.copy(
                        supervisionEnabled = effectiveEnabled,
                        focusGoalDraft = if (shouldInitDrafts) settings.focusGoal else current.focusGoalDraft,
                        forbiddenTagsDraft = if (shouldInitDrafts) settings.forbiddenTags else current.forbiddenTagsDraft,
                        rulesDirty = if (shouldInitDrafts) false else current.rulesDirty,
                        appMonitorBlacklistDraft = if (shouldInitDrafts) settings.appMonitorBlacklist
                        else current.appMonitorBlacklistDraft,
                        appMonitorWhitelistDraft = if (shouldInitDrafts) settings.appMonitorWhitelist
                        else current.appMonitorWhitelistDraft,
                        selectedInterceptionMode = settings.interceptionMode,
                        customCooldownTextDraft = if (shouldInitDrafts) settings.customCooldownText
                        else current.customCooldownTextDraft,
                        customCooldownSecondsDraft = if (shouldInitDrafts) settings.customCooldownSeconds.toString()
                        else current.customCooldownSecondsDraft,
                        modeDirty = if (shouldInitDrafts) false else current.modeDirty
                    )
                }
                draftsInitialized = true
            }
        }
        viewModelScope.launch { loadMonitorableApps() }
        refreshAccessibilityStatus()
    }

    fun refreshAccessibilityStatus() {
        val granted = AccessibilityUtils.isAccessibilityServiceEnabled(getApplication())
        val currentlyRunning = VisualSupervisionService.running
        _uiState.update { current ->
            current.copy(
                accessibilityGranted = granted,
                supervisionEnabled = current.supervisionEnabled && currentlyRunning
            )
        }
        if (!currentlyRunning) {
            viewModelScope.launch { settingsRepository.setSupervisionEnabled(false) }
        }
    }

    /**
     * 仅落盘开关状态。真正启停服务由 UI 层完成。
     */
    fun persistSupervisionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSupervisionEnabled(enabled)
        }
    }

    fun updateFocusGoalDraft(value: String) {
        _uiState.update {
            it.copy(
                focusGoalDraft = value,
                rulesDirty = value != savedFocusGoal || it.forbiddenTagsDraft != savedForbiddenTags
            )
        }
    }

    fun updateForbiddenTagsDraft(value: String) {
        _uiState.update {
            it.copy(
                forbiddenTagsDraft = value,
                rulesDirty = value != savedForbiddenTags || it.focusGoalDraft != savedFocusGoal
            )
        }
    }

    fun saveRules(savedLabel: String) {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.saveFocusGoal(state.focusGoalDraft)
            settingsRepository.saveForbiddenTags(state.forbiddenTagsDraft)
            _uiState.update { it.copy(rulesSavedMessage = savedLabel, rulesDirty = false) }
        }
    }

    fun clearRulesSavedMessage() {
        _uiState.update { it.copy(rulesSavedMessage = null) }
    }

    fun openAppMonitorDialog() {
        _uiState.update { it.copy(appMonitorDialogVisible = true) }
        viewModelScope.launch { loadMonitorableApps() }
    }

    fun closeAppMonitorDialog() {
        _uiState.update {
            it.copy(
                appMonitorDialogVisible = false,
                appMonitorSearchQuery = ""
            )
        }
    }

    fun updateAppMonitorSearchQuery(value: String) {
        _uiState.update { it.copy(appMonitorSearchQuery = value) }
    }

    fun setAppMonitorGroup(packageName: String, toBlacklist: Boolean) {
        _uiState.update {
            val normalized = packageName.lowercase()
            val next = if (toBlacklist) {
                it.copy(
                    appMonitorBlacklistDraft = it.appMonitorBlacklistDraft + normalized,
                    appMonitorWhitelistDraft = it.appMonitorWhitelistDraft - normalized
                )
            } else {
                it.copy(
                    appMonitorBlacklistDraft = it.appMonitorBlacklistDraft - normalized,
                    appMonitorWhitelistDraft = it.appMonitorWhitelistDraft + normalized
                )
            }
            next.copy(
                appMonitorWhitelistDraft = ensureSystemWhitelist(next.appMonitorWhitelistDraft, next.appMonitorBlacklistDraft)
            )
        }
        persistCurrentAppMonitorLists()
    }

    fun updateInterceptionMode(mode: InterceptionMode) {
        _uiState.update {
            val next = it.copy(selectedInterceptionMode = mode)
            next.copy(modeDirty = isModeDirty(next))
        }
    }

    fun updateCooldownTextDraft(value: String) {
        _uiState.update {
            val next = it.copy(customCooldownTextDraft = value)
            next.copy(modeDirty = isModeDirty(next))
        }
    }

    fun updateCooldownSecondsDraft(value: String) {
        _uiState.update {
            val sanitized = value.filter { ch -> ch.isDigit() }.take(3)
            val next = it.copy(customCooldownSecondsDraft = sanitized)
            next.copy(modeDirty = isModeDirty(next))
        }
    }

    fun saveInterceptionMode(savedLabel: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val seconds = state.customCooldownSecondsDraft.toIntOrNull()
                ?: DEFAULT_CUSTOM_COOLDOWN_SECONDS
            settingsRepository.saveInterceptionMode(
                mode = state.selectedInterceptionMode,
                customCooldownText = state.customCooldownTextDraft,
                customCooldownSeconds = seconds
            )
            _uiState.update {
                it.copy(modeSavedMessage = savedLabel, modeDirty = false)
            }
        }
    }

    fun clearModeSavedMessage() {
        _uiState.update { it.copy(modeSavedMessage = null) }
    }

    private fun isModeDirty(state: FocusUiState): Boolean {
        val seconds = state.customCooldownSecondsDraft.toIntOrNull() ?: DEFAULT_CUSTOM_COOLDOWN_SECONDS
        return state.selectedInterceptionMode != savedMode ||
            state.customCooldownTextDraft != savedCooldownText ||
            seconds != savedCooldownSeconds
    }

    private suspend fun loadMonitorableApps() {
        val apps = withContext(Dispatchers.IO) {
            val appContext = getApplication<Application>()
            val pm = appContext.packageManager
            val merged = LinkedHashMap<String, MonitorableApp>()

            // 优先：系统 LauncherApps API，与桌面图标列表最一致。
            runCatching {
                val launcherApps = appContext.getSystemService(LauncherApps::class.java)
                launcherApps?.getActivityList(null, Process.myUserHandle()).orEmpty()
                    .forEach { activity ->
                        val appInfo = activity.applicationInfo ?: return@forEach
                        if (appInfo.packageName == appContext.packageName) return@forEach
                        val packageName = appInfo.packageName.lowercase()
                        val label = activity.label?.toString().orEmpty()
                            .ifBlank { runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(packageName) }
                        merged[packageName] = MonitorableApp(
                            packageName = packageName,
                            appName = label.ifBlank { packageName },
                            isSystemApp = appInfo.isSystemAppCompat()
                        )
                    }
            }.onFailure {
                Log.w(TAG, "LauncherApps 枚举失败，回退 queryIntentActivities：${it.message}")
            }

            // 补充：部分 ROM 只在 queryIntentActivities 中暴露入口。
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
            resolveList.forEach { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@forEach
                if (appInfo.packageName == appContext.packageName) return@forEach
                val packageName = appInfo.packageName.lowercase()
                if (merged.containsKey(packageName)) return@forEach
                val label = runCatching { resolveInfo.loadLabel(pm).toString() }
                    .getOrDefault(packageName)
                merged[packageName] = MonitorableApp(
                    packageName = packageName,
                    appName = label.ifBlank { packageName },
                    isSystemApp = appInfo.isSystemAppCompat()
                )
            }

            merged.values.sortedBy { it.appName.lowercase() }
        }
        systemVisiblePackages = apps
            .asSequence()
            .filter { it.isSystemApp }
            .map { it.packageName }
            .toSet()
        _uiState.update { current ->
            val mergedWhitelist = ensureSystemWhitelist(
                whitelist = current.appMonitorWhitelistDraft,
                blacklist = current.appMonitorBlacklistDraft
            )
            current.copy(
                monitorableApps = apps,
                appMonitorWhitelistDraft = mergedWhitelist
            )
        }
        persistCurrentAppMonitorLists()
    }

    private fun persistCurrentAppMonitorLists() {
        viewModelScope.launch {
            val state = _uiState.value
            val blacklist = state.appMonitorBlacklistDraft
            val whitelist = ensureSystemWhitelist(
                whitelist = state.appMonitorWhitelistDraft,
                blacklist = blacklist
            ) - blacklist
            settingsRepository.saveAppMonitorLists(
                blacklist = blacklist,
                whitelist = whitelist
            )
        }
    }

    private fun ensureSystemWhitelist(
        whitelist: Set<String>,
        blacklist: Set<String>
    ): Set<String> {
        return whitelist + (systemVisiblePackages - blacklist)
    }
}

data class MonitorableApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

private fun ApplicationInfo.isSystemAppCompat(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

private const val TAG = "OnlySight-FocusVM"
