package com.focusai.app

import android.app.Application
import com.focusai.app.data.StatsRepository
import com.focusai.app.data.api.ApiClientFactory
import com.focusai.app.data.api.VisionRepository
import com.focusai.app.data.db.AppDatabase
import com.focusai.app.data.prefs.SettingsRepository

class FocusAiApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var statsRepository: StatsRepository
        private set

    lateinit var apiClientFactory: ApiClientFactory
        private set

    /**
     * 视觉判定仓库：把 Base64 截图 → 多模态请求 → Boolean 判定的"业务级"入口。
     * 由 [com.focusai.app.service.VisualSupervisionService] 在每个 4 秒周期调用一次。
     */
    lateinit var visionRepository: VisionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        statsRepository = StatsRepository.from(AppDatabase.getInstance(this))
        apiClientFactory = ApiClientFactory(settingsRepository)
        visionRepository = VisionRepository(apiClientFactory, settingsRepository)
    }
}
