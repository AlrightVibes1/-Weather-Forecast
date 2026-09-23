package com.tsygankov.weatherforecast.data.mapper

import com.tsygankov.weatherforecast.core.database.CityWithCurrentWeather
import com.tsygankov.weatherforecast.core.database.CityEntity
import com.tsygankov.weatherforecast.core.database.CurrentWeatherEntity
import com.tsygankov.weatherforecast.core.database.DailyWeatherEntity
import com.tsygankov.weatherforecast.core.database.ForecastCacheWrite
import com.tsygankov.weatherforecast.core.database.HourlyWeatherEntity
import com.tsygankov.weatherforecast.core.database.StoredForecast
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.core.model.UNKNOWN_WMO_CODE

internal fun CityWithCurrentWeather.toDomain(): City = city.toDomain(currentWeather)

internal fun CityEntity.toDomain(): City = toDomain(currentWeather = null)

private fun CityEntity.toDomain(currentWeather: CurrentWeatherEntity?): City = City(
    id = id,
    name = name,
    country = country,
    adminArea = adminArea,
    latitude = latitude,
    longitude = longitude,
    timezone = timezone,
    currentTemperatureCelsius = currentWeather?.temperatureCelsius,
    currentWeatherCode = currentWeather?.weatherCode,
    updatedAt = currentWeather?.updatedAt,
)

internal fun StoredForecast.toDomainOrNull(): Forecast? {
    val current = currentWeather?.toDomainOrNull() ?: return null
    val hourly = hourlyWeather.sortedBy(HourlyWeatherEntity::position).map(HourlyWeatherEntity::toDomain)
    val daily = dailyWeather.sortedBy(DailyWeatherEntity::position).map(DailyWeatherEntity::toDomain)
    if (hourly.isEmpty() || daily.isEmpty()) return null
    return Forecast(
        timezone = city.timezone,
        current = current,
        hourly = hourly,
        daily = daily,
    )
}

internal fun Forecast.toCacheWrite(cityId: Long, updatedAt: Long): ForecastCacheWrite =
    ForecastCacheWrite(
        cityId = cityId,
        timezone = timezone,
        currentWeather = CurrentWeatherEntity(
            cityId = cityId,
            time = current.time,
            temperatureCelsius = current.temperatureCelsius,
            apparentTemperatureCelsius = current.apparentTemperatureCelsius,
            relativeHumidity = current.relativeHumidity,
            pressure = current.pressure,
            windSpeed = current.windSpeed,
            weatherCode = current.weatherCode,
            isDay = current.isDay,
            updatedAt = updatedAt,
        ),
        hourlyWeather = hourly.mapIndexed { position, weather ->
            HourlyWeatherEntity(
                cityId = cityId,
                position = position,
                time = weather.time,
                temperatureCelsius = weather.temperatureCelsius,
                apparentTemperatureCelsius = weather.apparentTemperatureCelsius,
                humidity = weather.humidity,
                precipitationProbability = weather.precipitationProbability,
                weatherCode = weather.weatherCode ?: UNKNOWN_WMO_CODE,
                windSpeed = weather.windSpeed,
            )
        },
        dailyWeather = daily.mapIndexed { position, weather ->
            DailyWeatherEntity(
                cityId = cityId,
                position = position,
                date = weather.date,
                maxTemperatureCelsius = weather.maxTemperatureCelsius,
                minTemperatureCelsius = weather.minTemperatureCelsius,
                weatherCode = weather.weatherCode ?: UNKNOWN_WMO_CODE,
                precipitationProbability = weather.precipitationProbability,
                sunrise = weather.sunrise,
                sunset = weather.sunset,
            )
        },
    )

private fun CurrentWeatherEntity.toDomainOrNull(): CurrentWeather? {
    val validTime = time ?: return null
    val validTemperature = temperatureCelsius ?: return null
    val validApparentTemperature = apparentTemperatureCelsius ?: return null
    val validHumidity = relativeHumidity ?: return null
    val validPressure = pressure ?: return null
    val validWindSpeed = windSpeed ?: return null
    val validIsDay = isDay ?: return null
    return CurrentWeather(
        time = validTime,
        temperatureCelsius = validTemperature,
        apparentTemperatureCelsius = validApparentTemperature,
        relativeHumidity = validHumidity,
        pressure = validPressure,
        windSpeed = validWindSpeed,
        weatherCode = weatherCode,
        isDay = validIsDay,
    )
}

private fun HourlyWeatherEntity.toDomain(): HourlyWeather = HourlyWeather(
    time = time,
    temperatureCelsius = temperatureCelsius,
    apparentTemperatureCelsius = apparentTemperatureCelsius,
    humidity = humidity,
    precipitationProbability = precipitationProbability,
    weatherCode = weatherCode,
    windSpeed = windSpeed,
)

private fun DailyWeatherEntity.toDomain(): DailyWeather = DailyWeather(
    date = date,
    maxTemperatureCelsius = maxTemperatureCelsius,
    minTemperatureCelsius = minTemperatureCelsius,
    weatherCode = weatherCode,
    precipitationProbability = precipitationProbability,
    sunrise = sunrise,
    sunset = sunset,
)
