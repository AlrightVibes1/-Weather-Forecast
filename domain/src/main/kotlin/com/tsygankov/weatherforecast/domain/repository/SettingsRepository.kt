package com.tsygankov.weatherforecast.domain.repository

import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<UserSettings>

    fun setTemperatureUnit(unit: TemperatureUnit)

    fun setThemeMode(mode: ThemeMode)

    fun setAutomaticLocationWeather(enabled: Boolean)
}
