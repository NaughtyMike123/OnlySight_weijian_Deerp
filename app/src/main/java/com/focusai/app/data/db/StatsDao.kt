package com.focusai.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Insert
    suspend fun insertInterception(entity: InterceptionEntity)

    @Query("SELECT COUNT(*) FROM interceptions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun observeInterceptionCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT * FROM interceptions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC LIMIT 10")
    fun observeRecentInterceptions(startOfDay: Long, endOfDay: Long): Flow<List<InterceptionEntity>>

    @Query("SELECT COUNT(*) FROM interceptions WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getInterceptionCount(startOfDay: Long, endOfDay: Long): Int

    @Query("DELETE FROM interceptions WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
