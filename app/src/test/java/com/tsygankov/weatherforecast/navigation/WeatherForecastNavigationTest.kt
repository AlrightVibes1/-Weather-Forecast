package com.tsygankov.weatherforecast.navigation

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WeatherForecastNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `weather starts once and system back returns from settings without a duplicate`() {
        lateinit var navController: NavHostController
        lateinit var backDispatcher: OnBackPressedDispatcher

        composeRule.setContent {
            navController = rememberNavController()
            backDispatcher = requireNotNull(
                LocalOnBackPressedDispatcherOwner.current,
            ).onBackPressedDispatcher
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, onSettingsClick ->
                        Column(modifier = Modifier.testTag(WEATHER_TAG)) {
                            Button(
                                onClick = onCitiesClick,
                                modifier = Modifier.testTag(CITIES_ACTION_TAG),
                            ) { Text("cities") }
                            Button(
                                onClick = onSettingsClick,
                                modifier = Modifier.testTag(SETTINGS_ACTION_TAG),
                            ) { Text("settings") }
                        }
                    },
                    homeDestination = { _, _, _, onSettingsClick, _ ->
                        Column(modifier = Modifier.testTag(HOME_TAG)) {
                            Button(
                                onClick = onSettingsClick,
                            ) {
                                Text("settings")
                            }
                        }
                    },
                    forecastDestination = { _, _, _ ->
                        Text("forecast", modifier = Modifier.testTag(FORECAST_TAG))
                    },
                    settingsDestination = {
                        Text("settings", modifier = Modifier.testTag(SETTINGS_TAG))
                    },
                )
            }
        }

        composeRule.onNodeWithTag(WEATHER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()

        composeRule.runOnIdle {
            backDispatcher.onBackPressed()
        }

        composeRule.onNodeWithTag(WEATHER_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(WEATHER_START_ROUTE, navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(HOME_TAG).assertIsDisplayed()
        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(WEATHER_TAG).assertIsDisplayed()
        composeRule.runOnIdle { assertNull(navController.previousBackStackEntry) }
    }

    @Test
    fun `city callback sends only the long id to forecast and back returns home`() {
        lateinit var navController: NavHostController
        var receivedCityId: Long? = null

        composeRule.setContent {
            navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, _ ->
                        Button(
                            onClick = onCitiesClick,
                            modifier = Modifier.testTag(CITIES_ACTION_TAG),
                        ) { Text("cities") }
                    },
                    homeDestination = { onCityClick, _, _, _, _ ->
                        Button(
                            onClick = { onCityClick(EXPECTED_CITY_ID) },
                            modifier = Modifier.testTag(CITY_TAG),
                        ) {
                            Text("city")
                        }
                    },
                    forecastDestination = { cityId, onBackClick, _ ->
                        SideEffect {
                            receivedCityId = cityId
                        }
                        Button(
                            onClick = onBackClick,
                            modifier = Modifier.testTag(FORECAST_TAG),
                        ) {
                            Text("back")
                        }
                    },
                    settingsDestination = {},
                )
            }
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(CITY_TAG).performClick()
        composeRule.onNodeWithTag(FORECAST_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(EXPECTED_CITY_ID, receivedCityId)
            assertEquals(FORECAST_ROUTE, navController.currentDestination?.route)
            assertEquals(
                EXPECTED_CITY_ID,
                navController.currentBackStackEntry
                    ?.arguments
                    ?.getLong(FORECAST_CITY_ID_ARGUMENT),
            )
        }

        composeRule.onNodeWithTag(FORECAST_TAG).performClick()

        composeRule.onNodeWithTag(CITY_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(HOME_ROUTE, navController.currentDestination?.route)
            assertEquals(WEATHER_START_ROUTE, navController.previousBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun `current location shortcut returns to the existing weather start`() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, _ ->
                        Button(
                            onClick = onCitiesClick,
                            modifier = Modifier.testTag(CITIES_ACTION_TAG),
                        ) { Text("cities") }
                    },
                    homeDestination = { _, _, onCurrentLocationClick, _, _ ->
                        Button(
                            onClick = onCurrentLocationClick,
                            modifier = Modifier.testTag(CURRENT_LOCATION_TAG),
                        ) { Text("current") }
                    },
                    forecastDestination = { _, _, _ -> },
                    settingsDestination = {},
                )
            }
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(CURRENT_LOCATION_TAG).performClick()
        composeRule.onNodeWithTag(CITIES_ACTION_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(WEATHER_START_ROUTE, navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test
    fun `city title opens cities and app back returns to the forecast`() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, _ ->
                        Button(
                            onClick = onCitiesClick,
                            modifier = Modifier.testTag(CITIES_ACTION_TAG),
                        ) { Text("cities") }
                    },
                    homeDestination = { onCityClick, _, _, _, onBackClick ->
                        Column {
                            Button(
                                onClick = { onCityClick(EXPECTED_CITY_ID) },
                                modifier = Modifier.testTag(CITY_TAG),
                            ) { Text("city") }
                            Button(
                                onClick = onBackClick,
                                modifier = Modifier.testTag(HOME_BACK_TAG),
                            ) { Text("back") }
                        }
                    },
                    forecastDestination = { _, _, onCitiesClick ->
                        var selectedTab by rememberSaveable { mutableStateOf("now") }
                        Column {
                            Button(
                                onClick = { selectedTab = "month" },
                                modifier = Modifier.testTag(FORECAST_MONTH_TAB_TAG),
                            ) { Text("month") }
                            Button(
                                onClick = onCitiesClick,
                                modifier = Modifier.testTag(FORECAST_CITY_TITLE_TAG),
                            ) { Text("choose city") }
                            Text(
                                selectedTab,
                                modifier = Modifier.testTag("$FORECAST_TAB_STATE_PREFIX$selectedTab"),
                            )
                        }
                    },
                    settingsDestination = {},
                )
            }
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(CITY_TAG).performClick()
        composeRule.onNodeWithTag(FORECAST_MONTH_TAB_TAG).performClick()
        composeRule.onNodeWithTag("${FORECAST_TAB_STATE_PREFIX}month").assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_CITY_TITLE_TAG).performClick()
        composeRule.onNodeWithTag(CITY_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(HOME_ROUTE, navController.currentDestination?.route)
        }
        composeRule.onNodeWithTag(HOME_BACK_TAG).performClick()
        composeRule.onNodeWithTag(FORECAST_CITY_TITLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("${FORECAST_TAB_STATE_PREFIX}month").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(FORECAST_ROUTE, navController.currentDestination?.route)
        }
    }

    @Test
    fun `search result opens temporary forecast with stable id and back returns home`() {
        lateinit var navController: NavHostController
        var receivedCity: CitySearchResult? = null

        composeRule.setContent {
            navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, _ ->
                        Button(
                            onClick = onCitiesClick,
                            modifier = Modifier.testTag(CITIES_ACTION_TAG),
                        ) { Text("cities") }
                    },
                    homeDestination = { _, onPreviewCity, _, _, _ ->
                        Button(
                            onClick = { onPreviewCity(PREVIEW_CITY) },
                            modifier = Modifier.testTag(PREVIEW_CITY_TAG),
                        ) { Text("preview") }
                    },
                    forecastDestination = { _, _, _ -> },
                    temporaryForecastDestination = { city, onBackClick, _ ->
                        SideEffect { receivedCity = city }
                        Button(
                            onClick = onBackClick,
                            modifier = Modifier.testTag(TEMPORARY_FORECAST_TAG),
                        ) { Text("back") }
                    },
                    settingsDestination = {},
                )
            }
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(PREVIEW_CITY_TAG).performClick()
        composeRule.onNodeWithTag(TEMPORARY_FORECAST_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(PREVIEW_CITY, receivedCity)
            assertEquals(TEMPORARY_FORECAST_ROUTE, navController.currentDestination?.route)
            assertEquals(
                PREVIEW_CITY.id,
                navController.currentBackStackEntry
                    ?.arguments
                    ?.getLong(FORECAST_CITY_ID_ARGUMENT),
            )
        }

        composeRule.onNodeWithTag(TEMPORARY_FORECAST_TAG).performClick()
        composeRule.onNodeWithTag(PREVIEW_CITY_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(HOME_ROUTE, navController.currentDestination?.route)
        }
    }

    @Test
    fun `temporary city survives saved state restoration without becoming a route object`() {
        val restorationTester = StateRestorationTester(composeRule)
        var receivedCity: CitySearchResult? = null

        restorationTester.setContent {
            val navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, _ ->
                        Button(
                            onClick = onCitiesClick,
                            modifier = Modifier.testTag(CITIES_ACTION_TAG),
                        ) { Text("cities") }
                    },
                    homeDestination = { _, onPreviewCity, _, _, _ ->
                        Button(
                            onClick = { onPreviewCity(PREVIEW_CITY) },
                            modifier = Modifier.testTag(PREVIEW_CITY_TAG),
                        ) { Text("preview") }
                    },
                    forecastDestination = { _, _, _ -> },
                    temporaryForecastDestination = { city, _, _ ->
                        SideEffect { receivedCity = city }
                        Text("temporary", modifier = Modifier.testTag(TEMPORARY_FORECAST_TAG))
                    },
                    settingsDestination = {},
                )
            }
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(PREVIEW_CITY_TAG).performClick()
        composeRule.onNodeWithTag(TEMPORARY_FORECAST_TAG).assertIsDisplayed()

        receivedCity = null
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(TEMPORARY_FORECAST_TAG).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(PREVIEW_CITY, receivedCity) }
    }

    @Test
    fun `interrupted forward and back animations settle on one home destination`() {
        composeRule.mainClock.autoAdvance = false
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(
                    navController = navController,
                    weatherDestination = { onCitiesClick, _ ->
                        Button(
                            onClick = onCitiesClick,
                            modifier = Modifier.testTag(CITIES_ACTION_TAG),
                        ) { Text("cities") }
                    },
                    homeDestination = { _, _, _, _, _ ->
                        Text("home", modifier = Modifier.testTag(HOME_TAG))
                    },
                    forecastDestination = { _, _, _ ->
                        Text("forecast", modifier = Modifier.testTag(FORECAST_TAG))
                    },
                    settingsDestination = {},
                )
            }
        }

        composeRule.onNodeWithTag(CITIES_ACTION_TAG).performClick()
        composeRule.runOnIdle {
            navController.navigateToForecast(EXPECTED_CITY_ID)
        }
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.runOnIdle {
            navController.navigateUp()
        }
        composeRule.mainClock.advanceTimeBy(NAVIGATION_ANIMATION_DURATION_MILLIS * 2L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HOME_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_TAG).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(HOME_ROUTE, navController.currentDestination?.route)
            assertEquals(WEATHER_START_ROUTE, navController.previousBackStackEntry?.destination?.route)
        }
    }

    private companion object {
        const val EXPECTED_CITY_ID = 524901L
        const val HOME_TAG = "home_destination"
        const val WEATHER_TAG = "weather_start_destination"
        const val CITIES_ACTION_TAG = "cities_navigation_action"
        const val CITY_TAG = "city_navigation_action"
        const val PREVIEW_CITY_TAG = "preview_city_navigation_action"
        const val CURRENT_LOCATION_TAG = "current_location_navigation_action"
        const val FORECAST_TAG = "forecast_destination"
        const val FORECAST_CITY_TITLE_TAG = "forecast_city_title"
        const val FORECAST_MONTH_TAB_TAG = "forecast_month_tab"
        const val FORECAST_TAB_STATE_PREFIX = "forecast_tab_state_"
        const val HOME_BACK_TAG = "home_back"
        const val TEMPORARY_FORECAST_TAG = "temporary_forecast_destination"
        const val SETTINGS_ACTION_TAG = "settings_navigation_action"
        const val SETTINGS_TAG = "settings_destination"
        val PREVIEW_CITY = CitySearchResult(
            id = 524901L,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.75,
            longitude = 37.61,
            timezone = "Europe/Moscow",
        )
    }
}
