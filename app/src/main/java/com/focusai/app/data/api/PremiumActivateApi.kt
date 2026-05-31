package com.focusai.app.data.api

import retrofit2.http.Body
import retrofit2.http.POST

interface PremiumActivateApi {
    @POST("activate")
    suspend fun activate(@Body request: PremiumActivateRequest): PremiumActivateResponse
}
