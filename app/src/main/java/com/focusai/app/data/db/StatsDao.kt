package com.focusai.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Insert
    suspend fun insertInterception(entity: InterceptionEntity)

    @Insert
    suspend fun insertFocusSession(entity: FocusSessionEntity)

    @Query("SELECT COUNT(*) FROM interceptions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun observeInterceptionCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun observeFocusSeconds(startOfDay: Long, endOfDay: Long): Flow<Long>

    @Query("SELECT * FROM interceptions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC LIMIT 10")
    fun observeRecentInterceptions(startOfDay: Long, endOfDay: Long): Flow<List<InterceptionEntity>>

    @Query("SELECT * FROM interceptions WHERE id = :id LIMIT 1")
    suspend fun getInterceptionById(id: Long): InterceptionEntity?

    @Query("SELECT COUNT(*) FROM interceptions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getInterceptionCount(startOfDay: Long, endOfDay: Long): Int

    @Query("DELETE FROM interceptions WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
