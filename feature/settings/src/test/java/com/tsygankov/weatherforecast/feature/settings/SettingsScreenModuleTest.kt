package com.tsygankov.weatherforecast.feature.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsScreenModuleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `confirmation calls clear only after user confirms`() {
        var clearCalls = 0
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(hasFavorites = true),
                    onUnitSelected = {},
                    onThemeSelected = {},
                    onClearFavorites = { clearCalls++ },
                    onBackClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Очистить избранное").performScrollTo().performClick()
        composeRule.onNodeWithText("Очистить избранное?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, clearCalls) }
        composeRule.onNodeWithText("Очистить").performClick()
        composeRule.runOnIdle { assertEquals(1, clearCalls) }
    }

    @Test
    fun `automatic location checkbox sends a disable action`() {
        var selected: Boolean? = null
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(),
                    onUnitSelected = {},
                    onThemeSelected = {},
                    onClearFavorites = {},
                    onBackClick = {},
                    onAutomaticLocationSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Автоматическое получение погоды из локации")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertEquals(false, selected) }
    }
}
