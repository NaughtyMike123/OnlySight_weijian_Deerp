package com.focusai.app.data.api

import com.focusai.app.data.prefs.PREMIUM_PROXY_BASE_URL
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 会员中转服务的 Retrofit 单例。
 * 统一管理视觉识别（`/vision`）与激活兑换（`/activate`）两个接口。
 */
object PremiumProxyClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(PREMIUM_PROXY_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val visionApi: PremiumVisionApi by lazy { retrofit.create(PremiumVisionApi::class.java) }
    val activateApi: PremiumActivateApi by lazy { retrofit.create(PremiumActivateApi::class.java) }

    /** 供连通性检测复用，避免重复创建 OkHttpClient。 */
    val okHttpClient: OkHttpClient get() = httpClient
}
