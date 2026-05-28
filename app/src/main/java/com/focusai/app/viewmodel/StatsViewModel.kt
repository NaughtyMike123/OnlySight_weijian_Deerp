package com.focusai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.FocusAiApplication
import com.focusai.app.data.db.InterceptionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val interceptionCount: Int = 0,
    val recentInterceptions: List<InterceptionEntity> = emptyList()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val statsRepository = (application as FocusAiApplication).statsRepository

    val uiState: StateFlow<StatsUiState> = combine(
        statsRepository.observeTodayInterceptionCount(),
        statsRepository.observeRecentInterceptions()
    ) { count, recent ->
        StatsUiState(
            interceptionCount = count,
            recentInterceptions = recent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )
}
