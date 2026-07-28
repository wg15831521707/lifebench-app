package com.lifebench.app.data

/**
 * 天气演示数据（离线可用）。工程默认走本地演示，保证断网全部功能可用；
 * 真实数据由 data.remote.WeatherRepository 通过 Open-Meteo 获取（无需 Key）。
 */
object WeatherDemo {
    data class Now(
        val city: String, val temp: Int, val condition: String,
        val feel: Int, val aqi: Int, val uv: String,
        val indexes: Map<String, String>
    )
    data class Day(val label: String, val condition: String, val high: Int, val low: Int)
    data class Hour(val label: String, val temp: Int, val pop: Int, val wind: String)

    fun current() = Now(
        city = "上海市", temp = 24, condition = "多云",
        feel = 26, aqi = 48, uv = "中等",
        indexes = mapOf("穿衣" to "薄外套", "洗车" to "适宜", "出行" to "良好", "运动" to "适宜")
    )

    fun days() = listOf(
        Day("今天", "多云", 26, 18), Day("周二", "晴", 28, 19), Day("周三", "小雨", 23, 17),
        Day("周四", "阴", 25, 18), Day("周五", "晴", 27, 20), Day("周六", "多云", 26, 19),
        Day("周日", "雷阵雨", 22, 16)
    )

    fun hours() = (0..11).map {
        val h = (8 + it) % 24
        Hour("${h}时", 20 + it % 6, (it * 13) % 60, "${1 + it % 3}级")
    }

    /** 条件 → emoji 图标 */
    fun iconOf(condition: String): String = when {
        condition.contains("晴") -> "☀️"
        condition.contains("多云") -> "⛅"
        condition.contains("阴") -> "☁️"
        condition.contains("雨") -> "🌧️"
        condition.contains("雷") -> "⛈️"
        condition.contains("雪") -> "❄️"
        else -> "🌫️"
    }

    /** 演示数据 → 领域模型（离线/网络失败回退）。 */
    fun toDomain(city: String): com.lifebench.app.data.remote.WeatherData {
        val n = current().copy(city = city)
        val (aqi, aqiLevel) = com.lifebench.app.data.remote.aqiInfo(n.aqi)
        return com.lifebench.app.data.remote.WeatherData(
            now = com.lifebench.app.data.remote.WeatherNow(
                n.city, n.temp, n.condition, n.feel, n.aqi, aqiLevel, n.uv, 60, "2 m/s", n.indexes
            ),
            days = days().map { com.lifebench.app.data.remote.WeatherDay(it.label, it.condition, it.high, it.low) },
            hours = hours().map { com.lifebench.app.data.remote.WeatherHour(it.label, it.temp, it.pop, it.wind) },
            source = "演示数据"
        )
    }
}
