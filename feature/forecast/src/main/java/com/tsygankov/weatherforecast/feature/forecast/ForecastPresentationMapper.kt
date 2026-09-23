package com.tsygankov.weatherforecast.feature.forecast

import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import com.tsygankov.weatherforecast.core.model.roundedInUnit
import com.tsygankov.weatherforecast.ui.weather.WeatherDateTimeFormatter
import com.tsygankov.weatherforecast.ui.weather.formatRussianDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

internal const val FORECAST_HOURLY_LIMIT = 24
internal const val FORECAST_WEEK_LIMIT = 7
internal const val FORECAST_MONTH_LIMIT = 16

data class ForecastContentUiModel(
    val current: CurrentForecastUiModel,
    val hourly: List<HourlyForecastUiModel>,
    val weekDays: List<DailyForecastUiModel>,
    val monthDays: List<DailyForecastUiModel>,
)

data class CurrentForecastUiModel(
    val temperature: Int,
    val apparentTemperature: Int,
    val humidityPercent: Int,
    val pressure: String,
    val windSpeed: String,
    val condition: WeatherCondition,
    val localTimeLabel: String? = null,
)

data class HourlyForecastUiModel(
    val sourceTime: String,
    val timeLabel: String,
    val compactTimeLabel: String,
    val temperature: Int,
    val precipitationProbability: Int,
    val condition: WeatherCondition,
)

data class DailyForecastUiModel(
    val sourceDate: String,
    val dayOfWeekLabel: String,
    val dateLabel: String,
    val minTemperature: Int,
    val maxTemperature: Int,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
    val isToday: Boolean = false,
    val sunriseLabel: String? = null,
    val sunsetLabel: String? = null,
)

data class ForecastMonthCalendarUiModel(
    val monthLabel: String,
    val leadingEmptyDays: Int,
    val days: List<ForecastCalendarDayUiModel>,
)

data class ForecastCalendarDayUiModel(
    val sourceDate: String,
    val dayOfMonth: Int,
    val forecast: DailyForecastUiModel?,
)

internal fun Forecast.toForecastContentUiModel(
    city: City,
    temperatureUnit: TemperatureUnit,
    nowMillis: Long,
): ForecastContentUiModel = toForecastContentUiModel(
    fallbackTimezone = city.timezone,
    temperatureUnit = temperatureUnit,
    nowMillis = nowMillis,
)

internal fun Forecast.toForecastContentUiModel(
    fallbackTimezone: String,
    temperatureUnit: TemperatureUnit,
    nowMillis: Long,
): ForecastContentUiModel {
    val formatter = WeatherDateTimeFormatter.forTimeZone(
        primary = timezone,
        fallback = fallbackTimezone,
    )

    val parsedHours = hourly
        .mapNotNull { point ->
            val parsedTime = formatter.parseForecastHour(point.time) ?: return@mapNotNull null
            ParsedHourlyPoint(
                timestampMillis = parsedTime,
                uiModel = HourlyForecastUiModel(
                    sourceTime = point.time,
                    timeLabel = formatter.formatForecastHour(parsedTime),
                    compactTimeLabel = formatter.formatCompactForecastHour(parsedTime),
                    temperature = point.temperatureCelsius.roundedInUnit(temperatureUnit),
                    precipitationProbability = point.precipitationProbability.coerceIn(0, 100),
                    condition = WeatherCondition.fromWmo(point.weatherCode),
                ),
            )
        }
        .sortedBy(ParsedHourlyPoint::timestampMillis)
    val nearestHourIndex = parsedHours.indices.minWithOrNull(
        compareBy<Int> { index -> abs(parsedHours[index].timestampMillis - nowMillis) }
            .thenBy { index ->
                if (parsedHours[index].timestampMillis < nowMillis) PAST_HOUR_TIE_BREAKER else FUTURE_HOUR_TIE_BREAKER
            },
    ) ?: 0
    val nearestHours = parsedHours
        .asSequence()
        .drop(nearestHourIndex)
        .take(FORECAST_HOURLY_LIMIT)
        .map(ParsedHourlyPoint::uiModel)
        .toList()
    val startOfTodayMillis = formatter.startOfDay(nowMillis)
    val availableDays = daily
        .mapNotNull { day ->
            val parsedDate = formatter.parseForecastDay(day.date) ?: return@mapNotNull null
            ParsedDailyPoint(
                timestampMillis = parsedDate,
                uiModel = DailyForecastUiModel(
                    sourceDate = day.date,
                    dayOfWeekLabel = formatter.formatDayOfWeek(parsedDate),
                    dateLabel = formatter.formatForecastDate(parsedDate),
                    minTemperature = day.minTemperatureCelsius.roundedInUnit(temperatureUnit),
                    maxTemperature = day.maxTemperatureCelsius.roundedInUnit(temperatureUnit),
                    condition = WeatherCondition.fromWmo(day.weatherCode),
                    precipitationProbability = day.precipitationProbability,
                    isToday = parsedDate == startOfTodayMillis,
                    sunriseLabel = formatter.parseForecastHour(day.sunrise)
                        ?.let(formatter::formatCompactForecastHour),
                    sunsetLabel = formatter.parseForecastHour(day.sunset)
                        ?.let(formatter::formatCompactForecastHour),
                ),
            )
        }
        .sortedBy(ParsedDailyPoint::timestampMillis)
        .asSequence()
        .filter { day -> day.timestampMillis >= startOfTodayMillis }
        .take(FORECAST_MONTH_LIMIT)
        .map(ParsedDailyPoint::uiModel)
        .toList()

    return ForecastContentUiModel(
        current = CurrentForecastUiModel(
            temperature = current.temperatureCelsius.roundedInUnit(temperatureUnit),
            apparentTemperature = current.apparentTemperatureCelsius.roundedInUnit(temperatureUnit),
            humidityPercent = current.relativeHumidity,
            pressure = current.pressure.formatRussianDecimal(),
            windSpeed = current.windSpeed.formatRussianDecimal(),
            condition = WeatherCondition.fromWmo(current.weatherCode),
            localTimeLabel = formatter.parseForecastHour(current.time)
                ?.let(formatter::formatCurrentMoment),
        ),
        hourly = nearestHours,
        weekDays = availableDays.take(FORECAST_WEEK_LIMIT),
        monthDays = availableDays,
    )
}

internal fun List<DailyForecastUiModel>.toMonthCalendarUiModel(): ForecastMonthCalendarUiModel? {
    val sourceDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = CALENDAR_TIME_ZONE
    }
    val monthTitleFormatter = SimpleDateFormat(
        "LLLL yyyy",
        Locale.forLanguageTag("ru"),
    ).apply { timeZone = CALENDAR_TIME_ZONE }
    val parsedDays = mapNotNull { day ->
        runCatching { sourceDateFormatter.parse(day.sourceDate) }
            .getOrNull()
            ?.let { parsedDate -> parsedDate.time to day }
    }
    val firstTimestamp = parsedDays.minOfOrNull { (timestamp) -> timestamp } ?: return null
    val monthCalendar = Calendar.getInstance(CALENDAR_TIME_ZONE).apply {
        timeInMillis = firstTimestamp
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val targetYear = monthCalendar.get(Calendar.YEAR)
    val targetMonth = monthCalendar.get(Calendar.MONTH)
    val forecastsBySourceDate = parsedDays
        .filter { (timestamp) ->
            Calendar.getInstance(CALENDAR_TIME_ZONE).apply { timeInMillis = timestamp }.let {
                it.get(Calendar.YEAR) == targetYear && it.get(Calendar.MONTH) == targetMonth
            }
        }
        .associate { (_, day) -> day.sourceDate to day }
    val monthLabel = monthTitleFormatter.format(monthCalendar.time).replaceFirstChar { character ->
        character.titlecase(Locale.forLanguageTag("ru"))
    }
    val leadingEmptyDays = (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    return ForecastMonthCalendarUiModel(
        monthLabel = monthLabel,
        leadingEmptyDays = leadingEmptyDays,
        days = (1..daysInMonth).map { dayOfMonth ->
            monthCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            val sourceDate = sourceDateFormatter.format(monthCalendar.time)
            ForecastCalendarDayUiModel(
                sourceDate = sourceDate,
                dayOfMonth = dayOfMonth,
                forecast = forecastsBySourceDate[sourceDate],
            )
        },
    )
}

private data class ParsedHourlyPoint(
    val timestampMillis: Long,
    val uiModel: HourlyForecastUiModel,
)

private data class ParsedDailyPoint(
    val timestampMillis: Long,
    val uiModel: DailyForecastUiModel,
)
private const val FUTURE_HOUR_TIE_BREAKER = 0
private const val PAST_HOUR_TIE_BREAKER = 1
private val CALENDAR_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")
