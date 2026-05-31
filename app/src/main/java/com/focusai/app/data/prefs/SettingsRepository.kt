package com.focusai.app.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusai.app.data.security.ApiKeyCryptoManager
import com.focusai.app.data.security.EncryptedPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusai_settings")

class SettingsRepository(private val context: Context) {
    private val apiKeyCryptoManager = ApiKeyCryptoManager()

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val encryptedKey = prefs[KEY_API_KEY_ENCRYPTED].orEmpty()
        val encryptedIv = prefs[KEY_API_KEY_IV].orEmpty()
        val apiKey = when {
            encryptedKey.isNotBlank() && encryptedIv.isNotBlank() -> {
                runCatching {
                    apiKeyCryptoManager.decrypt(
                        EncryptedPayload(cipherTextBase64 = encryptedKey, ivBase64 = encryptedIv)
                    )
                }.onFailure {
                    Log.w(TAG, "解密 API Key 失败：${it.message}")
                }.getOrDefault("")
            }
            // 兼容旧版本（明文存储），新保存后会被自动抹除。
            else -> prefs[KEY_API_KEY_LEGACY].orEmpty()
        }
        val premiumToken = decryptPremiumToken(prefs)
        AppSettings(
            baseUrl = prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = apiKey,
            model = prefs[KEY_MODEL] ?: DEFAULT_MODEL,
            apiAccessMode = ApiAccessMode.fromStorage(
                prefs[KEY_API_ACCESS_MODE] ?: ApiAccessMode.CUSTOM.storageValue
            ),
            premiumAppToken = premiumToken,
            premiumExpiresAtMillis = prefs[KEY_PREMIUM_EXPIRES_AT],
            supervisionEnabled = prefs[KEY_SUPERVISION] ?: false,
            language = AppLanguage.fromStorage(prefs[KEY_LANGUAGE] ?: AppLanguage.SYSTEM.storageValue),
            focusGoal = prefs[KEY_FOCUS_GOAL] ?: DEFAULT_FOCUS_GOAL,
            forbiddenTags = prefs[KEY_FORBIDDEN_TAGS] ?: DEFAULT_FORBIDDEN_TAGS,
            useCustomPromptTemplate = prefs[KEY_USE_CUSTOM_PROMPT] ?: false,
            customPromptTemplate = prefs[KEY_CUSTOM_PROMPT] ?: DEFAULT_PROMPT_TEMPLATE,
            appMonitorBlacklist = prefs[KEY_APP_MONITOR_BLACKLIST].orEmpty(),
            appMonitorWhitelist = prefs[KEY_APP_MONITOR_WHITELIST].orEmpty(),
            interceptionMode = InterceptionMode.fromStorage(
                prefs[KEY_INTERCEPTION_MODE] ?: InterceptionMode.INSTANT_KILL.storageValue
            ),
            customCooldownText = prefs[KEY_CUSTOM_COOLDOWN_TEXT] ?: DEFAULT_CUSTOM_COOLDOWN_TEXT,
            customCooldownSeconds = (prefs[KEY_CUSTOM_COOLDOWN_SECONDS]
                ?: DEFAULT_CUSTOM_COOLDOWN_SECONDS).coerceIn(3, 120)
        )
    }

    suspend fun getSettingsSnapshot(): AppSettings = settingsFlow.first()

    suspend fun saveApiSettings(baseUrl: String, apiKey: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl.ifBlank { DEFAULT_BASE_URL }
            prefs[KEY_MODEL] = model.ifBlank { DEFAULT_MODEL }
            prefs[KEY_API_ACCESS_MODE] = ApiAccessMode.CUSTOM.storageValue
            if (apiKey.isBlank()) {
                prefs.remove(KEY_API_KEY_ENCRYPTED)
                prefs.remove(KEY_API_KEY_IV)
            } else {
                val payload = apiKeyCryptoManager.encrypt(apiKey)
                prefs[KEY_API_KEY_ENCRYPTED] = payload.cipherTextBase64
                prefs[KEY_API_KEY_IV] = payload.ivBase64
            }
            prefs.remove(KEY_API_KEY_LEGACY)
        }
    }

    suspend fun savePremiumMode(appToken: String, expiresAtMillis: Long? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_ACCESS_MODE] = ApiAccessMode.PREMIUM.storageValue
            prefs[KEY_BASE_URL] = PREMIUM_PROXY_BASE_URL
            if (appToken.isBlank()) {
                prefs.remove(KEY_PREMIUM_TOKEN_ENCRYPTED)
                prefs.remove(KEY_PREMIUM_TOKEN_IV)
                prefs.remove(KEY_PREMIUM_EXPIRES_AT)
            } else {
                val payload = apiKeyCryptoManager.encrypt(appToken)
                prefs[KEY_PREMIUM_TOKEN_ENCRYPTED] = payload.cipherTextBase64
                prefs[KEY_PREMIUM_TOKEN_IV] = payload.ivBase64
                if (expiresAtMillis != null) {
                    prefs[KEY_PREMIUM_EXPIRES_AT] = expiresAtMillis
                } else {
                    prefs.remove(KEY_PREMIUM_EXPIRES_AT)
                }
            }
        }
    }

    private fun decryptPremiumToken(prefs: Preferences): String {
        val encrypted = prefs[KEY_PREMIUM_TOKEN_ENCRYPTED].orEmpty()
        val iv = prefs[KEY_PREMIUM_TOKEN_IV].orEmpty()
        if (encrypted.isBlank() || iv.isBlank()) return ""
        return runCatching {
            apiKeyCryptoManager.decrypt(
                EncryptedPayload(cipherTextBase64 = encrypted, ivBase64 = iv)
            )
        }.onFailure {
            Log.w(TAG, "解密会员 Token 失败：${it.message}")
        }.getOrDefault("")
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

    suspend fun saveAppMonitorLists(blacklist: Set<String>, whitelist: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_MONITOR_BLACKLIST] = normalizePackageSet(blacklist)
            prefs[KEY_APP_MONITOR_WHITELIST] = normalizePackageSet(whitelist)
        }
    }

    suspend fun saveInterceptionMode(
        mode: InterceptionMode,
        customCooldownText: String,
        customCooldownSeconds: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INTERCEPTION_MODE] = mode.storageValue
            prefs[KEY_CUSTOM_COOLDOWN_TEXT] = customCooldownText.ifBlank { DEFAULT_CUSTOM_COOLDOWN_TEXT }
            prefs[KEY_CUSTOM_COOLDOWN_SECONDS] = customCooldownSeconds.coerceIn(3, 120)
        }
    }

    private fun normalizePackageSet(source: Set<String>): Set<String> {
        return source
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    companion object {
        private const val TAG = "OnlySight-SettingsRepo"
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY_ENCRYPTED = stringPreferencesKey("api_key_encrypted")
        private val KEY_API_KEY_IV = stringPreferencesKey("api_key_iv")
        private val KEY_API_KEY_LEGACY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_API_ACCESS_MODE = stringPreferencesKey("api_access_mode")
        private val KEY_SUPERVISION = booleanPreferencesKey("supervision_enabled")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_FOCUS_GOAL = stringPreferencesKey("focus_goal")
        private val KEY_FORBIDDEN_TAGS = stringPreferencesKey("forbidden_tags")
        private val KEY_USE_CUSTOM_PROMPT = booleanPreferencesKey("use_custom_prompt_template")
        private val KEY_CUSTOM_PROMPT = stringPreferencesKey("custom_prompt_template")
        private val KEY_APP_MONITOR_BLACKLIST = stringSetPreferencesKey("app_monitor_blacklist")
        private val KEY_APP_MONITOR_WHITELIST = stringSetPreferencesKey("app_monitor_whitelist")
        private val KEY_INTERCEPTION_MODE = stringPreferencesKey("interception_mode")
        private val KEY_CUSTOM_COOLDOWN_TEXT = stringPreferencesKey("custom_cooldown_text")
        private val KEY_CUSTOM_COOLDOWN_SECONDS = intPreferencesKey("custom_cooldown_seconds")
        private val KEY_PREMIUM_TOKEN_ENCRYPTED = stringPreferencesKey("premium_token_encrypted")
        private val KEY_PREMIUM_TOKEN_IV = stringPreferencesKey("premium_token_iv")
        private val KEY_PREMIUM_EXPIRES_AT = longPreferencesKey("premium_expires_at")
    }
}
