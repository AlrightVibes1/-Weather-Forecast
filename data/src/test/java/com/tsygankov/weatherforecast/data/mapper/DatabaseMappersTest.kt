package com.tsygankov.weatherforecast.data.mapper

import com.tsygankov.weatherforecast.core.database.CityEntity
import com.tsygankov.weatherforecast.core.database.CurrentWeatherEntity
import com.tsygankov.weatherforecast.core.database.StoredForecast
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.core.model.UNKNOWN_WMO_CODE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseMappersTest {
    @Test
    fun `forecast round trip keeps every value and restores source order`() {
        val forecast = forecast("Europe/Moscow", 20.5)
        val write = forecast.toCacheWrite(cityId = 42L, updatedAt = 1_234L)
        val stored = StoredForecast(
            city = city(42L, "Old/Timezone"),
            currentWeather = write.currentWeather,
            hourlyWeather = write.hourlyWeather.reversed(),
            dailyWeather = write.dailyWeather.reversed(),
        )

        assertEquals(
            forecast.copy(timezone = "Old/Timezone"),
            stored.toDomainOrNull(),
        )
        assertEquals(1_234L, write.currentWeather.updatedAt)
        assertEquals(listOf(0, 1), write.hourlyWeather.map { it.position })
        assertEquals(listOf(0, 1), write.dailyWeather.map { it.position })
    }

    @Test
    fun `legacy partial current row is not exposed as complete forecast`() {
        val stored = StoredForecast(
            city = city(42L, "Europe/Moscow"),
            currentWeather = CurrentWeatherEntity(
                cityId = 42L,
                time = null,
                temperatureCelsius = 7.5,
                apparentTemperatureCelsius = null,
                relativeHumidity = null,
                pressure = null,
                windSpeed = null,
                weatherCode = 3,
                isDay = null,
                updatedAt = 999L,
            ),
            hourlyWeather = emptyList(),
            dailyWeather = emptyList(),
        )

        assertNull(stored.toDomainOrNull())
    }

    @Test
    fun `missing weather codes remain neutral through the non-null hourly and daily cache columns`() {
        val forecast = forecast("Europe/Moscow", 20.5).let { value ->
            value.copy(
                current = value.current.copy(weatherCode = null),
                hourly = value.hourly.map { it.copy(weatherCode = null) },
                daily = value.daily.map { it.copy(weatherCode = null) },
            )
        }

        val write = forecast.toCacheWrite(cityId = 42L, updatedAt = 1_234L)
        val stored = StoredForecast(
            city = city(42L, "Europe/Moscow"),
            currentWeather = write.currentWeather,
            hourlyWeather = write.hourlyWeather,
            dailyWeather = write.dailyWeather,
        ).toDomainOrNull()

        assertNull(stored?.current?.weatherCode)
        assertEquals(listOf(UNKNOWN_WMO_CODE, UNKNOWN_WMO_CODE), stored?.hourly?.map { it.weatherCode })
        assertEquals(listOf(UNKNOWN_WMO_CODE, UNKNOWN_WMO_CODE), stored?.daily?.map { it.weatherCode })
    }

    private fun city(id: Long, timezone: String) = CityEntity(
        id = id,
        name = "Москва",
        country = "Россия",
        adminArea = "Москва",
        latitude = 55.75,
        longitude = 37.61,
        timezone = timezone,
        addedAt = 100L,
    )

    private fun forecast(timezone: String, temperature: Double) = Forecast(
        timezone = timezone,
        current = CurrentWeather(
            time = "2026-09-01T12:00",
            temperatureCelsius = temperature,
            apparentTemperatureCelsius = temperature - 1.0,
            relativeHumidity = 60,
            pressure = 1_005.0,
            windSpeed = 8.0,
            weatherCode = 2,
            isDay = true,
        ),
        hourly = listOf(
            HourlyWeather("2026-09-01T12:00", temperature, temperature - 1.0, 60, 10, 2, 8.0),
            HourlyWeather("2026-09-01T13:00", temperature + 1.0, temperature, 58, 20, 3, 9.0),
        ),
        daily = listOf(
            DailyWeather("2026-09-01", temperature + 2.0, 10.0, 2, 20, "05:30", "19:20"),
            DailyWeather("2026-09-02", temperature + 1.0, 9.0, 61, 70, "05:32", "19:17"),
        ),
    )
}
