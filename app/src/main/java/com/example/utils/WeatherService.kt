package com.example.utils

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current_weather: CurrentWeather? = null
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    val temperature: Double = 0.0,
    val windspeed: Double = 0.0,
    val weathercode: Int = 0
)

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): Response<OpenMeteoResponse>
}

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApiService: WeatherApiService
) {
    suspend fun fetchCurrentWeather(lat: Double = 30.1575, lon: Double = 71.5249): String {
        return try {
            val res = weatherApiService.getCurrentWeather(lat, lon)
            val cw = res.body()?.current_weather
            if (res.isSuccessful && cw != null) {
                val cond = when (cw.weathercode) {
                    0 -> "Clear sky"
                    in 1..3 -> "Partly cloudy"
                    in 45..48 -> "Foggy"
                    in 51..67 -> "Rainy"
                    in 80..99 -> "Thunderstorms"
                    else -> "Fair"
                }
                "${cw.temperature}°C, $cond, Wind ${cw.windspeed} km/h"
            } else {
                "Weather data unavailable"
            }
        } catch (_: Exception) {
            "Weather unavailable (offline)"
        }
    }
}
