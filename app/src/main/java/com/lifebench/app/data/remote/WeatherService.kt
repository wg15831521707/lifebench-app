package com.lifebench.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Open-Meteo 三个「无需 Key」的公开接口：天气预报 / 空气质量 / 地理编码。
 * 文档：https://open-meteo.com/en/docs
 */
interface ForecastApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,uv_index,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,wind_speed_10m",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 7,
        @Query("wind_speed_unit") windUnit: String = "ms"
    ): OmForecast
}

interface AirApi {
    @GET("v1/air-quality")
    suspend fun air(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "european_aqi,pm2_5,us_aqi"
    ): OmAir
}

interface GeoApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "zh",
        @Query("format") format: String = "json"
    ): OmGeo
}

object WeatherService {
    private fun build(base: String) = Retrofit.Builder()
        .baseUrl(base)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        )
        .build()

    val forecastApi = build("https://api.open-meteo.com/").create(ForecastApi::class.java)
    val airApi = build("https://air-quality-api.open-meteo.com/").create(AirApi::class.java)
    val geoApi = build("https://geocoding-api.open-meteo.com/").create(GeoApi::class.java)
}
