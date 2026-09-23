package com.tsygankov.weatherforecast.feature.settings

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tsygankov.weatherforecast.AppThemeViewModel
import com.tsygankov.weatherforecast.WeatherForecastAppRoute
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.data.SharedPreferencesSettingsRepository
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `screen offers only two units and two themes and updates selected rows`() {
        var state by mutableStateOf(SettingsUiState(hasFavorites = true))

        setScreen(
            stateProvider = { state },
            onUnitSelected = { unit ->
                state = state.copy(settings = state.settings.copy(temperatureUnit = unit))
            },
            onThemeSelected = { mode ->
                state = state.copy(settings = state.settings.copy(themeMode = mode))
            },
        )

        composeRule.onNodeWithText("Цельсий (°C)").assertIsSelected()
        composeRule.onNodeWithText("Фаренгейт (°F)").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("Фаренгейт (°F)").assertIsSelected()
        composeRule.onNodeWithText("Светлая").assertIsSelected()
        composeRule.onNodeWithText("Тёмная").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("Тёмная").assertIsSelected()
        composeRule.onNodeWithText("Как в системе").assertDoesNotExist()
    }

    @Test
    fun `cancel closes confirmation and never calls clear`() {
        var clearCalls = 0
        setScreen(
            stateProvider = { SettingsUiState(hasFavorites = true) },
            onClearFavorites = { clearCalls += 1 },
        )

        composeRule.onNodeWithText("Очистить избранное").performScrollTo().performClick()
        composeRule.onNodeWithText("Очистить избранное?").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.onNodeWithText("Очистить избранное?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, clearCalls) }
    }

    @Test
    fun `confirm closes dialog and calls clear exactly once`() {
        var clearCalls = 0
        setScreen(
            stateProvider = { SettingsUiState(hasFavorites = true) },
            onClearFavorites = { clearCalls += 1 },
        )

        composeRule.onNodeWithText("Очистить избранное").performScrollTo().performClick()
        composeRule.onNodeWithText("Очистить").performClick()

        composeRule.onNodeWithText("Очистить избранное?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, clearCalls) }
    }

    @Test
    fun `empty clearing and error states are explicit and keep the action safe`() {
        var state by mutableStateOf(SettingsUiState())
        setScreen(stateProvider = { state })

        composeRule.onNodeWithText("Очистить избранное").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Избранное уже пусто").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle {
            state = SettingsUiState(
                hasFavorites = true,
                clearState = ClearFavoritesUiState.CLEARING,
            )
        }
        composeRule.onNodeWithText("Очистить избранное").assertIsNotEnabled()
        composeRule.onNodeWithText("Удаляем сохранённые города…").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle {
            state = SettingsUiState(
                hasFavorites = true,
                clearState = ClearFavoritesUiState.ERROR,
                message = SettingsUserMessage.CLEAR_FAILED,
            )
        }
        composeRule.onNodeWithText("Очистить избранное").assertIsEnabled()
        composeRule.onNodeWithText(
            "Не удалось очистить избранное. Данные не были удалены.",
        ).assertIsDisplayed()
    }

    @Test
    fun `real settings route changes the root theme and persists both choices`() {
        val settingsRepository = SharedPreferencesSettingsRepository(preferences)
        val favoritesRepository = FakeFavoriteCitiesRepository()
        val settingsViewModel = SettingsViewModel(settingsRepository, favoritesRepository)
        val themeViewModel = AppThemeViewModel(settingsRepository)
        var observedBackground = Color.Unspecified

        composeRule.setContent {
            WeatherForecastAppRoute(
                viewModel = themeViewModel,
                dynamicColor = false,
            ) {
                val background = MaterialTheme.colorScheme.background
                SideEffect { observedBackground = background }
                SettingsRoute(
                    viewModel = settingsViewModel,
                    onBackClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Тёмная").performClick()
        composeRule.onNodeWithText("Фаренгейт (°F)").performClick()

        composeRule.runOnIdle {
            assertEquals(darkColorScheme().background, observedBackground)
            val restored = SharedPreferencesSettingsRepository(preferences).settings.value
            assertEquals(ThemeMode.DARK, restored.themeMode)
            assertEquals(TemperatureUnit.FAHRENHEIT, restored.temperatureUnit)
        }
    }

    @Test
    fun `route returns only after a confirmed successful clear`() {
        val favoritesRepository = FakeFavoriteCitiesRepository(listOf(MOSCOW))
        val settingsViewModel = SettingsViewModel(
            settingsRepository = SharedPreferencesSettingsRepository(preferences),
            favoritesRepository = favoritesRepository,
        )
        var backClicks = 0

        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                SettingsRoute(
                    viewModel = settingsViewModel,
                    onBackClick = { backClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Очистить избранное").performScrollTo().performClick()
        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.runOnIdle {
            assertEquals(0, favoritesRepository.clearCalls)
            assertEquals(0, backClicks)
        }

        composeRule.onNodeWithText("Очистить избранное").performScrollTo().performClick()
        composeRule.onNodeWithText("Очистить").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, favoritesRepository.clearCalls)
            assertEquals(emptyList<City>(), favoritesRepository.cities.value)
            assertEquals(1, backClicks)
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h240dp-land")
    fun `large font compact landscape keeps settings actions scrollable and large enough`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                WeatherForecastTheme(dynamicColor = false) {
                    SettingsScreen(
                        state = SettingsUiState(hasFavorites = true),
                        onUnitSelected = {},
                        onThemeSelected = {},
                        onClearFavorites = {},
                        onBackClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Цельсий (°C)")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Очистить избранное")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Назад")
            .assertHeightIsAtLeast(48.dp)
    }

    private fun setScreen(
        stateProvider: () -> SettingsUiState,
        onUnitSelected: (TemperatureUnit) -> Unit = {},
        onThemeSelected: (ThemeMode) -> Unit = {},
        onClearFavorites: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                SettingsScreen(
                    state = stateProvider(),
                    onUnitSelected = onUnitSelected,
                    onThemeSelected = onThemeSelected,
                    onClearFavorites = onClearFavorites,
                    onBackClick = {},
                )
            }
        }
    }

    private class FakeFavoriteCitiesRepository(
        initialCities: List<City> = emptyList(),
    ) : FavoriteCitiesRepository {
        val cities = MutableStateFlow(initialCities)
        var clearCalls = 0

        override fun observeCities(): Flow<List<City>> = cities
        override fun observeCity(id: Long): Flow<City?> = MutableStateFlow(null)
        override fun observeForecast(id: Long): Flow<Forecast?> = MutableStateFlow(null)
        override suspend fun getCity(id: Long): City? = null
        override suspend fun add(city: CitySearchResult): AddFavoriteResult = error("Not used")
        override suspend fun clear() {
            clearCalls += 1
            cities.value = emptyList()
        }
        override suspend fun refreshCity(cityId: Long): RefreshResult = RefreshResult.EmptyFavorites
        override suspend fun refreshAll(): RefreshResult = RefreshResult.EmptyFavorites
    }

    private companion object {
        const val PREFERENCES_NAME = "wf_017_settings_screen_test"
        val MOSCOW = City(
            id = 524901L,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.7558,
            longitude = 37.6173,
            timezone = "Europe/Moscow",
        )
    }
}
