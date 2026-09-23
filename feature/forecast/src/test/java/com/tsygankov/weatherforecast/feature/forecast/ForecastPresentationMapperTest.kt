package com.tsygankov.weatherforecast.feature.forecast

import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastPresentationMapperTest {
    @Test
    fun `hourly list starts at the next city hour uses city timezone and stops at 24 items`() {
        val previousDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        try {
            val timezoneId = "Asia/Tokyo"
            val forecast = forecast(
                timezone = timezoneId,
                hourly = hourlySequence(timezoneId, count = 40),
            )

            val content = forecast.toForecastContentUiModel(
                city = TOKYO,
                temperatureUnit = TemperatureUnit.CELSIUS,
                nowMillis = cityTimeMillis("2026-09-09T10:30", timezoneId),
            )

            assertEquals(FORECAST_HOURLY_LIMIT, content.hourly.size)
            assertEquals("ср, 9 сентября, 12:30", content.current.localTimeLabel)
            assertEquals("2026-09-09T11:00", content.hourly.first().sourceTime)
            assertTrue(content.hourly.first().timeLabel.endsWith("11:00"))
            assertEquals("2026-09-10T10:00", content.hourly.last().sourceTime)
        } finally {
            TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun `invalid response timezone safely falls back to the saved city timezone`() {
        val forecast = forecast(
            timezone = "Not/AZone",
            hourly = listOf(
                hour("2026-09-09T12:00", 12.0),
                hour("2026-09-09T13:00", 13.0),
            ),
        )

        val content = forecast.toForecastContentUiModel(
            city = MOSCOW,
            temperatureUnit = TemperatureUnit.CELSIUS,
            nowMillis = cityTimeMillis("2026-09-09T12:30", MOSCOW.timezone),
        )

        assertEquals(listOf("2026-09-09T13:00"), content.hourly.map { it.sourceTime })
        assertTrue(content.hourly.single().timeLabel.endsWith("13:00"))
    }

    @Test
    fun `nearest current hour remains first when it is closer than the next hour`() {
        val forecast = forecast(
            timezone = MOSCOW.timezone,
            hourly = listOf(
                hour("2026-09-09T11:00", 11.0),
                hour("2026-09-09T12:00", 12.0),
                hour("2026-09-09T13:00", 13.0),
            ),
        )

        val content = forecast.toForecastContentUiModel(
            city = MOSCOW,
            temperatureUnit = TemperatureUnit.CELSIUS,
            nowMillis = cityTimeMillis("2026-09-09T12:05", MOSCOW.timezone),
        )

        assertEquals(
            listOf("2026-09-09T12:00", "2026-09-09T13:00"),
            content.hourly.map { it.sourceTime },
        )
    }

    @Test
    fun `mapper skips invalid hours converts temperatures and keeps unknown weather neutral`() {
        val forecast = forecast(
            timezone = MOSCOW.timezone,
            currentTemperature = 0.0,
            currentWeatherCode = 999,
            hourly = listOf(
                hour("broken-time", -100.0),
                hour(
                    time = "2026-09-09T13:00",
                    temperature = 100.0,
                    weatherCode = 1,
                    precipitation = 140,
                ),
            ),
        )

        val content = forecast.toForecastContentUiModel(
            city = MOSCOW,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            nowMillis = cityTimeMillis("2026-09-09T12:30", MOSCOW.timezone),
        )

        assertEquals(32, content.current.temperature)
        assertEquals(WeatherCondition.UNKNOWN, content.current.condition)
        assertEquals(1, content.hourly.size)
        assertEquals(212, content.hourly.single().temperature)
        assertEquals("13:00", content.hourly.single().compactTimeLabel)
        assertEquals(100, content.hourly.single().precipitationProbability)
        assertEquals(WeatherCondition.CLEAR, content.hourly.single().condition)
    }

    @Test
    fun `sixteen response days become exactly the first seven days starting with today`() {
        val previousDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        try {
            val timezoneId = TOKYO.timezone
            val forecast = forecast(
                timezone = timezoneId,
                hourly = emptyList(),
                daily = dailySequence(
                    timezoneId = timezoneId,
                    startDate = "2026-09-09",
                    count = 16,
                ),
            )

            val content = forecast.toForecastContentUiModel(
                city = TOKYO,
                temperatureUnit = TemperatureUnit.CELSIUS,
                nowMillis = cityTimeMillis("2026-09-09T00:30", timezoneId),
            )

            assertEquals(FORECAST_WEEK_LIMIT, content.weekDays.size)
            assertEquals("2026-09-09", content.weekDays.first().sourceDate)
            assertEquals("среда", content.weekDays.first().dayOfWeekLabel)
            assertTrue(content.weekDays.first().isToday)
            assertTrue(content.weekDays.drop(1).none(DailyForecastUiModel::isToday))
            assertEquals("9 сентября", content.weekDays.first().dateLabel)
            assertEquals("2026-09-15", content.weekDays.last().sourceDate)
            assertEquals(FORECAST_MONTH_LIMIT, content.monthDays.size)
            assertEquals("2026-09-09", content.monthDays.first().sourceDate)
            assertEquals("2026-09-24", content.monthDays.last().sourceDate)
            assertEquals("06:00", content.weekDays.first().sunriseLabel)
            assertEquals("19:00", content.weekDays.first().sunsetLabel)

            val calendar = requireNotNull(content.monthDays.toMonthCalendarUiModel())
            assertEquals("Сентябрь 2026", calendar.monthLabel)
            assertEquals(1, calendar.leadingEmptyDays)
            assertEquals(30, calendar.days.size)
            assertEquals(null, calendar.days.first().forecast)
            assertEquals("2026-09-09", requireNotNull(calendar.days[8].forecast).sourceDate)
        } finally {
            TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun `city timezone chooses today even while the device is still on the previous date`() {
        val previousDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        try {
            val forecast = forecast(
                timezone = TOKYO.timezone,
                hourly = emptyList(),
                daily = listOf(
                    day("2026-09-08", min = 8.0, max = 18.0, weatherCode = 1, precipitation = 8),
                    day("2026-09-09", min = 9.0, max = 19.0, weatherCode = 2, precipitation = 9),
                    day("2026-09-10", min = 10.0, max = 20.0, weatherCode = 3, precipitation = 10),
                ),
            )

            val content = forecast.toForecastContentUiModel(
                city = TOKYO,
                temperatureUnit = TemperatureUnit.CELSIUS,
                nowMillis = cityTimeMillis("2026-09-09T00:30", TOKYO.timezone),
            )

            assertEquals(
                listOf("2026-09-09", "2026-09-10"),
                content.weekDays.map { it.sourceDate },
            )
            assertEquals(
                listOf("2026-09-09", "2026-09-10"),
                content.monthDays.map { it.sourceDate },
            )
        } finally {
            TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun `short daily response keeps only valid real days and each row stays internally aligned`() {
        val forecast = forecast(
            timezone = MOSCOW.timezone,
            hourly = emptyList(),
            daily = listOf(
                day("2026-09-09", min = 1.0, max = 11.0, weatherCode = 1, precipitation = 9),
                day("broken-date", min = 99.0, max = 199.0, weatherCode = 999, precipitation = 99),
                day("2026-09-10", min = 2.0, max = 12.0, weatherCode = 61, precipitation = 19),
                day("2026-09-11", min = 3.0, max = 13.0, weatherCode = 71, precipitation = 29),
            ),
        )

        val content = forecast.toForecastContentUiModel(
            city = MOSCOW,
            temperatureUnit = TemperatureUnit.CELSIUS,
            nowMillis = cityTimeMillis("2026-09-09T12:00", MOSCOW.timezone),
        )

        assertEquals(3, content.weekDays.size)
        assertEquals(3, content.monthDays.size)
        assertEquals(
            listOf("2026-09-09", "2026-09-10", "2026-09-11"),
            content.weekDays.map { it.sourceDate },
        )
        assertEquals(
            listOf(1, 2, 3),
            content.weekDays.map { it.minTemperature },
        )
        assertEquals(
            listOf(11, 12, 13),
            content.weekDays.map { it.maxTemperature },
        )
        assertEquals(
            listOf(9, 19, 29),
            content.weekDays.map { it.precipitationProbability },
        )
        assertEquals(
            listOf(WeatherCondition.CLEAR, WeatherCondition.RAIN, WeatherCondition.SNOW),
            content.weekDays.map { it.condition },
        )
        assertEquals(content.weekDays, content.monthDays)
    }

    @Test
    fun `response longer than the API horizon is capped at sixteen real days`() {
        val timezoneId = MOSCOW.timezone
        val sourceDays = dailySequence(
            timezoneId = timezoneId,
            startDate = "2026-09-09",
            count = 20,
        )
        val forecast = forecast(
            timezone = timezoneId,
            hourly = emptyList(),
            daily = sourceDays,
        )

        val content = forecast.toForecastContentUiModel(
            city = MOSCOW,
            temperatureUnit = TemperatureUnit.CELSIUS,
            nowMillis = cityTimeMillis("2026-09-09T12:00", timezoneId),
        )

        assertEquals(FORECAST_MONTH_LIMIT, content.monthDays.size)
        assertEquals(
            sourceDays.take(FORECAST_MONTH_LIMIT).map { it.date },
            content.monthDays.map { it.sourceDate },
        )
        assertEquals("2026-09-24", content.monthDays.last().sourceDate)
        assertEquals(FORECAST_WEEK_LIMIT, content.weekDays.size)
    }

    private fun hourlySequence(timezoneId: String, count: Int): List<HourlyWeather> {
        val timezone = TimeZone.getTimeZone(timezoneId)
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).apply {
            timeZone = timezone
        }
        val calendar = Calendar.getInstance(timezone).apply {
            timeInMillis = cityTimeMillis("2026-09-09T00:00", timezoneId)
        }
        return List(count) { index ->
            hour(
                time = formatter.format(calendar.time).also {
                    calendar.add(Calendar.HOUR_OF_DAY, 1)
                },
                temperature = index.toDouble(),
            )
        }
    }

    private fun dailySequence(
        timezoneId: String,
        startDate: String,
        count: Int,
    ): List<DailyWeather> {
        val timezone = TimeZone.getTimeZone(timezoneId)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = timezone
        }
        val calendar = Calendar.getInstance(timezone).apply {
            timeInMillis = cityTimeMillis("${startDate}T00:00", timezoneId)
        }
        return List(count) { index ->
            day(
                date = formatter.format(calendar.time).also {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                },
                min = index.toDouble(),
                max = index + 10.0,
                weatherCode = 2,
                precipitation = index,
            )
        }
    }

    private fun cityTimeMillis(value: String, timezoneId: String): Long =
        checkNotNull(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone(timezoneId)
            }.parse(value),
        ).time

    private fun forecast(
        timezone: String,
        hourly: List<HourlyWeather>,
        currentTemperature: Double = 20.0,
        currentWeatherCode: Int = 2,
        daily: List<DailyWeather> = listOf(
            day(
                date = "2026-09-09",
                min = 12.0,
                max = 24.0,
                weatherCode = 2,
                precipitation = 10,
            ),
        ),
    ) = Forecast(
        timezone = timezone,
        current = CurrentWeather(
            time = "2026-09-09T12:30",
            temperatureCelsius = currentTemperature,
            apparentTemperatureCelsius = currentTemperature - 2.0,
            relativeHumidity = 64,
            pressure = 1_012.5,
            windSpeed = 7.5,
            weatherCode = currentWeatherCode,
            isDay = true,
        ),
        hourly = hourly,
        daily = daily,
    )

    private fun hour(
        time: String,
        temperature: Double,
        weatherCode: Int = 2,
        precipitation: Int = 10,
    ) = HourlyWeather(
        time = time,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = temperature - 1.0,
        humidity = 60,
        precipitationProbability = precipitation,
        weatherCode = weatherCode,
        windSpeed = 8.0,
    )

    private fun day(
        date: String,
        min: Double,
        max: Double,
        weatherCode: Int,
        precipitation: Int,
    ) = DailyWeather(
        date = date,
        maxTemperatureCelsius = max,
        minTemperatureCelsius = min,
        weatherCode = weatherCode,
        precipitationProbability = precipitation,
        sunrise = "${date}T06:00",
        sunset = "${date}T19:00",
    )

    private companion object {
        val MOSCOW = city(
            id = 524901L,
            name = "Москва",
            timezone = "Europe/Moscow",
        )
        val TOKYO = city(
            id = 1850147L,
            name = "Токио",
            timezone = "Asia/Tokyo",
        )

        fun city(id: Long, name: String, timezone: String) = City(
            id = id,
            name = name,
            country = "Страна",
            adminArea = null,
            latitude = 0.0,
            longitude = 0.0,
            timezone = timezone,
        )
    }
}
