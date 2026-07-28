package com.lifebench.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办事项。priority: 0=低 1=中 2=高；repeatMode: 永不/每天/每周/每月/每年；archived 已完成归档。
 */
@Entity(tableName = "todo")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    val priority: Int = 1,
    val quadrant: Int = 2,            // 科维四象限：0重要紧急 1重要不紧急 2紧急不重要 3不重要不紧急
    val dueTime: Long? = null,        // 到期时间(ms)，用于提醒
    val repeatMode: String = "永不",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 收支记账。type: 0=支出 1=收入；date 为当天 0 点的 dayKey(ms)，便于按日聚合。
 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,                    // 0 支出, 1 收入
    val category: String,
    val amount: Double,
    val note: String = "",
    val date: Long,                  // dayKey
    val receiptPath: String? = null, // 小票照片本地路径
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 睡眠记录。sleepTime/wakeTime 为实际时刻(ms)，durationMin 为自动核算分钟数（已处理跨午夜）。
 */
@Entity(tableName = "sleep")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                  // 入睡日 dayKey
    val sleepTime: Long,             // 入睡时刻(ms)
    val wakeTime: Long,              // 起床时刻(ms)
    val durationMin: Int,            // 睡眠时长(分钟)
    val quality: Int = 0,            // 0 未评 1 差 2 中 3 好
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 菜谱。steps 以 JSON 字符串存储分步步骤；favorite 收藏常用。
 */
@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ingredients: String,         // 食材清单（换行分隔）
    val steps: String,               // 分步步骤 JSON
    val imagePath: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 饮食打卡。mealType: 0=早 1=午 2=晚。
 */
@Entity(tableName = "diet_log")
data class DietLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                  // dayKey
    val mealType: Int,               // 0 早 1 午 2 晚
    val foodName: String,
    val calories: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 健身计划动作（7 天循环）。dayIndex 0~6。
 */
@Entity(tableName = "fitness_plan")
data class FitnessPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayIndex: Int,               // 0~6 计划模板的星期站位；记录动作时为所属 dayKey 转换的索引
    val actionName: String,
    val sets: Int = 0,
    val reps: Int = 0,
    val durationMin: Int = 0,
    val calories: Int = 0,
    val done: Boolean = false,
    val date: Long = 0              // 0=计划模板；>0=该日实际记录的动作（dayKey）
)

/**
 * 健身资料（单行，id 固定为 1）。用于生成 7 天计划。
 */
@Entity(tableName = "fitness_profile")
data class FitnessProfileEntity(
    @PrimaryKey val id: Int = 1,
    val height: Int = 170,
    val weight: Int = 60,
    val level: String = "初级",       // 初级/中级/高级
    val goal: String = "减脂"          // 减脂/增肌/塑形
)

/**
 * 舒尔特方格成绩。efficiency 为效率分；size 为方格规格；mode 点错模式。
 */
@Entity(tableName = "schulte_result")
data class SchulteResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val size: Int,
    val timeMs: Long,
    val errors: Int,
    val efficiency: Float,
    val mode: String,                // 中断 / 继续
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 通用训练成绩（专注力/记忆力/逻辑/速读各板块共用）。
 */
@Entity(tableName = "training_result")
data class TrainingResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,            // 专注力/记忆力/逻辑/速读视幅/速读眼动/RSVP/默读矫正/理解闯关
    val score: Float,
    val wpm: Int = 0,                // 速读：每分钟词数
    val accuracy: Float = 0f,        // 速读/理解：正确率 0~1
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 密码保险箱条目。passwordEnc 为加密后的密文（见 util/CryptoUtil）。
 */
@Entity(tableName = "password_item")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val group: String,               // 网站 / 软件 / 银行卡
    val title: String,
    val account: String,
    val passwordEnc: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 随手笔记。content 以简化富文本（含标记）存储；imagePaths 为图片路径 JSON 数组。
 */
@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "默认",
    val imagePaths: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 纪念日。date 为事件时刻(ms)；repeatYearly 是否每年重复。
 */
@Entity(tableName = "anniversary")
data class AnniversaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long,
    val repeatYearly: Boolean = true,
    val icon: String = "🎉",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 每日步数。date 为 dayKey；steps 为当日步数（已从传感器累计值换算）。
 */
@Entity(tableName = "step_log")
data class StepEntity(
    @PrimaryKey val date: Long,      // dayKey
    val steps: Int,
    val calories: Int = 0
)

/**
 * 番茄专注会话。type: 专注/休息；interrupted 是否被中断。
 */
@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val plannedMin: Int,
    val type: String,                // 专注 / 休息
    val interrupted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 习惯。colorIndex 指向 HabitDotPalette 色板索引；icon 用 emoji；archived 已停用。
 * 删除习惯时由仓库层同步清理其打卡记录（避免外键级联带来的迁移复杂度）。
 */
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "✅",
    val colorIndex: Int = 0,         // 指向 HabitDotPalette 色板索引（0~7）
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 习惯打卡。date 为当天 0 点的 dayKey(ms)；(habitId, date) 唯一，重复打卡覆盖。
 */
@Entity(tableName = "habit_checkin")
data class HabitCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Long,                  // dayKey
    val createdAt: Long = System.currentTimeMillis()
)
