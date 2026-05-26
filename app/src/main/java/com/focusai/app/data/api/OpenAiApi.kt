package com.focusai.app.data.api

import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatCompletionRequest): ChatCompletionResponse
}
