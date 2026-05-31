package com.focusai.app.data.api

import com.google.gson.annotations.SerializedName

/**
 * 激活码兑换请求。
 *
 * 服务端约定：POST https://api.deerp.site/activate
 * ```json
 * {"activation_code": "XXXX-XXXX", "device_id": "设备唯一标识"}
 * ```
 */
data class PremiumActivateRequest(
    @SerializedName("activation_code") val activationCode: String,
    @SerializedName("device_id") val deviceId: String
)

/**
 * 激活码兑换成功响应。
 *
 * ```json
 * {"app_token": "...", "expires_at": "2026-12-31T00:00:00Z"}
 * ```
 * `expires_at` 可省略或为空，表示永久有效。
 */
data class PremiumActivateResponse(
    @SerializedName("app_token") val appToken: String,
    @SerializedName("expires_at") val expiresAt: String? = null
)

data class PremiumActivationResult(
    val appToken: String,
    val expiresAtMillis: Long? = null
)

/** 服务端失败响应：`{"error": "具体的错误提示"}` */
data class PremiumApiErrorResponse(
    @SerializedName("error") val error: String? = null
)
