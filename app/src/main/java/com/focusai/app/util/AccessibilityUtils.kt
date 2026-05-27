package com.focusai.app.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.focusai.app.service.FocusAccessibilityService

/**
 * 无障碍权限工具：判断是否已授权，以及打开系统无障碍设置页。
 *
 * 视觉版只需要一个无障碍服务（用来执行 `GLOBAL_ACTION_HOME` 把用户踢回桌面），
 * 因此这里不再处理"自启动 / 后台耗电"等厂商深定制项。
 */
object AccessibilityUtils {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${FocusAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
