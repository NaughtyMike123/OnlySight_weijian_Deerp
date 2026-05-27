package com.focusai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条「视觉模型判定为娱乐 → 已执行回桌面」的记录。
 * 字段保留为兼容旧数据库迁移；视觉版没有屏幕文本，excerpt 永远为空字符串。
 */
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
