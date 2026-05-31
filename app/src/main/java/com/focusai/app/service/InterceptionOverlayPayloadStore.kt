package com.focusai.app.service

import com.focusai.app.data.prefs.InterceptionMode
import java.util.concurrent.atomic.AtomicReference

data class InterceptionOverlayPayload(
    val screenshotJpeg: ByteArray,
    val reason: String,
    val mode: InterceptionMode,
    val customCooldownText: String,
    val customCooldownSeconds: Int
)

object InterceptionOverlayPayloadStore {
    private val payloadRef = AtomicReference<InterceptionOverlayPayload?>(null)

    fun save(payload: InterceptionOverlayPayload) {
        payloadRef.set(payload)
    }

    fun consume(): InterceptionOverlayPayload? = payloadRef.getAndSet(null)
}
