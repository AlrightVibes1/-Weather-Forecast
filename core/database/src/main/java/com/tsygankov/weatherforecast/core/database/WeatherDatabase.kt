package com.tsygankov.weatherforecast.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CityEntity::class,
        CurrentWeatherEntity::class,
        HourlyWeatherEntity::class,
        DailyWeatherEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
}
