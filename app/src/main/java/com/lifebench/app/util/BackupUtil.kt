package com.lifebench.app.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Type

/**
 * 数据备份与恢复：导出全部表为单个 JSON 文件；导入采用"先清后插"的事务策略，
 * 异常时回滚（不执行插入），保证不会写入半坏数据。文件保存在 app 内部 filesDir，隐私不外露。
 *
 * 注意：各 DAO 仅提供单条 insert（insert(e)），因此导入时逐条插入；
 * 整体包裹在 Room 的 withTransaction 中，保证原子性。
 */
object BackupUtil {
    private val gson = Gson()

    private fun listType(of: Type): Type = TypeToken.getParameterized(List::class.java, of).type

    /** 汇总全部 14 张表为一个 JSON 对象（导出/导入共用）。 */
    private suspend fun buildBackupJson(): JSONObject {
        val db = Repo.db
        return JSONObject().apply {
            put("todo", gson.toJson(db.todoDao().observeActive().first() + db.todoDao().observeArchived().first()))
            put("account", gson.toJson(db.accountDao().observeAll().first()))
            put("sleep", gson.toJson(db.sleepDao().getAll()))
            put("recipe", gson.toJson(db.recipeDao().observeAll().first()))
            put("diet", gson.toJson(db.dietLogDao().getAll()))
            put("fitness_plan", gson.toJson(db.fitnessPlanDao().observeAll().first()))
            put("fitness_profile", gson.toJson(listOfNotNull(db.fitnessProfileDao().get())))
            put("schulte", gson.toJson(db.schulteResultDao().observeAll().first()))
            put("training", gson.toJson(db.trainingResultDao().observeAll().first()))
            put("password", gson.toJson(db.passwordDao().observeAll().first()))
            put("note", gson.toJson(db.noteDao().observeAll().first()))
            put("anniversary", gson.toJson(db.anniversaryDao().observeAll().first()))
            put("step", gson.toJson(db.stepDao().getAll()))
            put("focus", gson.toJson(db.focusSessionDao().observeAll().first()))
        }
    }

    /** 导出到用户通过系统选择器指定的 Uri（SAF），返回是否成功。 */
    suspend fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = buildBackupJson().toString(2)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 导出：把 14 张表序列化为一个 JSON 文件，返回文件绝对路径（保留以兼容旧调用）。 */
    suspend fun exportAll(context: Context): String {
        val root = buildBackupJson()
        val file = File(context.filesDir, "lifebench_backup_${System.currentTimeMillis()}.json")
        file.writeText(root.toString(2))
        return file.absolutePath
    }

    /** 导入：清空全部表后在事务内逐条插入；成功返回 true，异常返回 false。 */
    private suspend fun applyImport(root: JSONObject): Boolean {
        return try {
            val db = Repo.db
            fun str(k: String) = root.optString(k, "[]")
            db.withTransaction {
                // 1) 先清空所有表（同一事务，保证原子）
                val wd = db.openHelper.writableDatabase
                listOf(
                    "todo", "account", "sleep", "recipe", "diet_log", "fitness_plan",
                    "fitness_profile", "schulte_result", "training_result", "password_item",
                    "note", "anniversary", "step_log", "focus_session"
                ).forEach { wd.execSQL("DELETE FROM $it") }

                // 2) 逐条插入（各 DAO 提供单条 insert）
                gson.fromJson<List<TodoEntity>>(str("todo"), listType(TodoEntity::class.java))
                    .forEach { db.todoDao().insert(it) }
                gson.fromJson<List<AccountEntity>>(str("account"), listType(AccountEntity::class.java))
                    .forEach { db.accountDao().insert(it) }
                gson.fromJson<List<SleepEntity>>(str("sleep"), listType(SleepEntity::class.java))
                    .forEach { db.sleepDao().insert(it) }
                gson.fromJson<List<RecipeEntity>>(str("recipe"), listType(RecipeEntity::class.java))
                    .forEach { db.recipeDao().insert(it) }
                gson.fromJson<List<DietLogEntity>>(str("diet"), listType(DietLogEntity::class.java))
                    .forEach { db.dietLogDao().insert(it) }
                gson.fromJson<List<FitnessPlanEntity>>(str("fitness_plan"), listType(FitnessPlanEntity::class.java))
                    .forEach { db.fitnessPlanDao().insert(it) }
                gson.fromJson<List<FitnessProfileEntity>>(str("fitness_profile"), listType(FitnessProfileEntity::class.java))
                    .forEach { db.fitnessProfileDao().upsert(it) }
                gson.fromJson<List<SchulteResultEntity>>(str("schulte"), listType(SchulteResultEntity::class.java))
                    .forEach { db.schulteResultDao().insert(it) }
                gson.fromJson<List<TrainingResultEntity>>(str("training"), listType(TrainingResultEntity::class.java))
                    .forEach { db.trainingResultDao().insert(it) }
                gson.fromJson<List<PasswordEntity>>(str("password"), listType(PasswordEntity::class.java))
                    .forEach { db.passwordDao().insert(it) }
                gson.fromJson<List<NoteEntity>>(str("note"), listType(NoteEntity::class.java))
                    .forEach { db.noteDao().insert(it) }
                gson.fromJson<List<AnniversaryEntity>>(str("anniversary"), listType(AnniversaryEntity::class.java))
                    .forEach { db.anniversaryDao().insert(it) }
                gson.fromJson<List<StepEntity>>(str("step"), listType(StepEntity::class.java))
                    .forEach { db.stepDao().upsert(it) }
                gson.fromJson<List<FocusSessionEntity>>(str("focus"), listType(FocusSessionEntity::class.java))
                    .forEach { db.focusSessionDao().insert(it) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 从用户通过系统选择器指定的 Uri 导入（SAF），返回是否成功。 */
    suspend fun importFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return false
            applyImport(JSONObject(text))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 导入：从本机私有目录的文件恢复（保留以兼容旧调用）。 */
    suspend fun importAll(context: Context, file: File): Boolean {
        return try {
            applyImport(JSONObject(file.readText()))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
