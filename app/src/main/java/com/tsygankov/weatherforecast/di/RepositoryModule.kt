package com.tsygankov.weatherforecast.di

import com.tsygankov.weatherforecast.data.AndroidDeviceLocationRepository
import com.tsygankov.weatherforecast.data.OfflineFirstFavoriteCitiesRepository
import com.tsygankov.weatherforecast.data.OpenMeteoWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import org.koin.core.module.dsl.singleOf
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

internal val repositoryModule = module {
    singleOf(::OpenMeteoWeatherRepository) bind WeatherSearchRepository::class
    single<CoordinatesWeatherRepository> { get<OpenMeteoWeatherRepository>() }
    single<DeviceLocationRepository> { AndroidDeviceLocationRepository(androidContext()) }
    single<FavoriteCitiesRepository> {
        OfflineFirstFavoriteCitiesRepository(
            dao = get(),
            weatherRepository = get(),
        )
    }
}
