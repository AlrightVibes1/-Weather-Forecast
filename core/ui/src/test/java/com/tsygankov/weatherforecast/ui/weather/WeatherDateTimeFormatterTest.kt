package com.tsygankov.weatherforecast.ui.weather

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherDateTimeFormatterTest {
    @Test
    fun `city date and time stay russian and use the city timezone regardless of device settings`() {
        val previousLocale = Locale.getDefault()
        val previousTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        try {
            val formatter = WeatherDateTimeFormatter.forTimeZone(primary = "Europe/Moscow")
            val timestamp = cityTimeMillis(
                value = "2026-09-09T13:05",
                timezoneId = "Europe/Moscow",
            )

            assertEquals("ср, 13:05", formatter.formatForecastHour(timestamp))
            assertEquals("13:05", formatter.formatCompactForecastHour(timestamp))
            assertEquals("ср, 9 сентября, 13:05", formatter.formatCurrentMoment(timestamp))
            assertEquals("среда", formatter.formatDayOfWeek(timestamp))
            assertEquals("9 сентября", formatter.formatForecastDate(timestamp))
            assertEquals("9 сентября 2026, 13:05", formatter.formatLastUpdated(timestamp))
            assertEquals(
                cityTimeMillis("2026-09-09T00:00", "Europe/Moscow"),
                formatter.startOfDay(timestamp),
            )
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun `invalid response timezone uses the saved city timezone and strict parsing`() {
        val formatter = WeatherDateTimeFormatter.forTimeZone(
            primary = "Not/AZone",
            fallback = "Asia/Tokyo",
        )

        val parsed = formatter.parseForecastHour("2026-09-09T08:30")

        assertEquals(
            cityTimeMillis("2026-09-09T08:30", "Asia/Tokyo"),
            parsed,
        )
        assertNull(formatter.parseForecastHour("2026-09-09T08:30extra"))
        assertNull(formatter.parseForecastDay("2026-09-09extra"))
    }

    @Test
    fun `decimal weather values always use the russian comma`() {
        assertEquals("1012,5", 1_012.5.formatRussianDecimal())
        assertEquals("7", 7.0.formatRussianDecimal())
    }

    private fun cityTimeMillis(value: String, timezoneId: String): Long =
        checkNotNull(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone(timezoneId)
            }.parse(value),
        ).time
}
