package com.lifebench.app.data.dao

import androidx.room.*
import androidx.room.OnConflictStrategy
import com.lifebench.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Insert suspend fun insert(e: TodoEntity): Long
    @Update suspend fun update(e: TodoEntity)
    @Delete suspend fun delete(e: TodoEntity)
    @Query("SELECT * FROM todo WHERE archived=0 ORDER BY done ASC, quadrant ASC, createdAt DESC")
    fun observeActive(): Flow<List<TodoEntity>>
    @Query("SELECT * FROM todo WHERE archived=1 ORDER BY createdAt DESC")
    fun observeArchived(): Flow<List<TodoEntity>>
}

@Dao
interface AccountDao {
    @Insert suspend fun insert(e: AccountEntity): Long
    @Update suspend fun update(e: AccountEntity)
    @Delete suspend fun delete(e: AccountEntity)
    @Query("SELECT * FROM account ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM account WHERE date BETWEEN :s AND :e ORDER BY date DESC")
    fun observeRange(s: Long, e: Long): Flow<List<AccountEntity>>
    @Query("SELECT COALESCE(SUM(amount),0) FROM account WHERE type=:type AND date BETWEEN :s AND :e")
    suspend fun sumByType(type: Int, s: Long, e: Long): Double
    @Query("SELECT category, SUM(amount) AS sum FROM account WHERE type=:type AND date BETWEEN :s AND :e GROUP BY category")
    suspend fun sumByCategory(type: Int, s: Long, e: Long): List<CategorySum>
}

@Suppress("unused")
data class CategorySum(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "sum") val sum: Double
)

@Dao
interface SleepDao {
    @Insert suspend fun insert(e: SleepEntity): Long
    @Delete suspend fun delete(e: SleepEntity)
    @Query("DELETE FROM sleep WHERE date=:day")
    suspend fun deleteByDate(day: Long)
    @Transaction
    suspend fun upsertByDate(e: SleepEntity) {
        deleteByDate(e.date)
        insert(e)
    }
    @Query("SELECT * FROM sleep ORDER BY date DESC LIMIT 7")
    fun observeRecent(): Flow<List<SleepEntity>>
    @Query("SELECT * FROM sleep")
    suspend fun getAll(): List<SleepEntity>
    @Query("SELECT * FROM sleep WHERE date=:day LIMIT 1")
    suspend fun getByDate(day: Long): SleepEntity?
}

@Dao
interface RecipeDao {
    @Insert suspend fun insert(e: RecipeEntity): Long
    @Update suspend fun update(e: RecipeEntity)
    @Delete suspend fun delete(e: RecipeEntity)
    @Query("SELECT * FROM recipe ORDER BY favorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>
}

@Dao
interface DietLogDao {
    @Insert suspend fun insert(e: DietLogEntity): Long
    @Update suspend fun update(e: DietLogEntity)
    @Delete suspend fun delete(e: DietLogEntity)
    @Query("SELECT * FROM diet_log WHERE date=:day ORDER BY mealType ASC")
    fun observeByDate(day: Long): Flow<List<DietLogEntity>>
    @Query("SELECT * FROM diet_log")
    suspend fun getAll(): List<DietLogEntity>
}

@Dao
interface FitnessPlanDao {
    @Insert suspend fun insert(e: FitnessPlanEntity): Long
    @Update suspend fun update(e: FitnessPlanEntity)
    @Delete suspend fun delete(e: FitnessPlanEntity)
    @Query("DELETE FROM fitness_plan")
    suspend fun clear()
    @Query("SELECT * FROM fitness_plan ORDER BY dayIndex ASC")
    fun observeAll(): Flow<List<FitnessPlanEntity>>
}

@Dao
interface FitnessProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: FitnessProfileEntity)
    @Query("SELECT * FROM fitness_profile WHERE id=1")
    suspend fun get(): FitnessProfileEntity?
}

@Dao
interface SchulteResultDao {
    @Insert suspend fun insert(e: SchulteResultEntity): Long
    @Query("SELECT * FROM schulte_result ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SchulteResultEntity>>
}

@Dao
interface TrainingResultDao {
    @Insert suspend fun insert(e: TrainingResultEntity): Long
    @Query("SELECT * FROM training_result WHERE category=:cat ORDER BY createdAt DESC")
    fun observeByCategory(cat: String): Flow<List<TrainingResultEntity>>
    @Query("SELECT * FROM training_result ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrainingResultEntity>>
}

@Dao
interface PasswordDao {
    @Insert suspend fun insert(e: PasswordEntity): Long
    @Update suspend fun update(e: PasswordEntity)
    @Delete suspend fun delete(e: PasswordEntity)
    @Query("SELECT * FROM password_item ORDER BY `group` ASC, title ASC")
    fun observeAll(): Flow<List<PasswordEntity>>
}

@Dao
interface NoteDao {
    @Insert suspend fun insert(e: NoteEntity): Long
    @Update suspend fun update(e: NoteEntity)
    @Delete suspend fun delete(e: NoteEntity)
    @Query("SELECT * FROM note ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM note WHERE id=:id")
    suspend fun getById(id: Long): NoteEntity?
}

@Dao
interface AnniversaryDao {
    @Insert suspend fun insert(e: AnniversaryEntity): Long
    @Update suspend fun update(e: AnniversaryEntity)
    @Delete suspend fun delete(e: AnniversaryEntity)
    @Query("SELECT * FROM anniversary ORDER BY date ASC")
    fun observeAll(): Flow<List<AnniversaryEntity>>
}

@Dao
interface StepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: StepEntity)
    @Query("SELECT * FROM step_log WHERE date=:day")
    suspend fun getByDate(day: Long): StepEntity?
    @Query("SELECT * FROM step_log")
    suspend fun getAll(): List<StepEntity>
    @Query("SELECT * FROM step_log ORDER BY date DESC LIMIT 7")
    fun observeWeek(): Flow<List<StepEntity>>
}

@Dao
interface FocusSessionDao {
    @Insert suspend fun insert(e: FocusSessionEntity): Long
    @Query("SELECT * FROM focus_session ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>
    @Query("SELECT COALESCE(SUM(plannedMin),0) FROM focus_session WHERE type='专注' AND startTime BETWEEN :s AND :e")
    suspend fun focusMinutesBetween(s: Long, e: Long): Int
    @Query("SELECT COUNT(*) FROM focus_session WHERE type='专注' AND interrupted=1 AND startTime BETWEEN :s AND :e")
    suspend fun interruptCountBetween(s: Long, e: Long): Int
}

@Dao
interface HabitDao {
    @Insert suspend fun insertHabit(e: HabitEntity): Long
    @Update suspend fun updateHabit(e: HabitEntity)
    @Delete suspend fun deleteHabit(e: HabitEntity)
    @Query("SELECT * FROM habit WHERE archived=0 ORDER BY createdAt DESC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habit WHERE id=:id LIMIT 1")
    suspend fun getHabit(id: Long): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCheckIn(e: HabitCheckInEntity)
    @Delete suspend fun deleteCheckIn(e: HabitCheckInEntity)
    @Query("DELETE FROM habit_checkin WHERE habitId=:habitId")
    suspend fun deleteCheckInsByHabit(habitId: Long)
    @Query("SELECT * FROM habit_checkin WHERE habitId=:habitId ORDER BY date DESC")
    fun observeCheckIns(habitId: Long): Flow<List<HabitCheckInEntity>>
    @Query("SELECT * FROM habit_checkin")
    fun observeAllCheckIns(): Flow<List<HabitCheckInEntity>>
    @Query("SELECT date, COUNT(*) as cnt FROM habit_checkin GROUP BY date")
    fun observeHeatmap(): Flow<List<DateCount>>
    @Query("SELECT COUNT(DISTINCT date) FROM habit_checkin WHERE habitId=:habitId")
    suspend fun distinctDays(habitId: Long): Int
}

/** 热力图按日聚合结果：date 为 dayKey，cnt 为当日打卡总次数。 */
data class DateCount(
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "cnt") val cnt: Int
)
