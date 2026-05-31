package com.focusai.app.data.api

import android.util.Log
import com.focusai.app.data.prefs.ApiAccessMode
import com.focusai.app.data.prefs.DEFAULT_MODEL
import com.focusai.app.data.prefs.DEFAULT_PERSUASION_PROMPT_TEMPLATE
import com.focusai.app.data.prefs.DEFAULT_PROMPT_TEMPLATE
import com.focusai.app.data.prefs.SettingsRepository
import com.focusai.app.data.prefs.isPremiumActive
import com.google.gson.JsonParser
import com.focusai.app.util.PromptTemplateRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视觉判定仓库。
 *
 * 这是网络层对外暴露的"业务级"入口：
 *  - 调用方只需把一张屏幕截图的 Base64 串递进来；
 *  - 仓库内部去取用户配置（API Key、Base URL、模型名/接入点 ID、提示词、监督规则），
 *    拼装多模态请求 → 调用火山方舟 Chat Completions → 解析回复；
 *  - 最终返回 [VisionDecision]：
 *      - [VisionDecision.isEntertainment] 代表是否娱乐
 *      - [VisionDecision.reason] 记录模型解释（供 UI 展示与日志）
 *
 * 设计要点：
 *  - 所有 IO 都在 [Dispatchers.IO] 上跑，业务方可以放心从主线程或 IO 协程调用
 *  - 仅吞掉网络/解析层异常并降级为 `false`，避免误打断用户；真正的异常会写日志
 *  - 提示词渲染逻辑统一在这里，与设置页预览共用同一套占位符 `{focusGoal}` / `{forbiddenTags}`
 */
class VisionRepository(
    private val apiClientFactory: ApiClientFactory,
    private val settingsRepository: SettingsRepository,
    private val premiumVisionRepository: PremiumVisionRepository
) {

    /**
     * 把一张 Base64 编码的屏幕截图交给视觉大模型判定是否"娱乐"。
     *
     * @param base64Image 截图的 JPEG/PNG 字节流经 Base64 编码后的**纯字符串**
     *                   （**不含** `data:image/jpeg;base64,` 前缀，由本仓库内部拼接）
     * @param mimeType    图片 MIME，默认 `image/jpeg`；如果传 PNG 请改成 `image/png`
     *
     * @return [VisionDecision]，支持两种回复：
     *  1) JSON：`{"is_entertainment":true/false,"reason":"..."}`
     *  2) 兼容旧格式：`0` / `1`
     */
    suspend fun judgeBase64Image(
        base64Image: String,
        mimeType: String = "image/jpeg"
    ): VisionDecision = withContext(Dispatchers.IO) {
        if (base64Image.isBlank()) {
            Log.w(TAG, "judgeBase64Image: 输入为空字符串，跳过。")
            return@withContext VisionDecision()
        }

        val settings = settingsRepository.getSettingsSnapshot()
        if (settings.apiAccessMode == ApiAccessMode.PREMIUM) {
            if (!settings.isPremiumActive()) {
                Log.w(TAG, "judgeBase64Image: 会员未激活或已过期，跳过。")
                return@withContext VisionDecision()
            }
            val reply = runCatching {
                premiumVisionRepository.analyzeWithStoredToken(base64Image)
            }.getOrElse { throwable ->
                Log.w(TAG, "会员 VLM 调用失败：${throwable.message}")
                null
            }
            val decision = parseDecision(reply)
            Log.d(TAG, "会员 VLM 回复=$reply → 判定=${decision.isEntertainment} reason=${decision.reason}")
            return@withContext decision
        }
        if (settings.apiAccessMode == ApiAccessMode.CUSTOM && settings.apiKey.isBlank()) {
            Log.w(TAG, "judgeBase64Image: 未配置 API Key，跳过 VLM 调用。")
            return@withContext VisionDecision()
        }

        // 渲染最终发给模型的提示词：尊重"自定义模板"开关，把占位符替换为用户当前规则。
        val rawTemplate = if (settings.useCustomPromptTemplate) {
            settings.customPromptTemplate
        } else {
            DEFAULT_PROMPT_TEMPLATE
        }
        val prompt = PromptTemplateRenderer.render(
            template = rawTemplate,
            focusGoal = settings.focusGoal,
            forbiddenTags = settings.forbiddenTags
        )

        // 构造多模态请求：一条 user 消息，先文本（指令）后图片（截图）。
        // 模型名既可以是公共模型 `doubao-...`，也可以是控制台接入点 ID `ep-xxx`。
        val request = ChatCompletionRequest(
            model = settings.model.ifBlank { DEFAULT_MODEL },
            messages = listOf(
                ChatMessage.multimodal(
                    role = "user",
                    parts = listOf(
                        ContentPart.text(prompt),
                        ContentPart.image(base64 = base64Image, mimeType = mimeType)
                    )
                )
            ),
            temperature = 0.0,
            maxTokens = 96
        )

        val reply: String? = runCatching {
            // TODO(cloud-proxy): 后续接入自建代理后，改为向本地后端发送截图，不再直连第三方模型。
            val api = apiClientFactory.createApi()
            api.chatCompletions(request).choices?.firstOrNull()?.message?.content?.trim()
        }.getOrElse { throwable ->
            Log.w(TAG, "VLM 调用失败：${throwable.javaClass.simpleName} - ${throwable.message}")
            null
        }

        val decision = parseDecision(reply)
        Log.d(TAG, "VLM 回复=$reply → 判定=${decision.isEntertainment} reason=${decision.reason}")
        decision
    }

    /**
     * AI 劝导模式：基于截图单独生成劝导语（第二次 VLM 调用）。
     */
    suspend fun generatePersuasionMessage(
        base64Image: String,
        mimeType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {
        if (base64Image.isBlank()) return@withContext ""

        val settings = settingsRepository.getSettingsSnapshot()
        if (settings.apiAccessMode == ApiAccessMode.PREMIUM) {
            if (!settings.isPremiumActive()) {
                Log.w(TAG, "generatePersuasionMessage: 会员未激活或已过期，跳过。")
                return@withContext ""
            }
            return@withContext runCatching {
                premiumVisionRepository.analyzeWithStoredToken(base64Image)
            }.getOrElse { throwable ->
                Log.w(TAG, "会员劝导文案失败：${throwable.message}")
                ""
            }.replace("```", "").trim().take(120)
        }
        if (settings.apiAccessMode == ApiAccessMode.CUSTOM && settings.apiKey.isBlank()) {
            Log.w(TAG, "generatePersuasionMessage: 未配置 API Key，跳过。")
            return@withContext ""
        }

        val prompt = PromptTemplateRenderer.render(
            template = DEFAULT_PERSUASION_PROMPT_TEMPLATE,
            focusGoal = settings.focusGoal,
            forbiddenTags = settings.forbiddenTags
        )

        val request = ChatCompletionRequest(
            model = settings.model.ifBlank { DEFAULT_MODEL },
            messages = listOf(
                ChatMessage.multimodal(
                    role = "user",
                    parts = listOf(
                        ContentPart.text(prompt),
                        ContentPart.image(base64 = base64Image, mimeType = mimeType)
                    )
                )
            ),
            temperature = 0.7,
            maxTokens = 160
        )

        val reply = runCatching {
            val api = apiClientFactory.createApi()
            api.chatCompletions(request).choices?.firstOrNull()?.message?.content?.trim()
        }.getOrElse { throwable ->
            Log.w(TAG, "劝导文案生成失败：${throwable.javaClass.simpleName} - ${throwable.message}")
            null
        }

        reply.orEmpty()
            .replace("```", "")
            .trim()
            .take(120)
    }

    /**
     * 把模型回复字符串解析为 [VisionDecision]。
     *
     * 优先解析 JSON；失败时回退到"取第一个数字字符"策略，兼容 `"1。"`、`"1."`、
     * `" 1"`、`"1\n"` 这类边角输出。
     */
    private fun parseDecision(reply: String?): VisionDecision {
        if (reply.isNullOrBlank()) return VisionDecision()
        val cleaned = reply
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val jsonDecision = runCatching {
            val obj = JsonParser().parse(cleaned).asJsonObject
            VisionDecision(
                isEntertainment = obj.get("is_entertainment")?.asBoolean == true,
                reason = obj.get("reason")?.asString.orEmpty()
            )
        }.getOrNull()
        if (jsonDecision != null) return jsonDecision

        val firstDigit = reply.firstOrNull { it.isDigit() } ?: return VisionDecision()
        return VisionDecision(
            isEntertainment = firstDigit == '1',
            reason = cleaned.take(120)
        )
    }

    companion object {
        private const val TAG = "OnlySight-VisionRepo"
    }
}

data class VisionDecision(
    val isEntertainment: Boolean = false,
    val reason: String = ""
)
