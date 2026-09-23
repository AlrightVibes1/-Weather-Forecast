package com.tsygankov.weatherforecast.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoContractTest {
    @Test
    fun `geocoding defaults match V1 contract`() {
        assertEquals(10, OpenMeteoContract.GEOCODING_RESULT_LIMIT)
        assertEquals("ru", OpenMeteoContract.GEOCODING_LANGUAGE)
        assertEquals("json", OpenMeteoContract.GEOCODING_FORMAT)
    }

    @Test
    fun `forecast defaults request canonical Celsius and sixteen local days`() {
        assertEquals(16, OpenMeteoContract.FORECAST_DAYS)
        assertEquals("auto", OpenMeteoContract.TIMEZONE)
        assertEquals("celsius", OpenMeteoContract.TEMPERATURE_UNIT)
        assertEquals(
            setOf(
                "temperature_2m",
                "apparent_temperature",
                "relative_humidity_2m",
                "weather_code",
                "surface_pressure",
                "is_day",
                "wind_speed_10m",
            ),
            OpenMeteoContract.CURRENT_FIELDS.split(',').toSet(),
        )
        assertEquals(
            setOf(
                "temperature_2m",
                "apparent_temperature",
                "relative_humidity_2m",
                "precipitation_probability",
                "weather_code",
                "wind_speed_10m",
            ),
            OpenMeteoContract.HOURLY_FIELDS.split(',').toSet(),
        )
        assertEquals(
            setOf(
                "temperature_2m_max",
                "temperature_2m_min",
                "weather_code",
                "precipitation_probability_max",
                "sunrise",
                "sunset",
            ),
            OpenMeteoContract.DAILY_FIELDS.split(',').toSet(),
        )
    }
}
