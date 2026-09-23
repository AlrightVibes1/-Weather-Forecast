package com.tsygankov.weatherforecast

import android.app.Application
import com.tsygankov.weatherforecast.di.appModules
import com.tsygankov.weatherforecast.worker.WeatherRefreshScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class WeatherForecastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WeatherForecastApplication)
            workManagerFactory()
            modules(appModules)
        }
        WeatherRefreshScheduler.schedule(this)
    }
}
