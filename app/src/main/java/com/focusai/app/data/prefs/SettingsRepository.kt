package com.focusai.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusai_settings")

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = prefs[KEY_API_KEY] ?: "",
            model = prefs[KEY_MODEL] ?: DEFAULT_MODEL,
            supervisionEnabled = prefs[KEY_SUPERVISION] ?: false,
            language = AppLanguage.fromStorage(prefs[KEY_LANGUAGE] ?: AppLanguage.SYSTEM.storageValue),
            focusGoal = prefs[KEY_FOCUS_GOAL] ?: DEFAULT_FOCUS_GOAL,
            forbiddenTags = prefs[KEY_FORBIDDEN_TAGS] ?: DEFAULT_FORBIDDEN_TAGS,
            useCustomPromptTemplate = prefs[KEY_USE_CUSTOM_PROMPT] ?: false,
            customPromptTemplate = prefs[KEY_CUSTOM_PROMPT] ?: DEFAULT_PROMPT_TEMPLATE
        )
    }

    suspend fun getSettingsSnapshot(): AppSettings = settingsFlow.first()

    suspend fun saveApiSettings(baseUrl: String, apiKey: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl.ifBlank { DEFAULT_BASE_URL }
            prefs[KEY_API_KEY] = apiKey
            prefs[KEY_MODEL] = model.ifBlank { DEFAULT_MODEL }
        }
    }

    suspend fun setSupervisionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUPERVISION] = enabled
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = language.storageValue
        }
    }

    suspend fun saveFocusGoal(goal: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_GOAL] = goal.ifBlank { DEFAULT_FOCUS_GOAL }
        }
    }

    suspend fun saveForbiddenTags(tags: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FORBIDDEN_TAGS] = tags
        }
    }

    suspend fun savePromptTemplate(useCustom: Boolean, template: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_CUSTOM_PROMPT] = useCustom
            prefs[KEY_CUSTOM_PROMPT] = template.ifBlank { DEFAULT_PROMPT_TEMPLATE }
        }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_SUPERVISION = booleanPreferencesKey("supervision_enabled")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_FOCUS_GOAL = stringPreferencesKey("focus_goal")
        private val KEY_FORBIDDEN_TAGS = stringPreferencesKey("forbidden_tags")
        private val KEY_USE_CUSTOM_PROMPT = booleanPreferencesKey("use_custom_prompt_template")
        private val KEY_CUSTOM_PROMPT = stringPreferencesKey("custom_prompt_template")
    }
}
