package com.lifebench.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lifebench.app.data.dao.*
import com.lifebench.app.data.entity.*

/**
 * 全局 Room 数据库：聚合所有本地实体，离线存储，无后端。
 *
 * 迁移约定（务必遵守，防数据丢失）：
 *  - 数据库 [version] 每次改 schema 必须 +1；
 *  - 每跨一个版本都要提供对应 Migration（如 3→4 加 MIGRATION_3_4）并在下方 addMigrations(...) 注册；
 *  - 严禁使用 fallbackToDestructiveMigration()（会清空用户数据，且覆盖安装时会连带丢失历史库）；
 *  - exportSchema=true 已开启，schema 导出到 app/schemas 并提交仓库，便于 Room 在编译期校验迁移。
 */
@Database(
    entities = [
        TodoEntity::class, AccountEntity::class, SleepEntity::class, RecipeEntity::class,
        DietLogEntity::class, FitnessPlanEntity::class, FitnessProfileEntity::class,
        SchulteResultEntity::class, TrainingResultEntity::class, PasswordEntity::class,
        NoteEntity::class, AnniversaryEntity::class, StepEntity::class, FocusSessionEntity::class,
        HabitEntity::class, HabitCheckInEntity::class
    ],
    version = 3,
    exportSchema = true
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
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1.0.0：todo 增加 quadrant 象限字段；fitness_plan 增加 date 字段。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo ADD COLUMN quadrant INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE fitness_plan ADD COLUMN date INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v1.0.0：新增习惯打卡模块（habit 习惯表 + habit_checkin 打卡表）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_checkin` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        /** 单例获取，确保全 App 共用同一数据库实例。 */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifebench.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
