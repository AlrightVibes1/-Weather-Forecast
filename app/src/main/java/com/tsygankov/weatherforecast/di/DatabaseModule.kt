package com.tsygankov.weatherforecast.di

import androidx.room.Room
import com.tsygankov.weatherforecast.core.database.MIGRATION_1_2
import com.tsygankov.weatherforecast.core.database.WeatherDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATABASE_NAME = "weather.db"

internal val databaseModule = module {
    single {
        Room.databaseBuilder(
            context = androidContext(),
            klass = WeatherDatabase::class.java,
            name = DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    single { get<WeatherDatabase>().cityDao() }
}
