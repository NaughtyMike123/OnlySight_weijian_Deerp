package com.focusai.app.data.api

import android.util.Log
import com.focusai.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.SocketTimeoutException

class PremiumVisionRepository(
    private val settingsRepository: SettingsRepository
) {

    /** 从本地 DataStore 读取 app_token，发起 POST /vision。 */
    suspend fun analyzeWithStoredToken(imageBase64: String): String {
        val token = settingsRepository.getSettingsSnapshot().premiumAppToken
        return analyzeImage(token, imageBase64)
    }

    suspend fun analyzeImage(appToken: String, imageBase64: String): String = withContext(Dispatchers.IO) {
        if (appToken.isBlank()) throw IllegalArgumentException("请先激活会员")
        if (imageBase64.isBlank()) throw IllegalArgumentException("图片不能为空")

        runCatching {
            PremiumProxyClient.visionApi.analyze(
                PremiumVisionRequest(
                    appToken = appToken,
                    imageBase64 = imageBase64
                )
            ).choices?.firstOrNull()?.message?.content?.trim().orEmpty()
        }.getOrElse { throwable ->
            Log.w(TAG, "会员视觉接口失败：${throwable.message}")
            throw mapThrowable(throwable)
        }
    }

    private fun mapThrowable(throwable: Throwable): Throwable {
        parsePremiumApiError(throwable)?.let { return IllegalStateException(it) }
        return when (throwable) {
            is HttpException -> IllegalStateException("服务器错误：HTTP ${throwable.code()}")
            is SocketTimeoutException -> IllegalStateException("请求超时，请检查网络")
            is IllegalArgumentException -> throwable
            else -> IllegalStateException(throwable.message ?: "网络请求失败")
        }
    }

    companion object {
        private const val TAG = "OnlySight-PremiumVision"
    }
}
