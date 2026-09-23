package com.tsygankov.weatherforecast.di

import com.tsygankov.weatherforecast.AppThemeViewModel
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.feature.forecast.ForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.LocationForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.TemporaryCityForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.WeatherStartViewModel
import com.tsygankov.weatherforecast.feature.home.HomeViewModel
import com.tsygankov.weatherforecast.feature.settings.SettingsViewModel
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val viewModelModule = module {
    single<AppClock> { AppClock { System.currentTimeMillis() } }
    viewModelOf(::AppThemeViewModel)
    viewModel {
        HomeViewModel(
            favoritesRepository = get<FavoriteCitiesRepository>(),
            weatherRepository = get<WeatherSearchRepository>(),
            deviceLocationRepository = getOrNull<DeviceLocationRepository>(),
            settingsRepository = get<SettingsRepository>(),
            clock = get(),
        )
    }
    viewModelOf(::ForecastViewModel)
    viewModelOf(::LocationForecastViewModel)
    viewModelOf(::TemporaryCityForecastViewModel)
    viewModelOf(::WeatherStartViewModel)
    viewModelOf(::SettingsViewModel)
}
