package com.focusai.app.data.api

import com.focusai.app.data.prefs.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 负责按 [baseUrl] + [apiKey] 缓存 Retrofit 实例，避免每次 AI 调用都重建 OkHttp/Retrofit。
 *
 * 设计要点：
 *  - 共用一个底层 [OkHttpClient]（线程池/连接池），按需要派生带鉴权头的子客户端
 *  - 用 (baseUrl, apiKey) 作为缓存 key，配置一变就自动失效旧实例
 *  - 给协程使用，全部 `suspend`，方便后续替换 [SettingsRepository] 时无需改下游
 */
class ApiClientFactory(
    private val settingsRepository: SettingsRepository
) {
    private val baseHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    @Volatile
    private var cachedApi: OpenAiApi? = null
    @Volatile
    private var cachedKey: String? = null
    @Volatile
    private var cachedUrl: String? = null

    suspend fun createApi(): OpenAiApi {
        val settings = settingsRepository.getSettingsSnapshot()
        val baseUrl = settings.baseUrl.trimEnd('/') + "/"
        val apiKey = settings.apiKey

        val existing = cachedApi
        if (existing != null && cachedUrl == baseUrl && cachedKey == apiKey) return existing

        return synchronized(this) {
            val current = cachedApi
            if (current != null && cachedUrl == baseUrl && cachedKey == apiKey) {
                current
            } else {
                // TODO(cloud-proxy): 未来替换为仅访问本地后端代理，不再在客户端直带第三方 Key。
                val authClient = baseHttpClient.newBuilder()
                    .addInterceptor { chain ->
                        val requestBuilder = chain.request().newBuilder()
                            .header("Content-Type", "application/json")
                        if (apiKey.isNotBlank()) {
                            requestBuilder.header("Authorization", "Bearer $apiKey")
                        }
                        val request = requestBuilder.build()
                        chain.proceed(request)
                    }
                    .build()
                val api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(authClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(OpenAiApi::class.java)
                cachedApi = api
                cachedKey = apiKey
                cachedUrl = baseUrl
                api
            }
        }
    }
}
