package com.focusai.app.util

import android.content.Context
import android.provider.Settings

/**
 * 获取设备唯一标识，用于激活码兑换时的 `device_id` 字段。
 *
 * 使用 [Settings.Secure.ANDROID_ID]：无需额外权限，重装应用后保持不变。
 * 模拟器上可能返回固定值，此时回退为包名后缀，避免空字符串。
 */
object DeviceIdProvider {

    /** 已知模拟器默认 ANDROID_ID，无区分度，需过滤。 */
    private const val EMULATOR_ANDROID_ID = "9774d56d682e549c"

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != EMULATOR_ANDROID_ID }
            ?: "unknown-${context.packageName}"
    }
}
