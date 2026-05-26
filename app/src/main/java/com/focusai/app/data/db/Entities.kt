package com.focusai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interceptions")
data class InterceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val appLabel: String = "",
    val reasonType: String = "",
    val reasonDetail: String = "",
    val screenTextExcerpt: String = "",
    val aiReply: String = ""
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
)
