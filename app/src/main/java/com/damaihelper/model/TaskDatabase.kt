package com.damaihelper.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ============================================================================
// 📅 数据库版本升级：2026-03-29 15:47
// 🔧 变更内容：
//   - version: 1 → 2
//   - 🆕 添加数据库迁移策略（支持新增字段）
//   - 新增字段：audienceName, selectedPrice, sessionId, priceTiers, 
//               audienceIndex, grabMode, quantity
// ============================================================================

@Database(entities = [TicketTask::class], version = 2, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        // 🆕 数据库迁移：version 1 → 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段（如果不存在）
                // audienceName: 用于自动选择观演人
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN audienceName TEXT NOT NULL DEFAULT ''"
                )
                // selectedPrice: 用于步骤 2 选择票档
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN selectedPrice TEXT NOT NULL DEFAULT ''"
                )
                // sessionId: 指定场次 ID
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''"
                )
                // priceTiers: 多票档备选
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN priceTiers TEXT NOT NULL DEFAULT ''"
                )
                // audienceIndex: 观影人索引
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN audienceIndex INTEGER NOT NULL DEFAULT 1"
                )
                // grabMode: 抢票模式
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN grabMode TEXT NOT NULL DEFAULT 'normal'"
                )
                // quantity: 票数（兼容字段）
                database.execSQL(
                    "ALTER TABLE ticket_tasks ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1"
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
                .addMigrations(MIGRATION_1_2)  // 🆕 添加迁移策略
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

