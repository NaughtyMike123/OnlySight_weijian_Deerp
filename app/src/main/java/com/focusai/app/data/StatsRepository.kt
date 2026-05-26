package com.focusai.app.data

import com.focusai.app.data.db.AppDatabase
import com.focusai.app.data.db.FocusSessionEntity
import com.focusai.app.data.db.InterceptionEntity
import com.focusai.app.data.db.StatsDao
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class StatsRepository(private val statsDao: StatsDao) {

    private fun todayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis
        return start to end
    }

    fun observeTodayInterceptionCount(): Flow<Int> {
        val (start, end) = todayRange()
        return statsDao.observeInterceptionCount(start, end)
    }

    fun observeTodayFocusSeconds(): Flow<Long> {
        val (start, end) = todayRange()
        return statsDao.observeFocusSeconds(start, end)
    }

    fun observeRecentInterceptions(): Flow<List<InterceptionEntity>> {
        val (start, end) = todayRange()
        return statsDao.observeRecentInterceptions(start, end)
    }

    suspend fun recordInterception(
        packageName: String = "",
        appLabel: String = "",
        reasonType: String = "",
        reasonDetail: String = "",
        screenTextExcerpt: String = "",
        aiReply: String = ""
    ): Int {
        statsDao.insertInterception(
            InterceptionEntity(
                packageName = packageName,
                appLabel = appLabel,
                reasonType = reasonType,
                reasonDetail = reasonDetail,
                screenTextExcerpt = screenTextExcerpt,
                aiReply = aiReply
            )
        )
        // Remove records older than 30 days so the database stays lean without
        // ever truncating today's count (the previous LIMIT 10 caused count to
        // freeze at ≤10 after 10 interceptions across the whole table lifetime).
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        statsDao.deleteOlderThan(thirtyDaysAgo)
        val (start, end) = todayRange()
        return statsDao.getInterceptionCount(start, end)
    }

    suspend fun getInterceptionById(id: Long): InterceptionEntity? {
        return statsDao.getInterceptionById(id)
    }

    suspend fun recordFocusSession(durationSeconds: Long) {
        if (durationSeconds <= 0) return
        statsDao.insertFocusSession(FocusSessionEntity(durationSeconds = durationSeconds))
    }

    companion object {
        fun from(database: AppDatabase): StatsRepository {
            return StatsRepository(database.statsDao())
        }
    }
}
