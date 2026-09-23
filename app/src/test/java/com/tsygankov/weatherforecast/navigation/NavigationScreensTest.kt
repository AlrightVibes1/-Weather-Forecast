package com.tsygankov.weatherforecast.navigation

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.tsygankov.weatherforecast.feature.forecast.ForecastScreen
import com.tsygankov.weatherforecast.feature.forecast.ForecastTab
import com.tsygankov.weatherforecast.feature.forecast.ForecastUiState
import com.tsygankov.weatherforecast.feature.home.HomeCityUiModel
import com.tsygankov.weatherforecast.feature.home.HomeScreen
import com.tsygankov.weatherforecast.feature.settings.SettingsScreen
import com.tsygankov.weatherforecast.feature.settings.SettingsUiState
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavigationScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `home sends the selected city id and opens settings through callbacks`() {
        var selectedCityId: Long? = null
        var settingsClicks = 0
        var backClicks = 0
        val city = HomeCityUiModel(
            id = EXPECTED_CITY_ID,
            name = "Москва",
            location = "Москва, Россия",
            temperature = null,
            temperatureUnitSymbol = "°C",
            weatherCondition = null,
            updatedAtMillis = null,
            timezone = "Europe/Moscow",
            isStale = false,
        )

        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                HomeScreen(
                    state = com.tsygankov.weatherforecast.feature.home.HomeUiState(
                        cityItems = listOf(city),
                    ),
                    onQueryChange = {},
                    onClearQuery = {},
                    onRetrySearch = {},
                    onAddCity = {},
                    onPreviewCity = {},
                    onCityClick = { selectedCityId = it },
                    onSettingsClick = { settingsClicks += 1 },
                    onBackClick = { backClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Города").assertIsDisplayed()
        assertHeading("Города")
        composeRule.onNodeWithContentDescription("Открыть настройки")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Назад")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("Москва").performClick()
        composeRule.onNodeWithContentDescription("Открыть настройки").performClick()

        composeRule.runOnIdle {
            assertEquals(EXPECTED_CITY_ID, selectedCityId)
            assertEquals(1, settingsClicks)
            assertEquals(1, backClicks)
        }
    }

    @Test
    fun `forecast shows the room backed city name and sends back callback`() {
        var backClicks = 0
        var cityTitleClicks = 0

        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                ForecastScreen(
                    state = ForecastUiState.InitialLoading(cityName = "Казань"),
                    selectedTab = ForecastTab.NOW,
                    onTabSelected = {},
                    onRetry = {},
                    onBackClick = { backClicks += 1 },
                    onCityTitleClick = { cityTitleClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Казань")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertHeading("Казань")
        composeRule.onNodeWithContentDescription("Назад")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, cityTitleClicks)
        }
    }

    @Test
    fun `settings has its own destination title and sends back callback`() {
        var backClicks = 0

        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(),
                    onUnitSelected = {},
                    onThemeSelected = {},
                    onClearFavorites = {},
                    onBackClick = { backClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        assertHeading("Настройки")
        composeRule.onNodeWithContentDescription("Назад")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
        }
    }

    private fun assertHeading(text: String) {
        val semantics = composeRule.onNodeWithText(text).fetchSemanticsNode().config
        assertTrue(semantics.contains(SemanticsProperties.Heading))
    }

    private companion object {
        const val EXPECTED_CITY_ID = 524901L
    }
}
