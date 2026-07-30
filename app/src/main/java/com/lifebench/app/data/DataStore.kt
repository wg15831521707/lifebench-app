package com.lifebench.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局偏好设置（DataStore）。保存主题、字体、通知、音效、预算、计步基线、应用锁等。
 * 与 Room 分离：轻量键值用 DataStore，结构化数据用 Room。
 */
private val Context.dataStore by preferencesDataStore(name = "lifebench_settings")

class SettingsStore(private val context: Context) {

    private val ds = context.dataStore

    // —— 主题：SYSTEM / LIGHT / DARK ——
    val themeMode: Flow<String> = ds.data.map { it[KEY_THEME] ?: "SYSTEM" }
    suspend fun setThemeMode(v: String) = ds.edit { it[KEY_THEME] = v }

    // —— 主题色彩预设 id（teal/blue/green/orange/pink/purple）——
    val themePreset: Flow<String> = ds.data.map { it[KEY_PRESET] ?: "teal" }
    suspend fun setThemePreset(v: String) = ds.edit { it[KEY_PRESET] = v }

    // —— 字体缩放 0.85~1.3 ——
    val fontScale: Flow<Float> = ds.data.map { it[KEY_FONT] ?: 1.0f }
    suspend fun setFontScale(v: Float) = ds.edit { it[KEY_FONT] = v.coerceIn(0.85f, 1.3f) }

    // —— 通知开关 ——
    val notificationEnabled: Flow<Boolean> = ds.data.map { it[KEY_NOTIFY] ?: true }
    suspend fun setNotificationEnabled(v: Boolean) = ds.edit { it[KEY_NOTIFY] = v }

    // —— 提示音效开关 ——
    val soundEnabled: Flow<Boolean> = ds.data.map { it[KEY_SOUND] ?: true }
    suspend fun setSoundEnabled(v: Boolean) = ds.edit { it[KEY_SOUND] = v }

    // —— 番茄钟白噪音选择（无/雨声/森林/海浪/咖啡馆）——
    val whiteNoise: Flow<String> = ds.data.map { it[KEY_NOISE] ?: "无" }
    suspend fun setWhiteNoise(v: String) = ds.edit { it[KEY_NOISE] = v }

    // —— 月度消费预算（元）——
    val monthlyBudget: Flow<Double> = ds.data.map { it[KEY_BUDGET] ?: 2000.0 }
    suspend fun setMonthlyBudget(v: Double) = ds.edit { it[KEY_BUDGET] = v.coerceAtLeast(0.0) }

    // —— 记账自定义分类（以 | 分隔存储）——
    val customCategories: Flow<List<String>> = ds.data.map { (it[KEY_CUSTOM_CAT] ?: "").split("|").filter { s -> s.isNotBlank() } }
    suspend fun addCustomCategory(v: String) = ds.edit {
        val cur = (it[KEY_CUSTOM_CAT] ?: "").split("|").filter { s -> s.isNotBlank() }.toMutableList()
        if (v.isNotBlank() && v !in cur) cur.add(v)
        it[KEY_CUSTOM_CAT] = cur.joinToString("|")
    }
    suspend fun removeCustomCategory(v: String) = ds.edit {
        val cur = (it[KEY_CUSTOM_CAT] ?: "").split("|").filter { s -> s.isNotBlank() && s != v }
        it[KEY_CUSTOM_CAT] = cur.joinToString("|")
    }

    // —— 计步传感器基线（首次读取的累计值）——
    val stepBaseline: Flow<Long> = ds.data.map { it[KEY_STEP_BASE] ?: -1L }
    suspend fun setStepBaseline(v: Long) = ds.edit { it[KEY_STEP_BASE] = v }

    // —— 应用锁 ——
    val appLockEnabled: Flow<Boolean> = ds.data.map { it[KEY_LOCK] ?: false }
    suspend fun setAppLockEnabled(v: Boolean) = ds.edit { it[KEY_LOCK] = v }

    // 一键清空（谨慎使用，仅导入覆盖前调用）
    suspend fun clearAll() = ds.edit { it.clear() }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_FONT = floatPreferencesKey("font_scale")
        private val KEY_NOTIFY = booleanPreferencesKey("notification_enabled")
        private val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        private val KEY_NOISE = stringPreferencesKey("white_noise")
        private val KEY_BUDGET = doublePreferencesKey("monthly_budget")
        private val KEY_CUSTOM_CAT = stringPreferencesKey("custom_categories")
        private val KEY_PRESET = stringPreferencesKey("theme_preset")
        private val KEY_STEP_BASE = longPreferencesKey("step_baseline")
        private val KEY_LOCK = booleanPreferencesKey("app_lock")
    }
}
