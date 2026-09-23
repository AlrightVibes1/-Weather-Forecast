package com.tsygankov.weatherforecast.data.mapper

import com.google.gson.Gson
import com.tsygankov.weatherforecast.core.network.ForecastResponseDto
import com.tsygankov.weatherforecast.core.network.GeocodingResponseDto
import com.tsygankov.weatherforecast.data.DataException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoMappersTest {
    private val gson = Gson()

    @Test
    fun `full forecast JSON maps current hourly daily and response timezone`() {
        val forecast = forecastFixture("full_forecast.json").toDomain()

        assertEquals("Europe/Moscow", forecast.timezone)
        assertEquals("2026-09-01T12:00", forecast.current.time)
        assertEquals(18.4, forecast.current.temperatureCelsius, 0.0)
        assertEquals(2, forecast.current.weatherCode)
        assertTrue(forecast.current.isDay)
        assertEquals(2, forecast.hourly.size)
        assertEquals("2026-09-01T13:00", forecast.hourly[1].time)
        assertEquals(19.1, forecast.hourly[1].temperatureCelsius, 0.0)
        assertEquals(20, forecast.hourly[1].precipitationProbability)
        assertEquals(2, forecast.daily.size)
        assertEquals("2026-09-02", forecast.daily[1].date)
        assertEquals(17.6, forecast.daily[1].maxTemperatureCelsius, 0.0)
        assertEquals("2026-09-02T19:17", forecast.daily[1].sunset)
    }

    @Test
    fun `different array lengths and null holes keep dates paired with same-index values`() {
        val forecast = forecastFixture("mismatched_forecast.json").toDomain()

        assertEquals("Asia/Yekaterinburg", forecast.timezone)
        assertEquals(
            listOf("2026-09-01T14:00", "2026-09-01T16:00"),
            forecast.hourly.map { it.time },
        )
        assertEquals(listOf(10.0, 30.0), forecast.hourly.map { it.temperatureCelsius })
        assertEquals(listOf(0, 2), forecast.hourly.map { it.weatherCode })
        assertEquals(
            listOf("2026-09-01", "2026-09-02"),
            forecast.daily.map { it.date },
        )
        assertEquals(listOf(5.0, 6.0), forecast.daily.map { it.maxTemperatureCelsius })
        assertEquals(listOf(1.0, 2.0), forecast.daily.map { it.minTemperatureCelsius })
    }

    @Test
    fun `missing weather codes keep otherwise valid rows as neutral unknown weather`() {
        val forecast = forecastFixture("missing_weather_codes.json").toDomain()

        assertNull(forecast.current.weatherCode)
        assertEquals(listOf(null, 61), forecast.hourly.map { it.weatherCode })
        assertEquals(listOf(null), forecast.daily.map { it.weatherCode })
    }

    @Test
    fun `empty forecast JSON becomes typed empty-data error`() {
        val error = assertThrows(DataException.EmptyData::class.java) {
            forecastFixture("empty_forecast.json").toDomain()
        }

        assertEquals("timezone", error.missingPart)
    }

    @Test
    fun `empty geocoding JSON maps to empty list`() {
        assertTrue(geocodingFixture("empty_geocoding.json").toDomain().isEmpty())
    }

    @Test
    fun `missing optional labels do not crash and unusable records are skipped`() {
        val cities = geocodingFixture("partial_geocoding.json").toDomain()

        assertEquals(2, cities.size)
        assertEquals("Москва", cities[0].name)
        assertEquals("Россия", cities[0].country)
        assertEquals("Москва", cities[0].adminArea)
        assertEquals("Europe/Moscow", cities[0].timezone)
        assertEquals("Рейкьявик", cities[1].name)
        assertNull(cities[1].country)
        assertNull(cities[1].adminArea)
        assertEquals("Atlantic/Reykjavik", cities[1].timezone)
        assertTrue(cities.none { it.name == "Без часового пояса" })
    }

    private fun forecastFixture(name: String): ForecastResponseDto =
        gson.fromJson(resourceText(name), ForecastResponseDto::class.java)

    private fun geocodingFixture(name: String): GeocodingResponseDto =
        gson.fromJson(resourceText(name), GeocodingResponseDto::class.java)

    private fun resourceText(name: String): String =
        checkNotNull(javaClass.getResource("/openmeteo/$name")) { "Fixture $name not found" }.readText()
}
