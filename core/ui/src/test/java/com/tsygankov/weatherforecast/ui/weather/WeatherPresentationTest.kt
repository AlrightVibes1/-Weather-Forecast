package com.tsygankov.weatherforecast.ui.weather

import android.app.Application
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.test.core.app.ApplicationProvider
import com.tsygankov.weatherforecast.ui.R
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WeatherPresentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every weather condition has one non-empty russian resource and a material icon`() {
        val expectedLabels = mapOf(
            WeatherCondition.CLEAR to "Ясно",
            WeatherCondition.PARTLY_CLOUDY to "Переменная облачность",
            WeatherCondition.CLOUDY to "Облачно",
            WeatherCondition.FOG to "Туман",
            WeatherCondition.DRIZZLE to "Морось",
            WeatherCondition.RAIN to "Дождь",
            WeatherCondition.SNOW to "Снег",
            WeatherCondition.THUNDERSTORM to "Гроза",
            WeatherCondition.UNKNOWN to "Неизвестно",
        )

        assertEquals(WeatherCondition.entries.toSet(), expectedLabels.keys)
        expectedLabels.forEach { (condition, expectedLabel) ->
            val presentation = condition.toWeatherPresentation()

            assertEquals(expectedLabel, context.getString(presentation.labelResource))
            assertFalse(presentation.icon.name.isBlank())
        }
    }

    @Test
    fun `unknown weather uses the neutral help icon and explicit label`() {
        val presentation = WeatherCondition.UNKNOWN.toWeatherPresentation()

        assertEquals(R.string.weather_unknown, presentation.labelResource)
        assertEquals(Icons.AutoMirrored.Outlined.HelpOutline.name, presentation.icon.name)
        assertEquals("Неизвестно", context.getString(presentation.labelResource))
    }

    @Test
    fun `shared temperature resource formats both supported units identically`() {
        assertEquals("12°C", context.getString(R.string.temperature_value, 12, "°C"))
        assertEquals("54°F", context.getString(R.string.temperature_value, 54, "°F"))
    }
}
