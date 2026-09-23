package com.tsygankov.weatherforecast.di

import com.tsygankov.weatherforecast.worker.WeatherRefreshWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

internal val workerModule = module {
    workerOf(::WeatherRefreshWorker)
}
