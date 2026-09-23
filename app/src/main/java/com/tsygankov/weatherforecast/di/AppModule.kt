package com.tsygankov.weatherforecast.di

import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    includes(
        networkModule,
        databaseModule,
        repositoryModule,
        settingsModule,
        viewModelModule,
        workerModule,
    )
}

val appModules: List<Module> = listOf(appModule)
