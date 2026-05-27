package com.focusai.app.data.api

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容的多模态对话请求体。
 *
 * 视觉版本与旧文本版本的关键差异：
 * - [ChatMessage.content] 由 `String` 变为 `Any`，可以是纯文本字符串，
 *   也可以是 `List<ContentPart>` 描述的多模态内容（文本块 + 图片块）。
 * - Gson 在序列化 `Any` 时按运行时类型展开：String 序列化为 JSON 字符串，
 *   List 序列化为 JSON 数组，正好符合 Doubao Vision / OpenAI Vision 协议。
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    @SerializedName("max_tokens") val maxTokens: Int = 16
)

/**
 * 一条对话消息。
 *
 * @param role "system" / "user" / "assistant"
 * @param content 文本路径下传 [String]；视觉路径下传 `List<ContentPart>`。
 */
data class ChatMessage(
    val role: String,
    val content: Any
) {
    companion object {
        /** 文本消息工厂方法，便于 system prompt 等场景使用。 */
        fun text(role: String, text: String): ChatMessage = ChatMessage(role, text)

        /** 多模态消息工厂方法：传入若干 [ContentPart]，会被原样序列化为 JSON 数组。 */
        fun multimodal(role: String, parts: List<ContentPart>): ChatMessage =
            ChatMessage(role, parts)
    }
}

/**
 * 多模态消息内部的一个"内容块"。
 * 协议层面只有两种 type：
 *  - "text"      → [text] 字段生效
 *  - "image_url" → [imageUrl] 字段生效
 */
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
) {
    companion object {
        fun text(text: String): ContentPart = ContentPart(type = "text", text = text)

        /**
         * 构造图片 part。
         * @param dataUrl 已包含 MIME 前缀的 data URL，例如 "data:image/jpeg;base64,xxxxx"
         */
        fun image(dataUrl: String): ContentPart =
            ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl))
    }
}

data class ImageUrl(
    val url: String
)

data class ChatCompletionResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: ResponseMessage?
)

/**
 * 响应里的消息体——`content` 一定是字符串（模型回复永远是纯文本）。
 * 单独建一个类避免和请求侧的 [ChatMessage.content: Any] 冲突，
 * 否则 Gson 解析回来时会拿到 LinkedHashMap，需要额外处理。
 */
data class ResponseMessage(
    val role: String?,
    val content: String?
)
