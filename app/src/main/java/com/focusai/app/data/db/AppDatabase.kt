package com.focusai.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InterceptionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusai.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interceptions ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE interceptions ADD COLUMN appLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE interceptions ADD COLUMN reasonType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE interceptions ADD COLUMN reasonDetail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE interceptions ADD COLUMN screenTextExcerpt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE interceptions ADD COLUMN aiReply TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3：删除番茄钟统计表，视觉监督版本不再需要专注时长统计。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS focus_sessions")
            }
        }

        /**
         * v4：拦截日志收敛为最小字段集合。
         * 不保留历史截图或冗余字段，只迁移时间、包名与原因文本。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interceptions RENAME TO interceptions_legacy")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS interceptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        packageName TEXT NOT NULL,
                        aiReason TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO interceptions(timestamp, packageName, aiReason)
                    SELECT timestamp, packageName, COALESCE(reasonDetail, '')
                    FROM interceptions_legacy
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE interceptions_legacy")
            }
        }
    }
}
