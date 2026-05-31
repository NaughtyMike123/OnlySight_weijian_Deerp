package com.focusai.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.focusai.app.FocusAiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 视觉监督版无障碍服务。
 *
 * 当前职责：
 * 1) 监听 event.packageName，驱动 [ForegroundCapturePolicy] 状态机（4s/30s/0）。
 * 2) 在拦截倒计时结束后执行 HOME 动作（performGlobalAction）。
 */
class FocusAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsSyncJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接，等待来自视觉监督服务的 HOME 指令。")
        val app = application as FocusAiApplication
        settingsSyncJob?.cancel()
        settingsSyncJob = serviceScope.launch {
            app.settingsRepository.settingsFlow.collectLatest { settings ->
                ForegroundCapturePolicy.updateMonitorLists(
                    blacklist = settings.appMonitorBlacklist,
                    whitelist = settings.appMonitorWhitelist
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        val packageName = evt.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        val secureWindow = isSecureWindow(evt.windowId)
        ForegroundCapturePolicy.update(packageName, secureWindow)
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: 系统中断信号。")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: 无障碍服务销毁。")
        if (instance === this) instance = null
        settingsSyncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 在主线程执行 GLOBAL_ACTION_HOME。
     * 此方法供 [VisualSupervisionService] 跨服务调用。
     */
    private fun performHomeAction(): Boolean {
        val result = runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
            .onFailure { Log.w(TAG, "执行 GLOBAL_ACTION_HOME 失败：${it.message}") }
            .getOrDefault(false)
        if (!result) {
            Log.w(TAG, "performGlobalAction 返回 false，可能被系统拦截。")
        }
        return result
    }

    companion object {
        private const val TAG = "OnlySight-A11y"

        /**
         * 当前已绑定的服务实例。
         * Android 框架同时只会存在一个无障碍服务实例，因此使用单例引用是安全的。
         * `@Volatile` 保证多线程可见性（截屏循环跑在 IO 线程）。
         */
        @Volatile
        private var instance: FocusAccessibilityService? = null

        /**
         * 是否已被用户在系统设置中授权并连接。
         * 用于 UI 层判定按钮的可用状态。
         */
        val isConnected: Boolean
            get() = instance != null

        /**
         * 视觉监督模块的外部入口：请求把用户踢回桌面。
         *
         * 调用约定：调用方必须已经在主线程（例如包在
         * `withContext(Dispatchers.Main) { ... }` 里），因为 [performGlobalAction]
         * 在部分 ROM 上要求主线程。若服务尚未被系统绑定（用户没授权），
         * 调用会安全地什么都不做。
         */
        fun requestHome(): Boolean {
            return instance?.performHomeAction() == true
        }
    }

    /**
     * FLAG_SECURE 兼容检测：
     * - 新系统可直接反射 `AccessibilityWindowInfo#isSecure`。
     * - 老系统没有该 API 时降级为 false。
     */
    private fun isSecureWindow(windowId: Int): Boolean {
        val matched = windows.firstOrNull { it.id == windowId && it.isActiveOrFocusedCompat() }
            ?: windows.firstOrNull { it.isActiveOrFocusedCompat() }
        return matched?.isSecureCompat() == true
    }

    private fun AccessibilityWindowInfo.isActiveOrFocusedCompat(): Boolean {
        return isActive || isFocused || isAccessibilityFocused
    }

    private fun AccessibilityWindowInfo.isSecureCompat(): Boolean {
        return runCatching {
            val method = javaClass.getMethod("isSecure")
            method.invoke(this) as? Boolean ?: false
        }.getOrDefault(false)
    }
}
