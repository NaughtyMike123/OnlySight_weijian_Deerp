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

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val supervisionEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val focusGoal: String = DEFAULT_FOCUS_GOAL,
    val forbiddenTags: String = DEFAULT_FORBIDDEN_TAGS,
    val useCustomPromptTemplate: Boolean = false,
    val customPromptTemplate: String = DEFAULT_PROMPT_TEMPLATE
)

// 视觉版默认指向豆包（火山方舟）OpenAI 兼容端点。
// 用户依然可以在「设置」里改成其它兼容 VLM（例如 OpenAI / Qwen-VL）。
const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
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

const val DEFAULT_PROMPT_TEMPLATE = """
你是一个极其严格的防沉迷监督员。
用户当前的专注目标是：{focusGoal}
用户明确不想看的内容类型（出现即视为娱乐）：{forbiddenTags}

判定规则（请严格遵守）：
1. 屏幕文本第一行若是 [当前场景: 浏览/搜索/推荐列表]，说明用户在找内容；仅当明确禁止标签或明显娱乐信号出现时回复 1，其余回复 0。
2. 屏幕文本第一行若是 [当前场景: 视频播放中]，请优先依据【视频主标题】和【可见标签】判断：
   - 与专注目标相关的技术、学习、工作内容 → 0
   - 娱乐 / 搞笑 / 无意义视频消磨时间 / 命中禁止标签 → 1
3. 只要禁止标签词出现，即使在浏览场景也应从严判断。

输出格式：只回复一个数字字符 '0' 或 '1'，不要输出任何其他内容。
"""
