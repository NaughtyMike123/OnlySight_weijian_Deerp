package com.focusai.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 视觉监督版无障碍服务。
 *
 * 设计变更说明：
 * - 旧版本会监听 TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOW_CONTENT_CHANGED，DFS 抓取
 *   节点文本，再走文本 LLM 判断娱乐性。重构后这一整套被彻底废弃。
 * - 现在本服务唯一的职责是：被 [VisualSupervisionService] 在视觉模型判定为娱乐时
 *   调用 [requestHome]，执行系统级"回桌面"动作（performGlobalAction(GLOBAL_ACTION_HOME)）。
 * - 之所以仍然保留无障碍服务，是因为只有无障碍服务才能在任意前台 App 中拿到
 *   系统返桌面的权限——前台 Service 自己是做不到的。
 */
class FocusAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接，等待来自视觉监督服务的 HOME 指令。")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 视觉路径下完全不消费事件，无需任何文本扫描。
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: 系统中断信号。")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: 无障碍服务销毁。")
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * 在主线程执行 GLOBAL_ACTION_HOME。
     * 此方法供 [VisualSupervisionService] 跨服务调用。
     */
    private fun performHomeAction() {
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
            .onFailure { Log.w(TAG, "执行 GLOBAL_ACTION_HOME 失败：${it.message}") }
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
        fun requestHome() {
            instance?.performHomeAction()
        }
    }
}
