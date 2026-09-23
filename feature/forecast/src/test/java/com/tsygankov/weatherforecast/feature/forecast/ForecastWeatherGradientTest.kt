package com.tsygankov.weatherforecast.feature.forecast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastWeatherGradientTest {
    @Test
    fun `content refresh and cached error keep current weather while states without data stay neutral`() {
        val content = ForecastContentUiModel(
            current = CurrentForecastUiModel(
                temperature = 8,
                apparentTemperature = 6,
                humidityPercent = 80,
                pressure = "1005",
                windSpeed = "12",
                condition = WeatherCondition.RAIN,
            ),
            hourly = emptyList(),
            weekDays = emptyList(),
            monthDays = emptyList(),
        )

        assertEquals(
            WeatherCondition.RAIN,
            ForecastUiState.Content(
                cityName = "Омск",
                content = content,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ).backgroundWeatherCondition(),
        )
        assertEquals(
            WeatherCondition.RAIN,
            ForecastUiState.RefreshingWithContent(
                cityName = "Омск",
                content = content,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ).backgroundWeatherCondition(),
        )
        assertEquals(
            WeatherCondition.RAIN,
            ForecastUiState.ErrorWithCache(
                cityName = "Омск",
                content = content,
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ).backgroundWeatherCondition(),
        )
        assertNull(ForecastUiState.InitialLoading().backgroundWeatherCondition())
        assertNull(
            ForecastUiState.ErrorWithoutCache(
                cityName = "Омск",
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ).backgroundWeatherCondition(),
        )
    }

    @Test
    fun `clear cloudy rain snow and thunderstorm have visibly different palettes`() {
        val clear = forecastGradientColors(WeatherCondition.CLEAR, isDarkTheme = false)
        val cloudy = forecastGradientColors(WeatherCondition.CLOUDY, isDarkTheme = false)
        val rain = forecastGradientColors(WeatherCondition.RAIN, isDarkTheme = false)
        val snow = forecastGradientColors(WeatherCondition.SNOW, isDarkTheme = false)
        val thunderstorm = forecastGradientColors(
            WeatherCondition.THUNDERSTORM,
            isDarkTheme = false,
        )

        assertEquals(5, setOf(clear, cloudy, rain, snow, thunderstorm).size)
        assertNotEquals(clear, rain)
    }

    @Test
    fun `light weather palettes communicate conditions through color`() {
        val clear = forecastGradientColors(WeatherCondition.CLEAR, isDarkTheme = false)
        val cloudy = forecastGradientColors(WeatherCondition.CLOUDY, isDarkTheme = false)
        val rain = forecastGradientColors(WeatherCondition.RAIN, isDarkTheme = false)
        val snow = forecastGradientColors(WeatherCondition.SNOW, isDarkTheme = false)
        val thunder = forecastGradientColors(WeatherCondition.THUNDERSTORM, isDarkTheme = false)

        assertTrue(clear.all { color -> color.red > color.blue && color.green > color.blue })
        assertTrue(cloudy.all { color -> channelSpread(color) < 0.10f })
        assertTrue(rain.all { color -> color.blue > color.red })
        assertTrue(snow.all { color -> color.blue > color.red && color.luminance() > 0.50f })
        assertTrue(thunder.all { color -> color.blue > color.green && color.red > color.green })
    }

    @Test
    fun `every weather and neutral state has three opaque colors with readable content`() {
        val conditions = WeatherCondition.entries.map<WeatherCondition, WeatherCondition?> { it } + null

        conditions.forEach { condition ->
            listOf(false, true).forEach { dark ->
                val colors = forecastGradientColors(condition, isDarkTheme = dark)
                val visual = forecastVisualColors(condition, isDarkTheme = dark)
                assertEquals(3, colors.size)
                colors.forEach { background ->
                    assertEquals(1f, background.alpha)
                    assertTrue(
                        "$condition dark=$dark contrast=${contrastRatio(visual.primaryText, background)}",
                        contrastRatio(visual.primaryText, background) >= MINIMUM_TEXT_CONTRAST,
                    )
                    val renderedSecondary = visual.secondaryText.compositeOver(background)
                    assertTrue(
                        "$condition dark=$dark secondary=${contrastRatio(renderedSecondary, background)}",
                        contrastRatio(renderedSecondary, background) >= MINIMUM_SECONDARY_CONTRAST,
                    )
                }
            }
        }
    }

    @Test
    fun `dark theme keeps every weather palette darker than its light partner`() {
        val conditions = WeatherCondition.entries.map<WeatherCondition, WeatherCondition?> { it } + null

        conditions.forEach { condition ->
            val lightAverage = forecastGradientColors(condition, isDarkTheme = false)
                .map(Color::luminance)
                .average()
            val darkAverage = forecastGradientColors(condition, isDarkTheme = true)
                .map(Color::luminance)
                .average()

            assertTrue("$condition light=$lightAverage dark=$darkAverage", darkAverage < lightAverage)
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun channelSpread(color: Color): Float =
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)

    private companion object {
        const val MINIMUM_TEXT_CONTRAST = 4.5f
        const val MINIMUM_SECONDARY_CONTRAST = 3f
    }
}
