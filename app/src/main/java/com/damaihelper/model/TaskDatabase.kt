package com.damaihelper.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ============================================================================
// 📅 数据库版本升级：2026-03-30 17:55
// 🔧 变更内容：
//   - version: 2 → 3
//   - 🆕 添加观众表（audiences）
//   - 🐛 修复 MIGRATION_1_2 重复定义问题
//   - 新增字段：audienceName, selectedPrice, sessionId, priceTiers, 
//               audienceIndex, grabMode, quantity
// ============================================================================

@Database(entities = [TicketTask::class, Audience::class], version = 3, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun audienceDao(): AudienceDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        // 🆕 数据库迁移：version 1 → 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段（使用 try-catch 避免重复添加）
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN audienceName TEXT NOT NULL DEFAULT ''"
                    )
                } catch (e: Exception) {}
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN selectedPrice TEXT NOT NULL DEFAULT ''"
                    )
                } catch (e: Exception) {}
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''"
                    )
                } catch (e: Exception) {}
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN priceTiers TEXT NOT NULL DEFAULT ''"
                    )
                } catch (e: Exception) {}
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN audienceIndex INTEGER NOT NULL DEFAULT 1"
                    )
                } catch (e: Exception) {}
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN grabMode TEXT NOT NULL DEFAULT 'normal'"
                    )
                } catch (e: Exception) {}
                try {
                    database.execSQL(
                        "ALTER TABLE ticket_tasks ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1"
                    )
                } catch (e: Exception) {}
            }
        }

        // 🆕 数据库迁移：version 2 → 3 (添加观众表)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建观众表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audiences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        idType TEXT NOT NULL DEFAULT '身份证',
                        idNumber TEXT NOT NULL DEFAULT '',
                        phone TEXT NOT NULL DEFAULT '',
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createTime INTEGER NOT NULL DEFAULT 0,
                        remark TEXT NOT NULL DEFAULT ''
                    )
                    """
                )
            }
        }

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // 🆕 添加迁移策略
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

