package com.lifebench.app.data

import android.content.Context

/**
 * 仓库提供器：在 Application 中初始化，集中持有数据库与设置实例。
 * 各 ViewModel/页面通过 Repo.xxx 直接访问 DAO（DAO 已是类型安全的本地数据访问层）。
 * 这样避免引入 DI 框架，保持工程轻量、易读、易改。
 */
object Repo {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set

    /** 在 LifeBenchApplication.onCreate 中调用一次。 */
    fun init(context: Context) {
        db = AppDatabase.getInstance(context)
        settings = SettingsStore(context)
    }

    // —— 便捷暴露各 DAO ——
    val todo get() = db.todoDao()
    val account get() = db.accountDao()
    val sleep get() = db.sleepDao()
    val recipe get() = db.recipeDao()
    val diet get() = db.dietLogDao()
    val fitnessPlan get() = db.fitnessPlanDao()
    val fitnessProfile get() = db.fitnessProfileDao()
    val schulte get() = db.schulteResultDao()
    val training get() = db.trainingResultDao()
    val password get() = db.passwordDao()
    val note get() = db.noteDao()
    val anniversary get() = db.anniversaryDao()
    val step get() = db.stepDao()
    val focus get() = db.focusSessionDao()
    val habit get() = db.habitDao()
}
