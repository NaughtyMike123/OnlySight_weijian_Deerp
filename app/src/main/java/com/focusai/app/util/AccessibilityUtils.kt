package com.focusai.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.focusai.app.service.FocusAccessibilityService

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

    /**
     * Opens the manufacturer-specific auto-start management screen.
     * Each OEM hides this under a different Activity; if that Activity is missing
     * (e.g. stock Android / emulator) we fall back to App Details.
     */
    fun openAutoStartSettings(context: Context) {
        val pkg = context.packageName
        val mfr = Build.MANUFACTURER.lowercase()

        val candidates = buildList {
            when {
                "xiaomi" in mfr || "redmi" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
                "huawei" in mfr || "honor" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                )
                "oppo" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.privacypermissionsentry.PermissionTopActivity"
                        )
                    )
                )
                "vivo" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                )
                "samsung" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"
                        )
                    )
                )
                "oneplus" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                    )
                )
            }
            // Universal fallback — App Details where the user can tweak permissions manually
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                }
            )
        }

        launchFirst(context, candidates)
    }

    /**
     * Opens battery / background-power management for this app.
     * Prefers opening the system battery-optimization settings page (stable on
     * most devices), then falls back to manufacturer-specific pages and finally
     * App Details.
     */
    fun openBatterySettings(context: Context) {
        val pkg = context.packageName
        val mfr = Build.MANUFACTURER.lowercase()

        val candidates = buildList {
            // Standard Android — open the global battery optimization settings list.
            // This is more reliable than ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            // which may silently fail/finish on some devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            when {
                "xiaomi" in mfr || "redmi" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                        )
                    )
                )
                "huawei" in mfr || "honor" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                        )
                    )
                )
                "oppo" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.FakeActivity"
                        )
                    )
                )
                "vivo" in mfr -> add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.safeguard.SoftPermissionDetailActivity"
                        )
                    )
                )
            }
            // All-device fallback
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                }
            )
        }

        launchFirst(context, candidates)
    }

    private fun launchFirst(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            val launched = runCatching {
                context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }.isSuccess
            if (launched) return
        }
    }
}
