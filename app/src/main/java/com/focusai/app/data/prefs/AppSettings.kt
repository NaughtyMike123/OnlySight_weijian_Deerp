package com.focusai.app.data.prefs

enum class AppLanguage(val storageValue: String) {
    SYSTEM("system"),
    CHINESE("zh"),
    ENGLISH("en");

    companion object {
        fun fromStorage(value: String): AppLanguage {
            return entries.find { it.storageValue == value } ?: SYSTEM
        }
    }
}

enum class InterceptionMode(val storageValue: String) {
    INSTANT_KILL("instant_kill"),
    CUSTOM_TIMEOUT("custom_timeout"),
    AI_PERSUASION("ai_persuasion");

    companion object {
        fun fromStorage(value: String): InterceptionMode {
            return entries.find { it.storageValue == value } ?: INSTANT_KILL
        }
    }
}

enum class ApiAccessMode(val storageValue: String) {
    PREMIUM("premium"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(value: String): ApiAccessMode {
            return entries.find { it.storageValue == value } ?: CUSTOM
        }
    }
}

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val apiAccessMode: ApiAccessMode = ApiAccessMode.CUSTOM,
    val premiumAppToken: String = "",
    val premiumExpiresAtMillis: Long? = null,
    val supervisionEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val focusGoal: String = DEFAULT_FOCUS_GOAL,
    val forbiddenTags: String = DEFAULT_FORBIDDEN_TAGS,
    val useCustomPromptTemplate: Boolean = false,
    val customPromptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
    val appMonitorBlacklist: Set<String> = emptySet(),
    val appMonitorWhitelist: Set<String> = emptySet(),
    val interceptionMode: InterceptionMode = InterceptionMode.INSTANT_KILL,
    val customCooldownText: String = DEFAULT_CUSTOM_COOLDOWN_TEXT,
    val customCooldownSeconds: Int = DEFAULT_CUSTOM_COOLDOWN_SECONDS
)

// 视觉版默认指向火山方舟（豆包）OpenAI 兼容端点。
// 注意末尾的 `/`，Retrofit 拼接相对路径 `chat/completions` 时必须保留。
const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/"
const val PREMIUM_PROXY_BASE_URL = "https://api.deerp.site/"

/**
 * 默认模型名。
 *
 * 火山方舟支持两种填法（任选其一，二者等价）：
 *  1. 公共模型名，例如 `doubao-1-5-vision-pro-32k-250115`
 *  2. 用户在控制台创建的"推理接入点 ID"，形如 `ep-20250115xxxxxx-xxxxx`
 *
 * 用户可在「设置 → Model Name」里自由切换。
 */
const val DEFAULT_MODEL = "doubao-1-5-vision-pro-32k-250115"
const val GITHUB_ISSUES_URL = "https://github.com/NaughtyMike123/OnlySight_weijian_Deerp/issues"

/**
 * 默认专注目标 —— 用户在首页可以随时改成自己的目标。
 */
const val DEFAULT_FOCUS_GOAL = "学习与工作（编程、阅读、文档、笔记、知识整理）"

/**
 * 默认禁止标签 —— 用户在首页可以随时增删。
 * 这些是经验上最常见的"娱乐消磨时间"内容类型。
 */
const val DEFAULT_FORBIDDEN_TAGS = "鬼畜, 美女, 搞笑段子, 游戏直播, 明星八卦, 颜值, 整活, 沙雕, 吐槽"

/**
 * 视觉版默认提示词。
 *
 * 关键设计：
 * - 直接面向"截屏图像"判定，不依赖任何文本前置标签（旧版的 `[当前场景: ...]` 已废除）。
 * - 优先输出 JSON：`{"is_entertainment": true/false, "reason": "..."}`
 * - 支持两个占位符 `{focusGoal}` 与 `{forbiddenTags}`，由 [com.focusai.app.util.PromptTemplateRenderer] 在
 *   发送给模型前替换为用户配置的真实文本。
 *
 * 用户可在"设置 → 高级提示词设置"里关闭/编辑模板。
 */
const val DEFAULT_PROMPT_TEMPLATE = """你是一个极其严格的防沉迷监督员，正在分析用户手机屏幕的实时截图。

用户当前的专注目标：{focusGoal}
用户明确不想看到的内容类型（出现即视为娱乐）：{forbiddenTags}

判定规则（请严格遵守）：
1. 截图显示短视频信息流、娱乐直播、游戏画面、鬼畜/搞笑/八卦/颜值类内容 → 回复 1
2. 截图明显与专注目标相关（学习、编程、阅读、文档、IDE、笔记、邮件、工具、桌面、设置等）→ 回复 0
3. 截图中只要出现禁止标签词，即使是浏览/搜索场景也从严判断为 1
4. 中性场景（桌面、系统设置、消息聊天列表）→ 回复 0

输出格式（严格 JSON）：
{"is_entertainment": true/false, "reason": "一句话说明触发依据"}

约束：
- 只输出 JSON，不要加 markdown 代码块
- `reason` 必须是中文，长度不超过 40 个字
- 无法判断时从严返回 `is_entertainment=false` 并写出原因"""

const val DEFAULT_CUSTOM_COOLDOWN_TEXT = "暂停一下，先呼吸 10 秒，再决定是否继续。"
const val DEFAULT_CUSTOM_COOLDOWN_SECONDS = 10

fun AppSettings.isPremiumActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (apiAccessMode != ApiAccessMode.PREMIUM || premiumAppToken.isBlank()) return false
    val expiresAt = premiumExpiresAtMillis ?: return true
    return nowMillis < expiresAt
}

/**
 * AI 劝导模式专用提示词：基于截图生成一段严厉但克制的劝导语。
 */
const val DEFAULT_PERSUASION_PROMPT_TEMPLATE = """你是用户的自律教练。请根据这张手机屏幕截图，写一段严厉但克制的劝导语，阻止用户继续刷娱乐内容。

用户专注目标：{focusGoal}
用户不想看的内容：{forbiddenTags}

要求：
1. 直接指出截图里正在做什么（例如刷短视频、看直播、玩游戏）
2. 语气严厉、有压迫感，但不要辱骂
3. 只输出劝导语正文，不要 JSON，不要 markdown
4. 中文，60 字以内，结尾用一句短命令（例如「现在放下手机」）"""
