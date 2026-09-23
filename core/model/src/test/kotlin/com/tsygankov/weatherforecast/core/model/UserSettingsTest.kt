package com.tsygankov.weatherforecast.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {
    @Test
    fun `first version exposes only light and dark themes`() {
        assertArrayEquals(
            arrayOf(ThemeMode.LIGHT, ThemeMode.DARK),
            ThemeMode.entries.toTypedArray(),
        )
    }

    @Test
    fun `default settings use Celsius and light theme`() {
        assertEquals(TemperatureUnit.CELSIUS, UserSettings().temperatureUnit)
        assertEquals(ThemeMode.LIGHT, UserSettings().themeMode)
    }
}
