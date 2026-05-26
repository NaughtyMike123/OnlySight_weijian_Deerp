package com.focusai.app.data.api

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    @SerializedName("max_tokens") val maxTokens: Int = 16
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: ChatMessage?
)
