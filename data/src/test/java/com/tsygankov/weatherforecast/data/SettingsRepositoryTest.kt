package com.tsygankov.weatherforecast.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryTest {
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `empty preferences use first version defaults`() {
        val repository = SharedPreferencesSettingsRepository(preferences)

        assertEquals(TemperatureUnit.CELSIUS, repository.settings.value.temperatureUnit)
        assertEquals(ThemeMode.LIGHT, repository.settings.value.themeMode)
        assertEquals(true, repository.settings.value.automaticLocationWeather)
    }

    @Test
    fun `all settings are emitted immediately and restored by a new repository`() {
        val repository = SharedPreferencesSettingsRepository(preferences)

        repository.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAutomaticLocationWeather(false)

        assertEquals(TemperatureUnit.FAHRENHEIT, repository.settings.value.temperatureUnit)
        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
        assertEquals(false, repository.settings.value.automaticLocationWeather)
        val restored = SharedPreferencesSettingsRepository(preferences).settings.value
        assertEquals(TemperatureUnit.FAHRENHEIT, restored.temperatureUnit)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(false, restored.automaticLocationWeather)
    }

    @Test
    fun `unknown stored values fall back to first version defaults`() {
        preferences.edit()
            .putString("temperature_unit", "KELVIN")
            .putString("theme_mode", "SYSTEM")
            .commit()

        val repository = SharedPreferencesSettingsRepository(preferences)

        assertEquals(TemperatureUnit.CELSIUS, repository.settings.value.temperatureUnit)
        assertEquals(ThemeMode.LIGHT, repository.settings.value.themeMode)
    }

    @Test
    fun `stored values with a wrong preference type also fall back without a crash`() {
        preferences.edit()
            .putInt("temperature_unit", 1)
            .putBoolean("theme_mode", true)
            .putString("automatic_location_weather", "invalid")
            .commit()

        val repository = SharedPreferencesSettingsRepository(preferences)

        assertEquals(TemperatureUnit.CELSIUS, repository.settings.value.temperatureUnit)
        assertEquals(ThemeMode.LIGHT, repository.settings.value.themeMode)
        assertEquals(true, repository.settings.value.automaticLocationWeather)
    }

    private companion object {
        const val PREFERENCES_NAME = "wf_007_settings_test"
    }
}
