package com.focusai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.FocusAiApplication
import com.focusai.app.util.AccessibilityUtils
import com.focusai.app.util.TimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PomodoroMode { COUNTDOWN, COUNT_UP }

const val POMODORO_DEFAULT_SECONDS = 1500L // 25 分钟

data class FocusUiState(
    val supervisionEnabled: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val pomodoroSeconds: Long = POMODORO_DEFAULT_SECONDS,
    val pomodoroRunning: Boolean = false,
    val pomodoroMode: PomodoroMode = PomodoroMode.COUNTDOWN,
    val displayTime: String = TimeFormatter.formatPomodoro(POMODORO_DEFAULT_SECONDS),
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
    private val statsRepository = app.statsRepository

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var sessionStartSeconds: Long = 0

    /** 已存的专注目标，用于和草稿对比是否 dirty。 */
    private var savedFocusGoal: String = ""
    private var savedForbiddenTags: String = ""

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                savedFocusGoal = settings.focusGoal
                savedForbiddenTags = settings.forbiddenTags
                _uiState.update { current ->
                    val isFirstLoad = current.focusGoalDraft.isEmpty() &&
                        current.forbiddenTagsDraft.isEmpty()
                    current.copy(
                        supervisionEnabled = settings.supervisionEnabled,
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
        _uiState.update { it.copy(accessibilityGranted = granted) }
    }

    fun toggleSupervision(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSupervisionEnabled(enabled)
        }
    }

    fun updateFocusGoalDraft(value: String) {
        _uiState.update {
            it.copy(focusGoalDraft = value, rulesDirty = value != savedFocusGoal || it.forbiddenTagsDraft != savedForbiddenTags)
        }
    }

    fun updateForbiddenTagsDraft(value: String) {
        _uiState.update {
            it.copy(forbiddenTagsDraft = value, rulesDirty = value != savedForbiddenTags || it.focusGoalDraft != savedFocusGoal)
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

    fun togglePomodoroMode() {
        resetPomodoro()
        _uiState.update {
            val newMode = if (it.pomodoroMode == PomodoroMode.COUNTDOWN) {
                PomodoroMode.COUNT_UP
            } else {
                PomodoroMode.COUNTDOWN
            }
            val seconds = if (newMode == PomodoroMode.COUNTDOWN) POMODORO_DEFAULT_SECONDS else 0L
            it.copy(
                pomodoroMode = newMode,
                pomodoroSeconds = seconds,
                displayTime = TimeFormatter.formatPomodoro(seconds)
            )
        }
    }

    fun startPomodoro() {
        if (_uiState.value.pomodoroRunning) return
        sessionStartSeconds = _uiState.value.pomodoroSeconds
        _uiState.update { it.copy(pomodoroRunning = true) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.pomodoroRunning) {
                delay(1000)
                _uiState.update { state ->
                    val next = when (state.pomodoroMode) {
                        PomodoroMode.COUNTDOWN -> (state.pomodoroSeconds - 1).coerceAtLeast(0)
                        PomodoroMode.COUNT_UP -> state.pomodoroSeconds + 1
                    }
                    val stillRunning = !(state.pomodoroMode == PomodoroMode.COUNTDOWN && next == 0L)
                    state.copy(
                        pomodoroSeconds = next,
                        displayTime = TimeFormatter.formatPomodoro(next),
                        pomodoroRunning = stillRunning
                    )
                }
                if (!_uiState.value.pomodoroRunning) {
                    onPomodoroFinished()
                }
            }
        }
    }

    fun pausePomodoro() {
        _uiState.update { it.copy(pomodoroRunning = false) }
        timerJob?.cancel()
        viewModelScope.launch {
            if (_uiState.value.pomodoroMode == PomodoroMode.COUNT_UP && _uiState.value.pomodoroSeconds > 0) {
                statsRepository.recordFocusSession(_uiState.value.pomodoroSeconds)
            }
        }
    }

    fun resetPomodoro() {
        timerJob?.cancel()
        val seconds = if (_uiState.value.pomodoroMode == PomodoroMode.COUNTDOWN) {
            POMODORO_DEFAULT_SECONDS
        } else {
            0L
        }
        _uiState.update {
            it.copy(
                pomodoroRunning = false,
                pomodoroSeconds = seconds,
                displayTime = TimeFormatter.formatPomodoro(seconds)
            )
        }
    }

    private fun onPomodoroFinished() {
        viewModelScope.launch {
            val elapsed = when (_uiState.value.pomodoroMode) {
                PomodoroMode.COUNTDOWN -> POMODORO_DEFAULT_SECONDS
                PomodoroMode.COUNT_UP -> _uiState.value.pomodoroSeconds
            }
            statsRepository.recordFocusSession(elapsed)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val POMODORO_DEFAULT_SECONDS = 25 * 60L
    }
}
