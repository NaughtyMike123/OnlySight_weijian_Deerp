package com.focusai.app.data.api

import android.util.Log
import com.focusai.app.data.prefs.DEFAULT_MODEL
import com.focusai.app.data.prefs.DEFAULT_PROMPT_TEMPLATE
import com.focusai.app.data.prefs.SettingsRepository
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
 *  - 最终只返回一个 [Boolean]：
 *      - `true`  → 模型判定该截图为娱乐内容，应执行回桌面
 *      - `false` → 模型判定为正常 / API Key 未配置 / 网络异常 / 解析失败
 *
 * 设计要点：
 *  - 所有 IO 都在 [Dispatchers.IO] 上跑，业务方可以放心从主线程或 IO 协程调用
 *  - 仅吞掉网络/解析层异常并降级为 `false`，避免误打断用户；真正的异常会写日志
 *  - 提示词渲染逻辑统一在这里，与设置页预览共用同一套占位符 `{focusGoal}` / `{forbiddenTags}`
 */
class VisionRepository(
    private val apiClientFactory: ApiClientFactory,
    private val settingsRepository: SettingsRepository
) {

    /**
     * 把一张 Base64 编码的屏幕截图交给视觉大模型判定是否"娱乐"。
     *
     * @param base64Image 截图的 JPEG/PNG 字节流经 Base64 编码后的**纯字符串**
     *                   （**不含** `data:image/jpeg;base64,` 前缀，由本仓库内部拼接）
     * @param mimeType    图片 MIME，默认 `image/jpeg`；如果传 PNG 请改成 `image/png`
     *
     * @return `true` 表示模型回复以字符 `'1'` 开头（=娱乐），其余情况均返回 `false`
     */
    suspend fun judgeBase64Image(
        base64Image: String,
        mimeType: String = "image/jpeg"
    ): Boolean = withContext(Dispatchers.IO) {
        if (base64Image.isBlank()) {
            Log.w(TAG, "judgeBase64Image: 输入为空字符串，跳过。")
            return@withContext false
        }

        val settings = settingsRepository.getSettingsSnapshot()
        if (settings.apiKey.isBlank()) {
            Log.w(TAG, "judgeBase64Image: 未配置 API Key，跳过 VLM 调用。")
            return@withContext false
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
            maxTokens = 4
        )

        val reply: String? = runCatching {
            val api = apiClientFactory.createApi()
            api.chatCompletions(request).choices?.firstOrNull()?.message?.content?.trim()
        }.getOrElse { throwable ->
            Log.w(TAG, "VLM 调用失败：${throwable.javaClass.simpleName} - ${throwable.message}")
            null
        }

        val distracted = parseVerdict(reply)
        Log.d(TAG, "VLM 回复=$reply → 判定为娱乐=$distracted")
        distracted
    }

    /**
     * 把模型回复字符串解析为 Boolean。
     *
     * 严格按"取第一个数字字符"的规则解析，可以容忍模型偶尔输出 `"1。"`、`"1."`、
     * `" 1"`、`"1\n"` 这种带标点/换行的边角情况。
     */
    private fun parseVerdict(reply: String?): Boolean {
        if (reply.isNullOrBlank()) return false
        val firstDigit = reply.firstOrNull { it.isDigit() } ?: return false
        return firstDigit == '1'
    }

    companion object {
        private const val TAG = "OnlySight-VisionRepo"
    }
}
