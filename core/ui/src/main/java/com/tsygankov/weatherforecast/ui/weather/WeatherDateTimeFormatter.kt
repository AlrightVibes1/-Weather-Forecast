package com.tsygankov.weatherforecast.ui.weather

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class WeatherDateTimeFormatter private constructor(
    private val timezone: TimeZone,
) {
    private val hourInput = SimpleDateFormat(FORECAST_API_HOUR_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = timezone
    }
    private val hourOutput = SimpleDateFormat(FORECAST_HOUR_LABEL_PATTERN, RUSSIAN_LOCALE).apply {
        timeZone = timezone
    }
    private val compactHourOutput =
        SimpleDateFormat(FORECAST_COMPACT_HOUR_LABEL_PATTERN, RUSSIAN_LOCALE).apply {
            timeZone = timezone
        }
    private val dayInput = SimpleDateFormat(FORECAST_API_DAY_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = timezone
    }
    private val dayOfWeekOutput = SimpleDateFormat(FORECAST_DAY_OF_WEEK_PATTERN, RUSSIAN_LOCALE).apply {
        timeZone = timezone
    }
    private val dateOutput = SimpleDateFormat(FORECAST_DATE_LABEL_PATTERN, RUSSIAN_LOCALE).apply {
        timeZone = timezone
    }
    private val lastUpdatedOutput = SimpleDateFormat(LAST_UPDATED_PATTERN, RUSSIAN_LOCALE).apply {
        timeZone = timezone
    }
    private val currentMomentOutput = SimpleDateFormat(CURRENT_MOMENT_PATTERN, RUSSIAN_LOCALE).apply {
        timeZone = timezone
    }

    fun parseForecastHour(value: String): Long? = hourInput.parseFully(value)

    fun formatForecastHour(timestampMillis: Long): String = hourOutput.format(timestampMillis)

    fun formatCompactForecastHour(timestampMillis: Long): String = compactHourOutput.format(timestampMillis)

    fun formatCurrentMoment(timestampMillis: Long): String = currentMomentOutput.format(timestampMillis)

    fun parseForecastDay(value: String): Long? = dayInput.parseFully(value)

    fun formatDayOfWeek(timestampMillis: Long): String = dayOfWeekOutput.format(timestampMillis)

    fun formatForecastDate(timestampMillis: Long): String = dateOutput.format(timestampMillis)

    fun formatLastUpdated(timestampMillis: Long): String = lastUpdatedOutput.format(timestampMillis)

    fun startOfDay(timestampMillis: Long): Long = Calendar.getInstance(timezone).apply {
        timeInMillis = timestampMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        fun forTimeZone(
            primary: String,
            fallback: String? = null,
        ): WeatherDateTimeFormatter = WeatherDateTimeFormatter(
            timezone = primary.toTimeZoneOrNull()
                ?: fallback?.toTimeZoneOrNull()
                ?: TimeZone.getTimeZone(UTC_TIMEZONE),
        )
    }
}

fun Double.formatRussianDecimal(): String = DecimalFormat(
    "0.#",
    DecimalFormatSymbols(RUSSIAN_LOCALE),
).format(this)

private fun SimpleDateFormat.parseFully(value: String): Long? {
    val position = ParsePosition(0)
    val parsed = parse(value, position) ?: return null
    return parsed.time.takeIf { position.index == value.length }
}

private fun String.toTimeZoneOrNull(): TimeZone? {
    val timezone = TimeZone.getTimeZone(this)
    val isExplicitGmt = equals("GMT", ignoreCase = true) ||
        equals("UTC", ignoreCase = true) ||
        startsWith("GMT+") ||
        startsWith("GMT-")
    return timezone.takeUnless { it.id == "GMT" && !isExplicitGmt }
}

private val RUSSIAN_LOCALE = Locale.forLanguageTag("ru-RU")
private const val FORECAST_API_HOUR_PATTERN = "yyyy-MM-dd'T'HH:mm"
private const val FORECAST_HOUR_LABEL_PATTERN = "EEE, HH:mm"
private const val FORECAST_COMPACT_HOUR_LABEL_PATTERN = "HH:mm"
private const val FORECAST_API_DAY_PATTERN = "yyyy-MM-dd"
private const val FORECAST_DAY_OF_WEEK_PATTERN = "EEEE"
private const val FORECAST_DATE_LABEL_PATTERN = "d MMMM"
private const val LAST_UPDATED_PATTERN = "d MMMM yyyy, HH:mm"
private const val CURRENT_MOMENT_PATTERN = "EEE, d MMMM, HH:mm"
private const val UTC_TIMEZONE = "UTC"
