package com.lifebench.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lifebench.app.data.dao.*
import com.lifebench.app.data.entity.*

/**
 * 全局 Room 数据库：聚合所有本地实体，离线存储，无后端。
 * version=1；升级时通过 Migration 处理（调试期可用 fallbackToDestructiveMigration）。
 */
@Database(
    entities = [
        TodoEntity::class, AccountEntity::class, SleepEntity::class, RecipeEntity::class,
        DietLogEntity::class, FitnessPlanEntity::class, FitnessProfileEntity::class,
        SchulteResultEntity::class, TrainingResultEntity::class, PasswordEntity::class,
        NoteEntity::class, AnniversaryEntity::class, StepEntity::class, FocusSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun accountDao(): AccountDao
    abstract fun sleepDao(): SleepDao
    abstract fun recipeDao(): RecipeDao
    abstract fun dietLogDao(): DietLogDao
    abstract fun fitnessPlanDao(): FitnessPlanDao
    abstract fun fitnessProfileDao(): FitnessProfileDao
    abstract fun schulteResultDao(): SchulteResultDao
    abstract fun trainingResultDao(): TrainingResultDao
    abstract fun passwordDao(): PasswordDao
    abstract fun noteDao(): NoteDao
    abstract fun anniversaryDao(): AnniversaryDao
    abstract fun stepDao(): StepDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 单例获取，确保全 App 共用同一数据库实例。 */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifebench.db"
                ).fallbackToDestructiveMigration() // 调试期改 schema 不崩；上线请改为 Migration
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
