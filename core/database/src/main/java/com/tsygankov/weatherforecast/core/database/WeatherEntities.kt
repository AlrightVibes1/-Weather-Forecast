package com.tsygankov.weatherforecast.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "favorite_cities")
data class CityEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val country: String,
    val adminArea: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val addedAt: Long,
)

@Entity(
    tableName = "current_weather",
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["id"],
            childColumns = ["cityId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CurrentWeatherEntity(
    @PrimaryKey val cityId: Long,
    val time: String?,
    val temperatureCelsius: Double?,
    val apparentTemperatureCelsius: Double?,
    val relativeHumidity: Int?,
    val pressure: Double?,
    val windSpeed: Double?,
    val weatherCode: Int?,
    val isDay: Boolean?,
    val updatedAt: Long?,
)

@Entity(
    tableName = "hourly_weather",
    primaryKeys = ["cityId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["id"],
            childColumns = ["cityId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cityId")],
)
data class HourlyWeatherEntity(
    val cityId: Long,
    val position: Int,
    val time: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val humidity: Int,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val windSpeed: Double,
)

@Entity(
    tableName = "daily_weather",
    primaryKeys = ["cityId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["id"],
            childColumns = ["cityId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cityId")],
)
data class DailyWeatherEntity(
    val cityId: Long,
    val position: Int,
    val date: String,
    val maxTemperatureCelsius: Double,
    val minTemperatureCelsius: Double,
    val weatherCode: Int,
    val precipitationProbability: Int,
    val sunrise: String,
    val sunset: String,
)

data class CityWithCurrentWeather(
    @Embedded val city: CityEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "cityId",
    )
    val currentWeather: CurrentWeatherEntity?,
)

data class StoredForecast(
    @Embedded val city: CityEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "cityId",
    )
    val currentWeather: CurrentWeatherEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "cityId",
    )
    val hourlyWeather: List<HourlyWeatherEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "cityId",
    )
    val dailyWeather: List<DailyWeatherEntity>,
)

data class ForecastCacheWrite(
    val cityId: Long,
    val timezone: String,
    val currentWeather: CurrentWeatherEntity,
    val hourlyWeather: List<HourlyWeatherEntity>,
    val dailyWeather: List<DailyWeatherEntity>,
)
