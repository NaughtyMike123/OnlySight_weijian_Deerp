package com.focusai.app

import android.app.Application
import com.focusai.app.data.StatsRepository
import com.focusai.app.data.api.ApiClientFactory
import com.focusai.app.data.api.PremiumActivateRepository
import com.focusai.app.data.api.PremiumVisionRepository
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
     * 视觉判定仓库：把 Base64 截图 → 多模态请求 → VisionDecision 的业务入口。
     * 由 [com.focusai.app.service.VisualSupervisionService] 按动态策略周期调用。
     */
    lateinit var visionRepository: VisionRepository
        private set

    lateinit var premiumActivateRepository: PremiumActivateRepository
        private set

    lateinit var premiumVisionRepository: PremiumVisionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        statsRepository = StatsRepository.from(AppDatabase.getInstance(this))
        apiClientFactory = ApiClientFactory(settingsRepository)
        premiumActivateRepository = PremiumActivateRepository(settingsRepository)
        premiumVisionRepository = PremiumVisionRepository(settingsRepository)
        visionRepository = VisionRepository(
            apiClientFactory = apiClientFactory,
            settingsRepository = settingsRepository,
            premiumVisionRepository = premiumVisionRepository
        )
    }
}
