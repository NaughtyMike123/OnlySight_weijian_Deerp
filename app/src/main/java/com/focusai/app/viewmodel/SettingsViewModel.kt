package com.focusai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.FocusAiApplication
import com.focusai.app.data.prefs.DEFAULT_BASE_URL
import com.focusai.app.data.prefs.DEFAULT_MODEL
import com.focusai.app.data.prefs.DEFAULT_PROMPT_TEMPLATE
import com.focusai.app.data.api.ChatCompletionRequest
import com.focusai.app.data.api.ChatMessage
import com.focusai.app.data.api.OpenAiApi
import com.focusai.app.util.PromptTemplateRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class SettingsUiState(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val savedMessage: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val useCustomPromptTemplate: Boolean = false,
    val promptTemplateDraft: String = DEFAULT_PROMPT_TEMPLATE,
    val promptPreview: String = DEFAULT_PROMPT_TEMPLATE,
    val promptSavedMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as FocusAiApplication).settingsRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var savedFocusGoal: String = ""
    private var savedForbiddenTags: String = ""

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                savedFocusGoal = settings.focusGoal
                savedForbiddenTags = settings.forbiddenTags
                _uiState.update {
                    it.copy(
                        baseUrl = settings.baseUrl,
                        apiKey = settings.apiKey,
                        model = settings.model,
                        useCustomPromptTemplate = settings.useCustomPromptTemplate,
                        promptTemplateDraft = settings.customPromptTemplate,
                        promptPreview = renderPreview(
                            template = if (settings.useCustomPromptTemplate) {
                                settings.customPromptTemplate
                            } else {
                                DEFAULT_PROMPT_TEMPLATE
                            },
                            focusGoal = settings.focusGoal,
                            forbiddenTags = settings.forbiddenTags
                        )
                    )
                }
            }
        }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    fun updateModel(value: String) {
        _uiState.update { it.copy(model = value) }
    }

    fun saveSettings(savedLabel: String) {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.saveApiSettings(state.baseUrl, state.apiKey, state.model)
            _uiState.update { it.copy(savedMessage = savedLabel) }
        }
    }

    fun toggleUseCustomPrompt(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                useCustomPromptTemplate = enabled,
                promptPreview = renderPreview(
                    template = if (enabled) current.promptTemplateDraft else DEFAULT_PROMPT_TEMPLATE,
                    focusGoal = savedFocusGoal,
                    forbiddenTags = savedForbiddenTags
                )
            )
        }
    }

    fun updatePromptTemplateDraft(value: String) {
        _uiState.update { current ->
            current.copy(
                promptTemplateDraft = value,
                promptPreview = renderPreview(
                    template = if (current.useCustomPromptTemplate) value else DEFAULT_PROMPT_TEMPLATE,
                    focusGoal = savedFocusGoal,
                    forbiddenTags = savedForbiddenTags
                )
            )
        }
    }

    fun savePromptTemplate(savedLabel: String) {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.savePromptTemplate(
                useCustom = state.useCustomPromptTemplate,
                template = state.promptTemplateDraft
            )
            _uiState.update { it.copy(promptSavedMessage = savedLabel) }
        }
    }

    fun clearPromptSavedMessage() {
        _uiState.update { it.copy(promptSavedMessage = null) }
    }

    fun testConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(testingConnection = true, connectionMessage = null) }

            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val api = createApiFromDraft(
                        baseUrl = state.baseUrl,
                        apiKey = state.apiKey
                    )
                    val reply = api.chatCompletions(
                        ChatCompletionRequest(
                            model = state.model.ifBlank { DEFAULT_MODEL },
                            messages = listOf(ChatMessage(role = "user", content = "ping")),
                            maxTokens = 8
                        )
                    ).choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                    if (reply.isBlank()) {
                        "连接成功，但回复为空。请检查模型名是否正确。"
                    } else {
                        "连接成功：API 可用，模型已返回内容。"
                    }
                }
            }.getOrElse { throwable ->
                when (throwable) {
                    is HttpException -> {
                        when (throwable.code()) {
                            401 -> "连接失败：API Key 无效（401）"
                            402 -> "连接失败：账户余额不足（402）"
                            404 -> "连接失败：Base URL 路径错误（404）"
                            429 -> "连接失败：请求过于频繁（429）"
                            else -> "连接失败：HTTP ${throwable.code()}"
                        }
                    }
                    is SocketTimeoutException -> "连接失败：请求超时，请检查网络"
                    else -> "连接失败：${throwable.message ?: throwable.javaClass.simpleName}"
                }
            }

            _uiState.update { it.copy(testingConnection = false, connectionMessage = message) }
        }
    }

    fun clearSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }

    private fun renderPreview(template: String, focusGoal: String, forbiddenTags: String): String {
        return PromptTemplateRenderer.render(
            template = template,
            focusGoal = focusGoal,
            forbiddenTags = forbiddenTags
        )
    }

    private fun createApiFromDraft(baseUrl: String, apiKey: String): OpenAiApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }
}
