package com.focusai.app.data.api

import android.content.Context
import android.util.Log
import com.focusai.app.data.prefs.SettingsRepository
import com.focusai.app.data.prefs.isPremiumActive
import com.focusai.app.data.prefs.PREMIUM_PROXY_BASE_URL
import com.focusai.app.util.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * 会员激活仓库：负责兑换激活码、加密持久化 app_token，以及会员状态判断。
 */
class PremiumActivateRepository(
    private val settingsRepository: SettingsRepository
) {

    /** 监听会员是否有效（token 存在且未过期）。 */
    fun observePremiumActive(): Flow<Boolean> {
        return settingsRepository.settingsFlow.map { it.isPremiumActive() }
    }

    /** 一次性读取当前会员是否有效。 */
    suspend fun isPremiumActive(): Boolean {
        return settingsRepository.getSettingsSnapshot().isPremiumActive()
    }

    /** 检测会员凭证是否有效，以及中转服务器是否可达。 */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        if (!isPremiumActive()) throw IllegalStateException("请先激活会员")
        runCatching {
            val request = Request.Builder().url(PREMIUM_PROXY_BASE_URL).get().build()
            PremiumProxyClient.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 400..599) {
                    "连接成功：服务器可达，会员已激活"
                } else {
                    throw IllegalStateException("连接失败：HTTP ${response.code}")
                }
            }
        }.getOrElse { throwable ->
            if (throwable is IllegalStateException) throw throwable
            throw IllegalStateException("连接失败：${throwable.message ?: "请检查网络"}")
        }
    }

    /**
     * 兑换激活码并在成功后写入 DataStore。
     *
     * @return 兑换结果（含过期时间），供 UI 展示
     */
    suspend fun activate(context: Context, activationCode: String): PremiumActivationResult =
        withContext(Dispatchers.IO) {
            val code = activationCode.trim()
            if (code.isBlank()) throw IllegalArgumentException("激活码不能为空")

            val result = redeemFromServer(code, DeviceIdProvider.getDeviceId(context))
            settingsRepository.savePremiumMode(
                appToken = result.appToken,
                expiresAtMillis = result.expiresAtMillis
            )
            Log.i(TAG, "会员激活成功，过期时间=${result.expiresAtMillis ?: "永久"}")
            result
        }

    private suspend fun redeemFromServer(
        activationCode: String,
        deviceId: String
    ): PremiumActivationResult {
        return runCatching {
            val response = PremiumProxyClient.activateApi.activate(
                PremiumActivateRequest(
                    activationCode = activationCode,
                    deviceId = deviceId
                )
            )
            val token = response.appToken.trim()
            if (token.isBlank()) throw IllegalStateException("服务器未返回有效 app_token")
            PremiumActivationResult(
                appToken = token,
                expiresAtMillis = parseExpiresAt(response.expiresAt)
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "激活码兑换失败：${throwable.message}")
            throw mapThrowable(throwable)
        }
    }

    private fun parseExpiresAt(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .getOrElse { cause ->
                if (cause is DateTimeParseException) {
                    throw IllegalStateException("服务器返回的过期时间格式无效")
                } else {
                    throw cause
                }
            }
    }

    private fun mapThrowable(throwable: Throwable): Throwable {
        parsePremiumApiError(throwable)?.let { return IllegalStateException(it) }
        return when (throwable) {
            is HttpException -> IllegalStateException("服务器错误：HTTP ${throwable.code()}")
            is SocketTimeoutException -> IllegalStateException("请求超时，请检查网络")
            is IllegalArgumentException, is IllegalStateException -> throwable
            else -> IllegalStateException(throwable.message ?: "激活失败")
        }
    }

    companion object {
        private const val TAG = "OnlySight-PremiumActivate"
    }
}
