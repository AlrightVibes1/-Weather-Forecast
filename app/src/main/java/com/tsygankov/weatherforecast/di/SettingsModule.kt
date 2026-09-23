package com.tsygankov.weatherforecast.di

import android.content.Context
import com.tsygankov.weatherforecast.data.SharedPreferencesSettingsRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private const val PREFERENCES_NAME = "weather_settings"

internal val settingsModule = module {
    single {
        androidContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    singleOf(::SharedPreferencesSettingsRepository) bind SettingsRepository::class
}
