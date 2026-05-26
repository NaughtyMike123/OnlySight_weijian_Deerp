package com.focusai.app.util

import com.focusai.app.data.prefs.DEFAULT_PROMPT_TEMPLATE

object PromptTemplateRenderer {

    fun render(
        template: String,
        focusGoal: String,
        forbiddenTags: String
    ): String {
        val normalizedTemplate = template.ifBlank { DEFAULT_PROMPT_TEMPLATE }.trim()
        return normalizedTemplate
            .replace("{focusGoal}", focusGoal.ifBlank { "学习与工作" })
            .replace("{forbiddenTags}", forbiddenTags.ifBlank { "无" })
    }
}

