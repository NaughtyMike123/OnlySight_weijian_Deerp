package com.focusai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地拦截日志（严格最小化）：
 * - 只保留时间戳、包名、AI 原因文本
 * - 不保存截图，避免隐私泄露与数据库膨胀
 */
@Entity(tableName = "interceptions")
data class InterceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val aiReason: String = ""
)
