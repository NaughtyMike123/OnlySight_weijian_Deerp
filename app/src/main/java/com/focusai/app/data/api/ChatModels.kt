package com.focusai.app.data.api

import com.google.gson.annotations.SerializedName

/**
 * 火山方舟（豆包）多模态对话请求体。
 *
 * 协议层面与 OpenAI Vision 完全一致，因此这套数据类也可以无改动地用于
 * OpenAI GPT-4o、阿里 Qwen-VL 等其它兼容厂商。
 *
 * @param model       模型名或推理接入点 ID（例如 `doubao-1-5-vision-pro-32k-250115` 或 `ep-xxxxxxxx`）
 * @param messages    一条或多条消息，视觉版通常只有一条 user 消息，里面携带文本 + 图片
 * @param temperature 采样温度，监督场景下固定 0.0 让模型输出更稳定
 * @param maxTokens   最大输出 token 数。判定只需要回 '0' / '1'，给到 4 已经非常宽裕
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
 * 关键设计：`content` 字段类型为 [Any]，可以是：
 *  - [String]              → 纯文本消息（OpenAI 兼容的"经典写法"）
 *  - [List]<[ContentPart]> → 多模态消息（视觉/音频时使用）
 *
 * Gson 在序列化 `Any` 时按运行时类型展开：String → JSON 字符串、List → JSON 数组，
 * 正好对齐 Doubao Vision / OpenAI Vision 协议。
 *
 * @param role    "system" / "user" / "assistant"
 * @param content 纯文本字符串 或 `List<ContentPart>`
 */
data class ChatMessage(
    val role: String,
    val content: Any
) {
    companion object {
        /** 文本消息工厂方法，便于 system / user 纯文本场景使用。 */
        fun text(role: String, text: String): ChatMessage = ChatMessage(role, text)

        /** 多模态消息工厂方法：传入若干 [ContentPart]，会被原样序列化为 JSON 数组。 */
        fun multimodal(role: String, parts: List<ContentPart>): ChatMessage =
            ChatMessage(role, parts)
    }
}

/**
 * 多模态消息内部的一个"内容块"。
 *
 * 协议层面只有两种 `type`：
 *  - `"text"`      → 仅 [text] 字段生效
 *  - `"image_url"` → 仅 [imageUrl] 字段生效
 *
 * 用 Kotlin 静态工厂方法 [text] / [image] 构造可避免手写 type 拼错。
 */
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
) {
    companion object {
        /** 构造文本 part。 */
        fun text(text: String): ContentPart = ContentPart(type = "text", text = text)

        /**
         * 由原始 Base64 字符串构造图片 part。
         * 内部会拼成 `data:image/jpeg;base64,xxx` 形式的 data URL。
         *
         * @param base64        裸 Base64 字符串（不含前缀），由调用方传入
         * @param mimeType      图片 MIME，默认 `image/jpeg`
         */
        fun image(base64: String, mimeType: String = "image/jpeg"): ContentPart {
            val dataUrl = "data:$mimeType;base64,$base64"
            return ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl))
        }

        /**
         * 直接由 data URL（例如 `data:image/jpeg;base64,xxx`）或 https URL 构造图片 part。
         * 与 [image] 区别仅在于：调用方已经自己拼好了 URL，不需要再加 MIME 前缀。
         */
        fun imageUrl(url: String): ContentPart =
            ContentPart(type = "image_url", imageUrl = ImageUrl(url = url))
    }
}

/**
 * `image_url` 子对象。火山方舟 / OpenAI 只识别一个 `url` 字段。
 */
data class ImageUrl(
    val url: String
)

// ────────────────────────────── 响应体 ──────────────────────────────

data class ChatCompletionResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: ResponseMessage?
)

/**
 * 响应里的消息体——`content` 一定是字符串（模型回复永远是纯文本）。
 *
 * 单独建一个类避免和请求侧的 [ChatMessage.content: Any] 冲突，
 * 否则 Gson 解析回来时会把多模态 Any 字段拿到 LinkedHashMap，需要额外处理。
 */
data class ResponseMessage(
    val role: String?,
    val content: String?
)
