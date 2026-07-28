package com.lifebench.app.data.remote

import android.content.Context
import com.google.gson.Gson
import com.lifebench.app.data.WeatherDemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 天气数据仓库：优先联网（Open-Meteo），失败回退本地缓存文件，再失败回退演示数据。
 * 每次成功获取都会把结果写入 filesDir/weather_cache.json，保证断网也能展示最近一次结果。
 */
object WeatherRepository {
    private val gson = Gson()
    private var memoryCache: WeatherData? = null

    private fun cacheFile(context: Context) = File(context.filesDir, "weather_cache.json")

    /** 读取本地离线缓存（断网可用） */
    suspend fun loadCached(context: Context): WeatherData? = withContext(Dispatchers.IO) {
        memoryCache?.let { return@withContext it }
        val f = cacheFile(context)
        if (f.exists()) runCatching { gson.fromJson(f.readText(), WeatherData::class.java) }.getOrNull()
        else null
    }

    private suspend fun saveCache(context: Context, data: WeatherData) = withContext(Dispatchers.IO) {
        runCatching { cacheFile(context).writeText(gson.toJson(data)) }
        memoryCache = data
    }

    /**
     * 加载天气。city 与 (lat,lon) 二选一；传 city 时会先做地理编码解析经纬度。
     */
    suspend fun load(context: Context, city: String? = null, lat: Double? = null, lon: Double? = null): WeatherResult {
        return try {
            val (la, lo, cityName) = if (lat != null && lon != null) {
                Triple(lat, lon, city ?: "当前位置")
            } else {
                val name = city?.takeIf { it.isNotBlank() } ?: "北京"
                val geo = WeatherService.geoApi.search(name)
                val r = geo.results?.firstOrNull()
                if (r?.lat != null && r.lon != null) {
                    Triple(r.lat, r.lon, r.name ?: name)
                } else {
                    return demoResult("未找到「$name」，已显示演示数据")
                }
            }
            val forecast = WeatherService.forecastApi.forecast(la, lo)
            val air = runCatching { WeatherService.airApi.air(la, lo) }.getOrNull()
            val data = mapToDomain(forecast, air, cityName)
            saveCache(context, data)
            WeatherResult(data, data.source)
        } catch (e: Exception) {
            val cached = loadCached(context)
            if (cached != null) WeatherResult(cached, "离线缓存（网络不可用）")
            else demoResult("演示数据（网络不可用）")
        }
    }

    private fun demoResult(msg: String): WeatherResult {
        val d = WeatherDemo.toDomain("本地")
        return WeatherResult(d.copy(source = msg), msg)
    }

    private fun mapToDomain(f: OmForecast, air: OmAir?, cityName: String): WeatherData {
        val c = f.current
        val temp = c?.temp?.toInt() ?: 0
        val feel = c?.feel?.toInt() ?: temp
        val code = c?.code ?: 0
        val condition = conditionOf(code)
        val aqiVal = air?.current?.aqi?.toInt() ?: -1
        val (aqi, aqiLevel) = aqiInfo(aqiVal)
        val uv = when ((c?.uv ?: 0.0).toInt()) {
            0, 1 -> "低"
            in 2..4 -> "中等"
            in 5..6 -> "高"
            in 7..9 -> "很高"
            else -> "极高"
        }
        val humidity = c?.humidity?.toInt() ?: -1
        val wind = "${c?.wind?.toInt() ?: 0} m/s"
        val indexes = buildIndexes(temp, condition)
        val now = WeatherNow(cityName, temp, condition, feel, aqi, aqiLevel, uv, humidity, wind, indexes)

        val days = mutableListOf<WeatherDay>()
        val d = f.daily
        val dn = minOf(d?.time?.size ?: 0, 7)
        for (i in 0 until dn) {
            days.add(
                WeatherDay(
                    dayLabel(d?.time?.get(i), i),
                    conditionOf(d?.code?.get(i) ?: 0),
                    d?.max?.get(i)?.toInt() ?: 0,
                    d?.min?.get(i)?.toInt() ?: 0
                )
            )
        }

        val hours = mutableListOf<WeatherHour>()
        val h = f.hourly
        val hn = minOf(h?.time?.size ?: 0, 24)
        for (i in 0 until hn) {
            val t = h?.time?.get(i) ?: ""
            val hh = if (t.length >= 13) t.substring(11, 13) else "?"
            val w = h?.wind?.get(i) ?: 0.0
            hours.add(WeatherHour("${hh}时", h?.temp?.get(i)?.toInt() ?: 0, h?.pop?.get(i) ?: 0, "${w.toInt()} m/s"))
        }
        return WeatherData(now, days, hours, source = "网络实时")
    }

    private fun buildIndexes(temp: Int, condition: String): Map<String, String> {
        val cloth = when {
            temp >= 28 -> "短袖"
            temp >= 20 -> "薄衫"
            temp >= 12 -> "长袖"
            temp >= 5 -> "厚外套"
            else -> "羽绒服"
        }
        val rainy = condition.contains("雨") || condition.contains("雪") || condition.contains("雷")
        return mapOf(
            "穿衣" to cloth,
            "出行" to if (rainy) "带伞" else "适宜",
            "洗车" to if (rainy) "不宜" else "适宜",
            "运动" to if (rainy) "室内为宜" else "适宜"
        )
    }
}
