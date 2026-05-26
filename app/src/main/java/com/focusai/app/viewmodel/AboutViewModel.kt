package com.focusai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.BuildConfig
import com.focusai.app.FocusAiApplication
import com.focusai.app.data.prefs.AppLanguage
import com.focusai.app.util.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AboutUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val showDonateDialog: Boolean = false
)

class AboutViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as FocusAiApplication).settingsRepository

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(language = settings.language) }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            LocaleHelper.applyLanguage(language)
            _uiState.update { it.copy(language = language) }
        }
    }

    fun showDonateDialog() {
        _uiState.update { it.copy(showDonateDialog = true) }
    }

    fun hideDonateDialog() {
        _uiState.update { it.copy(showDonateDialog = false) }
    }
}
