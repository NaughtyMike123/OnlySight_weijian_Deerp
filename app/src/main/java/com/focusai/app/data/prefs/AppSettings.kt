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

// 视觉版默认指向火山方舟（豆包）OpenAI 兼容端点。
// 注意末尾的 `/`，Retrofit 拼接相对路径 `chat/completions` 时必须保留。
const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/"

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
 * - 强制只输出一个数字字符 `0` / `1`，方便后台稳定解析。
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

输出格式：只回复一个数字字符 '0' 或 '1'，不要输出任何其他内容、标点或解释。"""
