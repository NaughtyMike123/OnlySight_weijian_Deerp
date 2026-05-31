package com.focusai.app.service

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.focusai.app.ui.intercept.InterceptionOverlayContent
import com.focusai.app.util.OverlayPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统悬浮窗拦截层：从后台前台服务拉起，覆盖在当前 App 之上。
 */
object InterceptionOverlayController {

    private const val TAG = "OnlySight-Overlay"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var windowManager: WindowManager? = null

    @Volatile
    private var overlayView: ComposeView? = null

    @Volatile
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    suspend fun show(context: Context, payload: InterceptionOverlayPayload): Boolean {
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            Log.w(TAG, "无悬浮窗权限，无法展示拦截层。")
            return false
        }
        return withContext(Dispatchers.Main) {
            runCatching {
                dismissInternal()
                val appContext = context.applicationContext
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val owner = OverlayLifecycleOwner()
                lifecycleOwner = owner

                val composeView = ComposeView(appContext).apply {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                }

                val blurredBitmap = payload.screenshotJpeg?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                }
                val reason = payload.reason.ifBlank { "AI 判定当前内容为娱乐流消费，请中断。" }

                composeView.setContent {
                    InterceptionOverlayContent(
                        reason = reason,
                        mode = payload.mode,
                        customCooldownText = payload.customCooldownText,
                        customCooldownSeconds = payload.customCooldownSeconds,
                        imageBitmap = blurredBitmap,
                        onDismiss = { dismiss(appContext) }
                    )
                }

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                wm.addView(composeView, layoutParams)
                windowManager = wm
                overlayView = composeView
                owner.resume()
                Log.d(TAG, "拦截悬浮窗已展示 mode=${payload.mode}")
                true
            }.getOrElse {
                Log.e(TAG, "展示拦截悬浮窗失败：${it.message}")
                dismissInternal()
                false
            }
        }
    }

    fun dismiss(context: Context) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal()
        } else {
            mainHandler.post { dismissInternal() }
        }
    }

    fun isShowing(): Boolean = overlayView != null

    private fun dismissInternal() {
        runCatching {
            lifecycleOwner?.destroy()
            val view = overlayView
            if (view != null) {
                windowManager?.removeView(view)
            }
        }.onFailure {
            Log.w(TAG, "关闭拦截悬浮窗异常：${it.message}")
        }
        overlayView = null
        windowManager = null
        lifecycleOwner = null
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = lifecycleRegistry

        fun resume() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
