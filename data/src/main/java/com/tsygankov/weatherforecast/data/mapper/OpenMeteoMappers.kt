package com.tsygankov.weatherforecast.data.mapper

import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.core.network.CityDto
import com.tsygankov.weatherforecast.core.network.CurrentDto
import com.tsygankov.weatherforecast.core.network.DailyDto
import com.tsygankov.weatherforecast.core.network.ForecastResponseDto
import com.tsygankov.weatherforecast.core.network.GeocodingResponseDto
import com.tsygankov.weatherforecast.core.network.HourlyDto
import com.tsygankov.weatherforecast.data.DataException

internal fun GeocodingResponseDto.toDomain(): List<CitySearchResult> =
    results.orEmpty().mapNotNull { it?.toDomainOrNull() }

private fun CityDto.toDomainOrNull(): CitySearchResult? {
    val validId = id?.takeIf { it > 0 } ?: return null
    val validName = name.nonBlankOrNull() ?: return null
    val validLatitude = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
    val validLongitude = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
    val validTimezone = timezone.nonBlankOrNull() ?: return null
    return CitySearchResult(
        id = validId,
        name = validName,
        country = country.nonBlankOrNull(),
        adminArea = adminArea.nonBlankOrNull(),
        latitude = validLatitude,
        longitude = validLongitude,
        timezone = validTimezone,
    )
}

internal fun ForecastResponseDto.toDomain(): Forecast {
    val validTimezone = timezone.nonBlankOrNull()
        ?: throw DataException.EmptyData("timezone")
    val mappedCurrent = current?.toDomain()
        ?: throw DataException.EmptyData("current")
    val mappedHourly = hourly?.toDomain().orEmpty()
    if (mappedHourly.isEmpty()) throw DataException.EmptyData("hourly")
    val mappedDaily = daily?.toDomain().orEmpty()
    if (mappedDaily.isEmpty()) throw DataException.EmptyData("daily")
    return Forecast(
        timezone = validTimezone,
        current = mappedCurrent,
        hourly = mappedHourly,
        daily = mappedDaily,
    )
}

private fun CurrentDto.toDomain(): CurrentWeather = CurrentWeather(
    time = time.nonBlankOrMissing("current.time"),
    temperatureCelsius = temperature.finiteOrMissing("current.temperature_2m"),
    apparentTemperatureCelsius = apparentTemperature.finiteOrMissing("current.apparent_temperature"),
    relativeHumidity = relativeHumidity ?: throw DataException.EmptyData("current.relative_humidity_2m"),
    pressure = pressure.finiteOrMissing("current.surface_pressure"),
    windSpeed = windSpeed.finiteOrMissing("current.wind_speed_10m"),
    weatherCode = weatherCode,
    isDay = when (isDay) {
        0 -> false
        1 -> true
        else -> throw DataException.EmptyData("current.is_day")
    },
)

private fun HourlyDto.toDomain(): List<HourlyWeather> = time.orEmpty().mapIndexedNotNull { index, value ->
    val validTime = value.nonBlankOrNull() ?: return@mapIndexedNotNull null
    val temperature = temperatures?.getOrNull(index).finiteOrNull() ?: return@mapIndexedNotNull null
    val apparent = apparentTemperatures?.getOrNull(index).finiteOrNull() ?: return@mapIndexedNotNull null
    val humidity = humidities?.getOrNull(index) ?: return@mapIndexedNotNull null
    val precipitation = precipitationProbabilities?.getOrNull(index) ?: return@mapIndexedNotNull null
    val code = weatherCodes?.getOrNull(index)
    val wind = windSpeeds?.getOrNull(index).finiteOrNull() ?: return@mapIndexedNotNull null
    HourlyWeather(
        time = validTime,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = apparent,
        humidity = humidity,
        precipitationProbability = precipitation,
        weatherCode = code,
        windSpeed = wind,
    )
}

private fun DailyDto.toDomain(): List<DailyWeather> = time.orEmpty().mapIndexedNotNull { index, value ->
    val validDate = value.nonBlankOrNull() ?: return@mapIndexedNotNull null
    val maximum = maxTemperatures?.getOrNull(index).finiteOrNull() ?: return@mapIndexedNotNull null
    val minimum = minTemperatures?.getOrNull(index).finiteOrNull() ?: return@mapIndexedNotNull null
    val code = weatherCodes?.getOrNull(index)
    val precipitation = precipitationProbabilities?.getOrNull(index) ?: return@mapIndexedNotNull null
    val validSunrise = sunrise?.getOrNull(index).nonBlankOrNull() ?: return@mapIndexedNotNull null
    val validSunset = sunset?.getOrNull(index).nonBlankOrNull() ?: return@mapIndexedNotNull null
    DailyWeather(
        date = validDate,
        maxTemperatureCelsius = maximum,
        minTemperatureCelsius = minimum,
        weatherCode = code,
        precipitationProbability = precipitation,
        sunrise = validSunrise,
        sunset = validSunset,
    )
}

private fun String?.nonBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

private fun String?.nonBlankOrMissing(field: String): String =
    nonBlankOrNull() ?: throw DataException.EmptyData(field)

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)

private fun Double?.finiteOrMissing(field: String): Double =
    finiteOrNull() ?: throw DataException.EmptyData(field)
