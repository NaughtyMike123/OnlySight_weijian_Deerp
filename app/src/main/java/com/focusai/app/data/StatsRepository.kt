package com.focusai.app.data

import com.focusai.app.data.db.AppDatabase
import com.focusai.app.data.db.InterceptionEntity
import com.focusai.app.data.db.StatsDao
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 视觉监督版的统计仓库：只关心"今日被打断了多少次 + 最近打断记录"。
 * 旧版的番茄钟专注时长统计已废弃。
 */
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

    fun observeRecentInterceptions(): Flow<List<InterceptionEntity>> {
        val (start, end) = todayRange()
        return statsDao.observeRecentInterceptions(start, end)
    }

    /**
     * 记录一次拦截，并返回"今天已拦截多少次"用于通知文案。
     * 顺便清理 30 天前的旧记录，避免数据库无限增长。
     */
    suspend fun recordInterception(
        reasonType: String = "",
        reasonDetail: String = "",
        aiReply: String = ""
    ): Int {
        statsDao.insertInterception(
            InterceptionEntity(
                reasonType = reasonType,
                reasonDetail = reasonDetail,
                aiReply = aiReply
            )
        )
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        statsDao.deleteOlderThan(thirtyDaysAgo)
        val (start, end) = todayRange()
        return statsDao.getInterceptionCount(start, end)
    }

    companion object {
        fun from(database: AppDatabase): StatsRepository {
            return StatsRepository(database.statsDao())
        }
    }
}
