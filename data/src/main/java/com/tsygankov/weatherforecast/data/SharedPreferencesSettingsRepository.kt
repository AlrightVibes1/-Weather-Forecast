package com.tsygankov.weatherforecast.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SharedPreferencesSettingsRepository(
    private val preferences: SharedPreferences,
) : SettingsRepository {
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    override fun setTemperatureUnit(unit: TemperatureUnit) {
        preferences.edit { putString(KEY_UNIT, unit.name) }
        _settings.update { current -> current.copy(temperatureUnit = unit) }
    }

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME, mode.name) }
        _settings.update { current -> current.copy(themeMode = mode) }
    }

    override fun setAutomaticLocationWeather(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTOMATIC_LOCATION_WEATHER, enabled) }
        _settings.update { current -> current.copy(automaticLocationWeather = enabled) }
    }

    private fun read() = UserSettings(
        temperatureUnit = readEnum(
            key = KEY_UNIT,
            default = TemperatureUnit.CELSIUS,
            parse = TemperatureUnit::valueOf,
        ),
        themeMode = readEnum(
            key = KEY_THEME,
            default = ThemeMode.LIGHT,
            parse = ThemeMode::valueOf,
        ),
        automaticLocationWeather = runCatching {
            preferences.getBoolean(KEY_AUTOMATIC_LOCATION_WEATHER, true)
        }.getOrDefault(true),
    )

    private fun <T> readEnum(
        key: String,
        default: T,
        parse: (String) -> T,
    ): T = runCatching {
        preferences.getString(key, null)?.let(parse)
    }.getOrNull() ?: default

    private companion object {
        const val KEY_UNIT = "temperature_unit"
        const val KEY_THEME = "theme_mode"
        const val KEY_AUTOMATIC_LOCATION_WEATHER = "automatic_location_weather"
    }
}
