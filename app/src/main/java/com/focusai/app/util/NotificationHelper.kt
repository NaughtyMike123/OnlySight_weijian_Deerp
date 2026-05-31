package com.focusai.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.focusai.app.MainActivity
import com.focusai.app.R
import com.focusai.app.ui.intercept.MetaInterceptionActivity

object NotificationHelper {

    private const val CHANNEL_ID = "focusai_intercept"
    private const val FULL_SCREEN_CHANNEL_ID = "onlysight_intercept_overlay"
    private const val NOTIFICATION_ID = 1001
    private const val FULL_SCREEN_NOTIFICATION_ID = 1002

    fun showInterceptNotification(context: Context, todayCount: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannelIfNeeded(manager)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.intercept_notification_title))
            .setContentText(context.getString(R.string.intercept_notification_body, todayCount))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 后台无法直接 startActivity 时的兜底：全屏通知拉起拦截页。
     */
    fun launchInterceptionFullScreen(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createFullScreenChannelIfNeeded(context, manager)

        val intent = Intent(context, MetaInterceptionActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            FULL_SCREEN_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FULL_SCREEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.intercept_notification_title))
            .setContentText(context.getString(R.string.meta_intercept_overlay_fallback))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            manager.notify(FULL_SCREEN_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun createFullScreenChannelIfNeeded(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(FULL_SCREEN_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FULL_SCREEN_CHANNEL_ID,
            context.getString(R.string.intercept_overlay_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.intercept_overlay_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun createChannelIfNeeded(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OnlySight",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI supervision interruption alerts"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
