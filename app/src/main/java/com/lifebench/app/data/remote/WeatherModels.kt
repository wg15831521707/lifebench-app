package com.lifebench.app.data.remote

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * 天气领域模型 + Open-Meteo 接口响应模型 + WMO/空气质量 映射工具。
 * 领域模型字段与 WeatherDemo 保持一致，便于 UI 直接复用。
 */

// ===== 对外领域模型 =====
data class WeatherNow(
    val city: String,
    val temp: Int,
    val condition: String,
    val feel: Int,
    val aqi: Int,        // -1 表示未知
    val aqiLevel: String,
    val uv: String,
    val humidity: Int,   // -1 表示未知
    val wind: String,
    val indexes: Map<String, String>
)
data class WeatherDay(val label: String, val condition: String, val high: Int, val low: Int)
data class WeatherHour(val label: String, val temp: Int, val pop: Int, val wind: String)
data class WeatherData(
    val now: WeatherNow,
    val days: List<WeatherDay>,
    val hours: List<WeatherHour>,
    val source: String   // 网络实时 / 离线缓存 / 演示数据
)
data class WeatherResult(val data: WeatherData, val source: String)

// ===== Open-Meteo 天气预报响应 =====
data class OmForecast(
    @SerializedName("current") val current: OmCurrent?,
    @SerializedName("daily") val daily: OmDaily?,
    @SerializedName("hourly") val hourly: OmHourly?
)
data class OmCurrent(
    @SerializedName("temperature_2m") val temp: Double?,
    @SerializedName("apparent_temperature") val feel: Double?,
    @SerializedName("relative_humidity_2m") val humidity: Double?,
    @SerializedName("weather_code") val code: Int?,
    @SerializedName("wind_speed_10m") val wind: Double?,
    @SerializedName("uv_index") val uv: Double?,
    @SerializedName("is_day") val isDay: Int?
)
data class OmDaily(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("weather_code") val code: List<Int>?,
    @SerializedName("temperature_2m_max") val max: List<Double>?,
    @SerializedName("temperature_2m_min") val min: List<Double>?
)
data class OmHourly(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("temperature_2m") val temp: List<Double>?,
    @SerializedName("precipitation_probability") val pop: List<Int>?,
    @SerializedName("wind_speed_10m") val wind: List<Double>?
)

// ===== Open-Meteo 空气质量响应 =====
data class OmAir(
    @SerializedName("current") val current: OmAirCurrent?
)
data class OmAirCurrent(
    @SerializedName("european_aqi") val aqi: Double?,
    @SerializedName("pm2_5") val pm25: Double?,
    @SerializedName("us_aqi") val usAqi: Double?
)

// ===== Open-Meteo 地理编码响应 =====
data class OmGeo(
    @SerializedName("results") val results: List<OmGeoResult>?
)
data class OmGeoResult(
    @SerializedName("name") val name: String?,
    @SerializedName("latitude") val lat: Double?,
    @SerializedName("longitude") val lon: Double?,
    @SerializedName("country") val country: String?,
    @SerializedName("admin1") val admin1: String?
)

// ===== 映射工具 =====

/** WMO 天气代码 → 中文天气状况 */
fun conditionOf(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "晴间多云"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    in 51..57 -> "毛毛雨"
    in 61..67 -> "雨"
    in 71..77 -> "雪"
    in 80..82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷阵雨"
    in 96..99 -> "雷阵雨伴冰雹"
    else -> "多云"
}

/** 欧洲 AQI 数值 → (展示值, 等级文字) */
fun aqiInfo(aqi: Int): Pair<Int, String> {
    if (aqi < 0) return -1 to "—"
    val level = when {
        aqi <= 20 -> "优"
        aqi <= 40 -> "良"
        aqi <= 60 -> "轻度污染"
        aqi <= 80 -> "中度污染"
        aqi <= 100 -> "重度污染"
        else -> "严重污染"
    }
    return aqi to level
}

private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
private val weekNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

/** 日期串 + 序号 → 「今天/明天/周X」 */
fun dayLabel(date: String?, i: Int): String {
    if (i == 0) return "今天"
    if (i == 1) return "明天"
    if (date == null) return "第${i + 1}天"
    return try {
        val cal = Calendar.getInstance().apply { time = sdf.parse(date)!! }
        weekNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
    } catch (e: Exception) { "第${i + 1}天" }
}
