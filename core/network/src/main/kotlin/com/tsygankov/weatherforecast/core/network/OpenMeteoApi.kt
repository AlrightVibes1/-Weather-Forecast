package com.tsygankov.weatherforecast.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = OpenMeteoContract.GEOCODING_RESULT_LIMIT,
        @Query("language") language: String = OpenMeteoContract.GEOCODING_LANGUAGE,
        @Query("format") format: String = OpenMeteoContract.GEOCODING_FORMAT,
    ): GeocodingResponseDto
}

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = OpenMeteoContract.CURRENT_FIELDS,
        @Query("hourly") hourly: String = OpenMeteoContract.HOURLY_FIELDS,
        @Query("daily") daily: String = OpenMeteoContract.DAILY_FIELDS,
        @Query("forecast_days") forecastDays: Int = OpenMeteoContract.FORECAST_DAYS,
        @Query("timezone") timezone: String = OpenMeteoContract.TIMEZONE,
        @Query("temperature_unit") temperatureUnit: String = OpenMeteoContract.TEMPERATURE_UNIT,
    ): ForecastResponseDto
}

object OpenMeteoContract {
    const val GEOCODING_RESULT_LIMIT = 10
    const val GEOCODING_LANGUAGE = "ru"
    const val GEOCODING_FORMAT = "json"
    const val FORECAST_DAYS = 16
    const val TIMEZONE = "auto"
    const val TEMPERATURE_UNIT = "celsius"
    const val CURRENT_FIELDS =
        "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,surface_pressure,is_day,wind_speed_10m"
    const val HOURLY_FIELDS =
        "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m"
    const val DAILY_FIELDS =
        "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,sunrise,sunset"
}

data class GeocodingResponseDto(val results: List<CityDto?>? = null)

data class CityDto(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val timezone: String? = null,
    @SerializedName("admin1") val adminArea: String? = null,
)

data class ForecastResponseDto(
    val timezone: String? = null,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
)

data class CurrentDto(
    val time: String? = null,
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerializedName("relative_humidity_2m") val relativeHumidity: Int? = null,
    @SerializedName("surface_pressure") val pressure: Double? = null,
    @SerializedName("wind_speed_10m") val windSpeed: Double? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("is_day") val isDay: Int? = null,
)

data class HourlyDto(
    val time: List<String?>? = null,
    @SerializedName("temperature_2m") val temperatures: List<Double?>? = null,
    @SerializedName("apparent_temperature") val apparentTemperatures: List<Double?>? = null,
    @SerializedName("relative_humidity_2m") val humidities: List<Int?>? = null,
    @SerializedName("precipitation_probability") val precipitationProbabilities: List<Int?>? = null,
    @SerializedName("weather_code") val weatherCodes: List<Int?>? = null,
    @SerializedName("wind_speed_10m") val windSpeeds: List<Double?>? = null,
)

data class DailyDto(
    val time: List<String?>? = null,
    @SerializedName("temperature_2m_max") val maxTemperatures: List<Double?>? = null,
    @SerializedName("temperature_2m_min") val minTemperatures: List<Double?>? = null,
    @SerializedName("weather_code") val weatherCodes: List<Int?>? = null,
    @SerializedName("precipitation_probability_max") val precipitationProbabilities: List<Int?>? = null,
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
)
