package com.focusai.app.service

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

enum class CaptureTier(val intervalMs: Long, val shouldCapture: Boolean) {
    BLACKLIST(intervalMs = 4_000L, shouldCapture = true),
    WHITELIST(intervalMs = 0L, shouldCapture = false)
}

data class CapturePolicyState(
    val packageName: String = "",
    val tier: CaptureTier = CaptureTier.WHITELIST,
    val secureWindow: Boolean = false
)

/**
 * 前台 App 捕获策略状态机（极简单例，无需额外依赖）。
 */
object ForegroundCapturePolicy {
    private const val TAG = "OnlySight-CapturePolicy"

    private val stateRef = AtomicReference(CapturePolicyState())
    private val blacklistRef = AtomicReference<Set<String>>(emptySet())
    private val whitelistRef = AtomicReference<Set<String>>(emptySet())

    fun updateMonitorLists(blacklist: Set<String>, whitelist: Set<String>) {
        blacklistRef.set(blacklist)
        whitelistRef.set(whitelist)
        val current = stateRef.get()
        if (current.packageName.isNotBlank()) {
            update(current.packageName, current.secureWindow)
        }
    }

    fun update(packageName: String, secureWindow: Boolean) {
        if (packageName.isBlank()) return
        val safePackage = packageName.lowercase()
        val dynamicBlacklist = blacklistRef.get()
        val dynamicWhitelist = whitelistRef.get()
        val tier = when {
            secureWindow -> CaptureTier.WHITELIST
            safePackage in dynamicWhitelist -> CaptureTier.WHITELIST
            safePackage in dynamicBlacklist -> CaptureTier.BLACKLIST
            // 默认所有应用都视为白名单，不抓帧。
            else -> CaptureTier.WHITELIST
        }
        val newState = CapturePolicyState(
            packageName = safePackage,
            tier = tier,
            secureWindow = secureWindow
        )
        val oldState = stateRef.getAndSet(newState)
        if (oldState != newState) {
            Log.d(TAG, "策略切换：pkg=$safePackage tier=${tier.name} secure=$secureWindow")
        }
    }

    fun current(): CapturePolicyState = stateRef.get()
}
