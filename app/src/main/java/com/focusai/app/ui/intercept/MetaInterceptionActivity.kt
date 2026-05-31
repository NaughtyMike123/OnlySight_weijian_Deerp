package com.focusai.app.ui.intercept

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import com.focusai.app.data.prefs.InterceptionMode
import com.focusai.app.service.InterceptionOverlayPayloadStore

/**
 * 拦截层 Activity 兜底：当悬浮窗权限不可用时，通过全屏通知或系统允许的后台启动进入。
 */
class MetaInterceptionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        setFinishOnTouchOutside(false)

        val payload = InterceptionOverlayPayloadStore.consume()
        val blurredBitmap = payload?.screenshotJpeg?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
        }
        val reason = payload?.reason.orEmpty().ifBlank { "AI 判定当前内容为娱乐流消费，请中断。" }
        val mode = payload?.mode ?: InterceptionMode.AI_PERSUASION
        val customText = payload?.customCooldownText.orEmpty()
        val customSeconds = payload?.customCooldownSeconds ?: 10

        setContent {
            InterceptionOverlayContent(
                reason = reason,
                mode = mode,
                customCooldownText = customText,
                customCooldownSeconds = customSeconds,
                imageBitmap = blurredBitmap,
                onDismiss = { finish() }
            )
        }
    }
}
