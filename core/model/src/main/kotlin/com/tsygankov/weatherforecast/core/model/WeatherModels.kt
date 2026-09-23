package com.tsygankov.weatherforecast.core.model

import kotlin.math.ceil
import kotlin.math.floor

data class City(
    val id: Long,
    val name: String,
    val country: String,
    val adminArea: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val currentTemperatureCelsius: Double? = null,
    val currentWeatherCode: Int? = null,
    val updatedAt: Long? = null,
)

data class CitySearchResult(
    val id: Long,
    val name: String,
    val country: String?,
    val adminArea: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
)

data class CurrentWeather(
    val time: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val relativeHumidity: Int,
    val pressure: Double,
    val windSpeed: Double,
    val weatherCode: Int?,
    val isDay: Boolean,
)

data class HourlyWeather(
    val time: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val humidity: Int,
    val precipitationProbability: Int,
    val weatherCode: Int?,
    val windSpeed: Double,
)

data class DailyWeather(
    val date: String,
    val maxTemperatureCelsius: Double,
    val minTemperatureCelsius: Double,
    val weatherCode: Int?,
    val precipitationProbability: Int,
    val sunrise: String,
    val sunset: String,
)

data class Forecast(
    val timezone: String,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
)

enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM, UNKNOWN;

    companion object {
        fun fromWmo(code: Int?): WeatherCondition = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

/** Sentinel used only by non-null persistence columns for an absent or unknown WMO code. */
const val UNKNOWN_WMO_CODE = -1

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    /** Converts a canonical Celsius value without rounding it. */
    fun convertFromCelsius(temperatureCelsius: Double): Double = when (this) {
        CELSIUS -> temperatureCelsius
        FAHRENHEIT -> temperatureCelsius * 9.0 / 5.0 + 32.0
    }
}

enum class ThemeMode { LIGHT, DARK }

data class UserSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val automaticLocationWeather: Boolean = true,
)

fun Double.inUnit(unit: TemperatureUnit): Double =
    unit.convertFromCelsius(this)

/**
 * Converts a canonical Celsius value and rounds it for presentation.
 * Half values are rounded away from zero, so 1.5 becomes 2 and -1.5 becomes -2.
 */
fun Double.roundedInUnit(unit: TemperatureUnit): Int =
    inUnit(unit).roundHalfAwayFromZero()

private fun Double.roundHalfAwayFromZero(): Int = when {
    this >= 0.0 -> floor(this + 0.5).toInt()
    else -> ceil(this - 0.5).toInt()
}
