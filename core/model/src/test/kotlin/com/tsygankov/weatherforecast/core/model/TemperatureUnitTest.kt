package com.tsygankov.weatherforecast.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureUnitTest {
    @Test
    fun `zero Celsius equals thirty two Fahrenheit`() {
        assertEquals(32.0, 0.0.inUnit(TemperatureUnit.FAHRENHEIT), 0.0)
    }

    @Test
    fun `one hundred Celsius equals two hundred twelve Fahrenheit`() {
        assertEquals(212.0, 100.0.inUnit(TemperatureUnit.FAHRENHEIT), 0.0)
    }

    @Test
    fun `minus forty is the same in Celsius and Fahrenheit`() {
        assertEquals(-40.0, (-40.0).inUnit(TemperatureUnit.CELSIUS), 0.0)
        assertEquals(-40.0, (-40.0).inUnit(TemperatureUnit.FAHRENHEIT), 0.0)
    }

    @Test
    fun `rounding halves is symmetric around zero`() {
        assertEquals(2, 1.5.roundedInUnit(TemperatureUnit.CELSIUS))
        assertEquals(-2, (-1.5).roundedInUnit(TemperatureUnit.CELSIUS))
        assertEquals(1, (-17.5).roundedInUnit(TemperatureUnit.FAHRENHEIT))
        assertEquals(-1, (-18.055555555555557).roundedInUnit(TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `rounding uses converted value`() {
        assertEquals(68, 20.0.roundedInUnit(TemperatureUnit.FAHRENHEIT))
        assertEquals(-13, (-25.0).roundedInUnit(TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `unit exposes matching symbol`() {
        assertEquals("°C", TemperatureUnit.CELSIUS.symbol)
        assertEquals("°F", TemperatureUnit.FAHRENHEIT.symbol)
    }
}
