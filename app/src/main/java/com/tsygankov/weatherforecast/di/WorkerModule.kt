package com.tsygankov.weatherforecast.di

import com.tsygankov.weatherforecast.worker.WeatherRefreshWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

internal val workerModule = module {
    // Фабрика WorkManager через Koin создаёт Worker с параметрами запуска
    // и передаёт ему репозиторий, который выполняет обновление погоды.
    workerOf(::WeatherRefreshWorker)
}
