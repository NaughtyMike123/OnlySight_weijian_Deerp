package com.focusai.app.data.api

import retrofit2.http.Body
import retrofit2.http.POST

interface PremiumVisionApi {
    @POST("vision")
    suspend fun analyze(@Body request: PremiumVisionRequest): ChatCompletionResponse
}
