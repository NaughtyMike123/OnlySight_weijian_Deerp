package com.focusai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.FocusAiApplication
import com.focusai.app.service.VisualSupervisionService
import com.focusai.app.util.AccessibilityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel：负责"监督开关、无障碍权限状态、监督规则草稿"三件事。
 *
 * 注意：真正的"启停截屏服务"动作在 UI 层完成，因为申请 MediaProjection 必须有
 * Activity 上下文 + ActivityResult API。这里只负责持久化开关状态与自愈状态对齐。
 */
data class FocusUiState(
    val supervisionEnabled: Boolean = false,
    val accessibilityGranted: Boolean = false,
    /** 用户在输入框内当前编辑的「专注目标」（未保存）。 */
    val focusGoalDraft: String = "",
    /** 用户在输入框内当前编辑的「禁止标签」（未保存）。 */
    val forbiddenTagsDraft: String = "",
    /** 草稿是否与已存值不同，控制「保存」按钮高亮。 */
    val rulesDirty: Boolean = false,
    val rulesSavedMessage: String? = null
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusAiApplication
    private val settingsRepository = app.settingsRepository

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    /** 已存的专注目标，用于和草稿对比是否 dirty。 */
    private var savedFocusGoal: String = ""
    private var savedForbiddenTags: String = ""

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                savedFocusGoal = settings.focusGoal
                savedForbiddenTags = settings.forbiddenTags

                // 关键自愈逻辑：若 prefs 里残留 supervisionEnabled=true 但前台服务
                // 实际未运行（典型场景：用户上次启用后杀了进程 / 重启过手机），
                // 把开关同步回 false，强制用户重新申请 MediaProjection。
                val effectiveEnabled = settings.supervisionEnabled && VisualSupervisionService.running
                if (settings.supervisionEnabled && !VisualSupervisionService.running) {
                    settingsRepository.setSupervisionEnabled(false)
                }

                _uiState.update { current ->
                    val isFirstLoad = current.focusGoalDraft.isEmpty() &&
                        current.forbiddenTagsDraft.isEmpty()
                    current.copy(
                        supervisionEnabled = effectiveEnabled,
                        focusGoalDraft = if (isFirstLoad) settings.focusGoal else current.focusGoalDraft,
                        forbiddenTagsDraft = if (isFirstLoad) settings.forbiddenTags else current.forbiddenTagsDraft,
                        rulesDirty = if (isFirstLoad) false else current.rulesDirty
                    )
                }
            }
        }
        refreshAccessibilityStatus()
    }

    fun refreshAccessibilityStatus() {
        val granted = AccessibilityUtils.isAccessibilityServiceEnabled(getApplication())
        val currentlyRunning = VisualSupervisionService.running
        _uiState.update { current ->
            current.copy(
                accessibilityGranted = granted,
                supervisionEnabled = current.supervisionEnabled && currentlyRunning
            )
        }
        if (!currentlyRunning) {
            viewModelScope.launch { settingsRepository.setSupervisionEnabled(false) }
        }
    }

    /**
     * 仅落盘开关状态。真正启停服务由 UI 层完成。
     */
    fun persistSupervisionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSupervisionEnabled(enabled)
        }
    }

    fun updateFocusGoalDraft(value: String) {
        _uiState.update {
            it.copy(
                focusGoalDraft = value,
                rulesDirty = value != savedFocusGoal || it.forbiddenTagsDraft != savedForbiddenTags
            )
        }
    }

    fun updateForbiddenTagsDraft(value: String) {
        _uiState.update {
            it.copy(
                forbiddenTagsDraft = value,
                rulesDirty = value != savedForbiddenTags || it.focusGoalDraft != savedFocusGoal
            )
        }
    }

    fun saveRules(savedLabel: String) {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.saveFocusGoal(state.focusGoalDraft)
            settingsRepository.saveForbiddenTags(state.forbiddenTagsDraft)
            _uiState.update { it.copy(rulesSavedMessage = savedLabel, rulesDirty = false) }
        }
    }

    fun clearRulesSavedMessage() {
        _uiState.update { it.copy(rulesSavedMessage = null) }
    }
}
