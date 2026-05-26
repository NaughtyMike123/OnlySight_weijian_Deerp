package com.focusai.app

import android.app.Application
import com.focusai.app.data.StatsRepository
import com.focusai.app.data.api.ApiClientFactory
import com.focusai.app.data.db.AppDatabase
import com.focusai.app.data.prefs.AppListRepository
import com.focusai.app.data.prefs.SettingsRepository

class FocusAiApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var statsRepository: StatsRepository
        private set

    lateinit var apiClientFactory: ApiClientFactory
        private set

    lateinit var appListRepository: AppListRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        statsRepository = StatsRepository.from(AppDatabase.getInstance(this))
        apiClientFactory = ApiClientFactory(settingsRepository)
        appListRepository = AppListRepository(this)
    }
}
