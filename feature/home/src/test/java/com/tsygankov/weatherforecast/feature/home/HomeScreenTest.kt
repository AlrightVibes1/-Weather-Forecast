package com.tsygankov.weatherforecast.feature.home

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.GeoCoordinates
import com.tsygankov.weatherforecast.domain.repository.RefreshFailure
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.TemporaryRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `current location city is shown above favorites and opens weather start`() {
        var currentLocationClicks = 0
        setScreen(
            state = HomeUiState(
                cityItems = listOf(homeCity(name = "Москва")),
                showCurrentLocation = true,
                currentLocationCityName = "Омск",
            ),
            onCurrentLocationClick = { currentLocationClicks += 1 },
        )

        composeRule.onNodeWithText("Города").assertIsDisplayed()
        composeRule.onNodeWithText("Текущее местоположение").assertIsDisplayed()
        composeRule.onNodeWithText("Омск").assertIsDisplayed()
        composeRule.onNodeWithText("Избранные города").assertIsDisplayed()
        composeRule.onNodeWithTag(CURRENT_LOCATION_TEST_TAG)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle { assertEquals(1, currentLocationClicks) }
    }

    @Test
    fun `current location shortcut is hidden when automatic mode is disabled`() {
        setScreen(
            HomeUiState(
                showCurrentLocation = false,
                currentLocationCityName = "Омск",
            ),
        )

        composeRule.onNodeWithText("Текущее местоположение").assertDoesNotExist()
        composeRule.onNodeWithText("Омск").assertDoesNotExist()
    }

    @Test
    fun `empty state keeps the russian search field available`() {
        setScreen(HomeUiState())

        composeRule.onNodeWithText("Поиск города").assertIsDisplayed()
        composeRule.onNodeWithText("Избранных городов пока нет").assertIsDisplayed()
        composeRule.onNodeWithText("Найдите и добавьте первый город").assertIsDisplayed()
    }

    @Test
    fun `search field forwards input and clear callbacks`() {
        var changedQuery: String? = null
        var clearClicks = 0
        setScreen(
            state = HomeUiState(query = "Мос"),
            onQueryChange = { changedQuery = it },
            onClearQuery = { clearClicks += 1 },
        )

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Москва")
        composeRule.onNodeWithContentDescription("Очистить поиск")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Москва", changedQuery)
            assertEquals(1, clearClicks)
        }
    }

    @Test
    fun `loading empty and safe error with retry are visible`() {
        var state by mutableStateOf(
            HomeUiState(
                query = "Москва",
                search = SearchUiState.Loading("Москва"),
            ),
        )
        var retryClicks = 0
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                HomeScreen(
                    state = state,
                    onQueryChange = {},
                    onClearQuery = {},
                    onRetrySearch = { retryClicks += 1 },
                    onAddCity = {},
                    onCityClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Ищем города…").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(search = SearchUiState.Empty("Москва"))
        }
        composeRule.onNodeWithText("Ничего не найдено").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(
                search = SearchUiState.Error(
                    query = "Москва",
                    message = HomeUserMessage.SEARCH_FAILED,
                ),
            )
        }
        composeRule.onNodeWithText(
            "Не удалось найти города. Проверьте подключение к интернету.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("HTTP 500: internal-secret").assertDoesNotExist()
        composeRule.onNodeWithText("Повторить").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(1, retryClicks) }
    }

    @Test
    fun `same named search results show locations and send the exact selected city`() {
        var selected: CitySearchResult? = null
        setScreen(
            state = HomeUiState(
                query = "Москва",
                search = SearchUiState.Results(
                    query = "Москва",
                    cities = listOf(MOSCOW, MOSCOW_IDAHO),
                ),
            ),
            onAddCity = { selected = it },
        )

        composeRule.onNodeWithText("Результаты поиска").assertIsDisplayed()
        composeRule.onNodeWithText("Москва, Россия").assertIsDisplayed()
        composeRule.onNodeWithTag("$SEARCH_ADD_TEST_TAG_PREFIX${MOSCOW_IDAHO.id}")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(MOSCOW_IDAHO, selected) }
    }

    @Test
    fun `adding state disables every result and marks the selected city`() {
        var addClicks = 0
        val state = HomeUiState(
            query = "Москва",
            search = SearchUiState.Results(
                query = "Москва",
                cities = listOf(MOSCOW, MOSCOW_IDAHO),
            ),
            adding = AddCityUiState.Adding(MOSCOW.id),
        )
        setScreen(state = state, onAddCity = { addClicks += 1 })

        composeRule.onNodeWithText("Добавляем…").assertIsDisplayed()
        composeRule.onNodeWithTag("$SEARCH_ADD_TEST_TAG_PREFIX${MOSCOW.id}")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("$SEARCH_ADD_TEST_TAG_PREFIX${MOSCOW_IDAHO.id}")
            .assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, addClicks) }
    }

    @Test
    fun `opening a search result is separate from adding it to favorites`() {
        var previewed: CitySearchResult? = null
        var addClicks = 0
        setScreen(
            state = HomeUiState(
                query = "Москва",
                search = SearchUiState.Results(
                    query = "Москва",
                    cities = listOf(MOSCOW),
                ),
            ),
            onAddCity = { addClicks += 1 },
            onPreviewCity = { previewed = it },
        )

        composeRule.onNodeWithTag("$SEARCH_PREVIEW_TEST_TAG_PREFIX${MOSCOW.id}")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(MOSCOW, previewed)
            assertEquals(0, addClicks)
        }
    }

    @Test
    fun `network search error is safe and retry shows results`() {
        var attempt = 0
        val searchRepository = FakeWeatherSearchRepository {
            attempt += 1
            if (attempt == 1) {
                Result.failure(IllegalStateException("HTTP 500: internal-secret"))
            } else {
                Result.success(listOf(MOSCOW))
            }
        }
        val viewModel = createViewModel(searchRepository = searchRepository)
        setRoute(viewModel)

        composeRule.onNode(hasSetTextAction()).performTextInput("Москва")
        waitForText("Не удалось найти города. Проверьте подключение к интернету.")
        composeRule.onNodeWithText("HTTP 500: internal-secret").assertDoesNotExist()
        composeRule.onNodeWithText("Повторить").performClick()
        waitForText("Результаты поиска")

        composeRule.onNodeWithText("Москва, Россия").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("Москва", "Москва"), searchRepository.queries)
        }
    }

    @Test
    fun `successful add clears search and shows the favorite from repository flow`() {
        val favoritesRepository = FakeFavoriteCitiesRepository(publishCityOnAdd = true)
        val viewModel = createViewModel(
            favoritesRepository = favoritesRepository,
            searchRepository = FakeWeatherSearchRepository { Result.success(listOf(MOSCOW)) },
        )
        setRoute(viewModel)

        composeRule.onNode(hasSetTextAction()).performTextInput("Москва")
        waitForText("Результаты поиска")
        composeRule.onNodeWithText("Добавить").performClick()

        waitForText("Избранные города")
        composeRule.onNodeWithText("Погода ещё не загружена").assertIsDisplayed()
        composeRule.onNodeWithText("Результаты поиска").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Очистить поиск").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(MOSCOW), favoritesRepository.addedCities)
        }
    }

    @Test
    fun `failed first forecast keeps added city clears search and shows snackbar`() {
        val firstRefreshFailure = RefreshResult.TemporaryFailure(
            listOf(
                RefreshFailure.Temporary(
                    cityId = MOSCOW.id,
                    reason = TemporaryRefreshFailureReason.NETWORK,
                ),
            ),
        )
        val favoritesRepository = FakeFavoriteCitiesRepository(
            addResult = firstRefreshFailure,
            publishCityOnAdd = true,
        )
        val viewModel = createViewModel(
            favoritesRepository = favoritesRepository,
            searchRepository = FakeWeatherSearchRepository { Result.success(listOf(MOSCOW)) },
        )
        setRoute(viewModel)

        composeRule.onNode(hasSetTextAction()).performTextInput("Москва")
        waitForText("Результаты поиска")
        composeRule.onNodeWithText("Добавить").performClick()

        waitForText("Город добавлен, но погоду пока не удалось обновить")
        composeRule.onNodeWithText("Избранные города").assertIsDisplayed()
        composeRule.onNodeWithText("Погода ещё не загружена").assertIsDisplayed()
        composeRule.onNodeWithText("Результаты поиска").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Очистить поиск").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(MOSCOW), favoritesRepository.addedCities)
        }
    }

    @Test
    fun `add storage error keeps search and shows only safe snackbar`() {
        val favoritesRepository = FakeFavoriteCitiesRepository(
            addFailure = IllegalStateException("database internals"),
        )
        val viewModel = createViewModel(
            favoritesRepository = favoritesRepository,
            searchRepository = FakeWeatherSearchRepository { Result.success(listOf(MOSCOW)) },
        )
        setRoute(viewModel)

        composeRule.onNode(hasSetTextAction()).performTextInput("Москва")
        waitForText("Результаты поиска")
        composeRule.onNodeWithText("Добавить").performClick()

        waitForText("Не удалось добавить город")
        composeRule.onNodeWithText("database internals").assertDoesNotExist()
        composeRule.onNodeWithText("Результаты поиска").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Очистить поиск").assertIsDisplayed()
    }

    @Test
    fun `repository flow changes empty state to card and settings change celsius to fahrenheit`() {
        val favoritesRepository = FakeFavoriteCitiesRepository()
        val settingsRepository = FakeSettingsRepository()
        val viewModel = createViewModel(
            favoritesRepository = favoritesRepository,
            settingsRepository = settingsRepository,
        )
        var selectedCityId: Long? = null
        setRoute(viewModel, onCityClick = { selectedCityId = it })

        composeRule.onNodeWithText("Избранных городов пока нет").assertIsDisplayed()
        composeRule.runOnIdle {
            favoritesRepository.cities.value = listOf(
                city(
                    temperatureCelsius = 10.0,
                    weatherCode = 61,
                    updatedAt = NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS + 1L,
                ),
            )
        }

        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.onNodeWithText("Москва, Россия").assertIsDisplayed()
        composeRule.onNodeWithText("10°C").assertIsDisplayed()
        composeRule.onNodeWithText("Дождь").assertIsDisplayed()
        composeRule.onNodeWithText("Данные устарели").assertDoesNotExist()
        composeRule.onNodeWithText("Обновлено:", substring = true).assertIsDisplayed()

        composeRule.runOnIdle {
            favoritesRepository.cities.value = listOf(
                city(
                    temperatureCelsius = 10.0,
                    weatherCode = 61,
                    updatedAt = NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS,
                ),
            )
        }
        composeRule.onNodeWithText("Данные устарели").assertIsDisplayed()

        val clickableCard = composeRule.onNode(
            hasClickAction() and hasText("Москва", substring = true),
        )
        clickableCard.assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle {
            assertEquals(CITY_ID, selectedCityId)
            settingsRepository.settingsState.value = UserSettings(
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                themeMode = ThemeMode.LIGHT,
            )
        }

        composeRule.onNodeWithText("50°F").assertIsDisplayed()
        composeRule.onNodeWithText("10°C").assertDoesNotExist()
    }

    @Test
    fun `card without weather cache explains missing data and never invents zero degrees`() {
        setScreen(
            HomeUiState(
                cityItems = listOf(
                    HomeCityUiModel(
                        id = CITY_ID,
                        name = "Москва",
                        location = "Россия",
                        temperature = null,
                        temperatureUnitSymbol = "°C",
                        weatherCondition = null,
                        updatedAtMillis = null,
                        timezone = "Europe/Moscow",
                        isStale = false,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Погода ещё не загружена").assertIsDisplayed()
        composeRule.onNodeWithText("0°C").assertDoesNotExist()
        composeRule.onNodeWithText("Данные устарели").assertDoesNotExist()
    }

    @Test
    fun `compact refresh progress keeps cached weather visible`() {
        setScreen(
            HomeUiState(
                refreshing = true,
                cityItems = listOf(
                    HomeCityUiModel(
                        id = CITY_ID,
                        name = "Москва",
                        location = "Москва, Россия",
                        temperature = 10,
                        temperatureUnitSymbol = "°C",
                        weatherCondition = WeatherCondition.RAIN,
                        updatedAtMillis = NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS,
                        timezone = "Europe/Moscow",
                        isStale = true,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Обновляем погоду…").assertIsDisplayed()
        composeRule.onNodeWithText("10°C").assertIsDisplayed()
        composeRule.onNodeWithText("Дождь").assertIsDisplayed()
        composeRule.onNodeWithText("Данные устарели").assertIsDisplayed()
    }

    @Test
    fun `home gradient responds to theme while cached content and actions remain available`() {
        var darkTheme by mutableStateOf(false)
        var settingsClicks = 0
        val state = HomeUiState(
            cityItems = listOf(
                HomeCityUiModel(
                    id = CITY_ID,
                    name = "Москва",
                    location = "Россия",
                    temperature = 10,
                    temperatureUnitSymbol = "°C",
                    weatherCondition = WeatherCondition.RAIN,
                    updatedAtMillis = NOW_MILLIS,
                    timezone = "Europe/Moscow",
                    isStale = true,
                ),
            ),
        )
        composeRule.setContent {
            WeatherForecastTheme(darkTheme = darkTheme, dynamicColor = false) {
                HomeScreen(
                    state = state,
                    onQueryChange = {},
                    onClearQuery = {},
                    onRetrySearch = {},
                    onAddCity = {},
                    onCityClick = {},
                    onSettingsClick = { settingsClicks += 1 },
                )
            }
        }

        assertTrue(homeGradientFor(isDarkTheme = false) != homeGradientFor(isDarkTheme = true))
        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.runOnIdle { darkTheme = true }
        composeRule.onNodeWithText("Поиск города").assertIsDisplayed()
        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.onNodeWithText("10°C").assertIsDisplayed()
        composeRule.onNodeWithText("Данные устарели").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Открыть настройки")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, settingsClicks) }
    }

    @Test
    fun `route refreshes on lifecycle resume but never on recomposition`() {
        val lifecycleOwner = MutableLifecycleOwner()
        composeRule.runOnIdle {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handle(Lifecycle.Event.ON_START)
        }
        val failure = RefreshResult.TemporaryFailure(
            listOf(
                RefreshFailure.Temporary(
                    cityId = CITY_ID,
                    reason = TemporaryRefreshFailureReason.NETWORK,
                ),
            ),
        )
        val favoritesRepository = FakeFavoriteCitiesRepository(
            refreshHandler = { failure },
        ).apply {
            cities.value = listOf(
                city(
                    temperatureCelsius = 10.0,
                    weatherCode = 61,
                    updatedAt = NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS,
                ),
            )
        }
        val viewModel = createViewModel(favoritesRepository = favoritesRepository)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                WeatherForecastTheme(dynamicColor = false) {
                    HomeRoute(
                        onCityClick = {},
                        onSettingsClick = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(favoritesRepository.refreshedCityIds.isEmpty())
            viewModel.updateQuery("М")
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(favoritesRepository.refreshedCityIds.isEmpty())
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        waitForText("Не удалось обновить погоду. Сохранённые данные остаются доступны")
        composeRule.onNodeWithText("10°C").assertIsDisplayed()
        composeRule.onNodeWithText("Данные устарели").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(CITY_ID), favoritesRepository.refreshedCityIds)
            viewModel.updateQuery("Мо")
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(CITY_ID), favoritesRepository.refreshedCityIds)
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            favoritesRepository.refreshedCityIds.size == 2
        }
        composeRule.runOnIdle {
            assertEquals(listOf(CITY_ID, CITY_ID), favoritesRepository.refreshedCityIds)
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h240dp-land")
    fun `large font list remains scrollable to the last city`() {
        val cities = List(15) { index ->
            HomeCityUiModel(
                id = index.toLong(),
                name = "Город $index",
                location = "Регион",
                temperature = null,
                temperatureUnitSymbol = "°C",
                weatherCondition = null,
                updatedAtMillis = null,
                timezone = "UTC",
                isStale = false,
            )
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                WeatherForecastTheme(dynamicColor = false) {
                    HomeScreen(
                        state = HomeUiState(cityItems = cities),
                        onQueryChange = {},
                        onClearQuery = {},
                        onRetrySearch = {},
                        onAddCity = {},
                        onCityClick = {},
                        onSettingsClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(HOME_CITY_LIST_TEST_TAG)
            .performScrollToIndex(16)
        composeRule.onNodeWithText("Город 14").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Открыть настройки")
            .assertHeightIsAtLeast(48.dp)
    }

    private fun setScreen(
        state: HomeUiState,
        onQueryChange: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onRetrySearch: () -> Unit = {},
        onAddCity: (CitySearchResult) -> Unit = {},
        onPreviewCity: (CitySearchResult) -> Unit = {},
        onCurrentLocationClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                HomeScreen(
                    state = state,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onRetrySearch = onRetrySearch,
                    onAddCity = onAddCity,
                    onPreviewCity = onPreviewCity,
                    onCurrentLocationClick = onCurrentLocationClick,
                    onCityClick = {},
                    onSettingsClick = {},
                )
            }
        }
    }

    private fun setRoute(
        viewModel: HomeViewModel,
        onCityClick: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                HomeRoute(
                    onCityClick = onCityClick,
                    onSettingsClick = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun createViewModel(
        favoritesRepository: FakeFavoriteCitiesRepository = FakeFavoriteCitiesRepository(),
        searchRepository: FakeWeatherSearchRepository = FakeWeatherSearchRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ) = HomeViewModel(
        favoritesRepository = favoritesRepository,
        weatherRepository = searchRepository,
        deviceLocationRepository = FakeDeviceLocationRepository(),
        settingsRepository = settingsRepository,
        clock = AppClock { NOW_MILLIS },
    )

    private fun homeCity(name: String) = HomeCityUiModel(
        id = CITY_ID,
        name = name,
        location = "Россия",
        temperature = 10,
        temperatureUnitSymbol = "°C",
        weatherCondition = WeatherCondition.CLEAR,
        updatedAtMillis = NOW_MILLIS,
        timezone = "Europe/Moscow",
        isStale = false,
    )

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class FakeFavoriteCitiesRepository(
        private val addResult: RefreshResult = RefreshResult.Success(setOf(CITY_ID)),
        private val addFailure: Throwable? = null,
        private val publishCityOnAdd: Boolean = false,
        private val refreshHandler: suspend (Long) -> RefreshResult = { cityId ->
            RefreshResult.Success(setOf(cityId))
        },
    ) : FavoriteCitiesRepository {
        val cities = MutableStateFlow<List<City>>(emptyList())
        val addedCities = mutableListOf<CitySearchResult>()
        val refreshedCityIds = mutableListOf<Long>()

        override fun observeCities(): Flow<List<City>> = cities
        override fun observeCity(id: Long): Flow<City?> = MutableStateFlow(null)
        override fun observeForecast(id: Long): Flow<Forecast?> = MutableStateFlow(null)
        override suspend fun getCity(id: Long): City? = null

        override suspend fun add(city: CitySearchResult): AddFavoriteResult {
            addedCities += city
            addFailure?.let { throw it }
            if (publishCityOnAdd) {
                cities.value = listOf(city.toFavoriteCity())
            }
            return AddFavoriteResult(city.id, addResult)
        }

        override suspend fun clear() = Unit
        override suspend fun refreshCity(cityId: Long): RefreshResult {
            refreshedCityIds += cityId
            return refreshHandler(cityId)
        }

        override suspend fun refreshAll(): RefreshResult = RefreshResult.EmptyFavorites
    }

    private class FakeDeviceLocationRepository : DeviceLocationRepository {
        override val latestCoordinates = MutableStateFlow<GeoCoordinates?>(null)
        override suspend fun currentCoordinates(): Result<GeoCoordinates> = Result.failure(
            IllegalStateException("Location is not requested by HomeViewModel"),
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        val settingsState = MutableStateFlow(UserSettings())
        override val settings: StateFlow<UserSettings> = settingsState

        override fun setTemperatureUnit(unit: TemperatureUnit) {
            settingsState.value = settingsState.value.copy(temperatureUnit = unit)
        }

        override fun setThemeMode(mode: ThemeMode) {
            settingsState.value = settingsState.value.copy(themeMode = mode)
        }

        override fun setAutomaticLocationWeather(enabled: Boolean) {
            settingsState.value = settingsState.value.copy(automaticLocationWeather = enabled)
        }
    }

    private class FakeWeatherSearchRepository(
        private val handler: suspend (String) -> Result<List<CitySearchResult>> = {
            Result.success(emptyList())
        },
    ) : WeatherSearchRepository {
        val queries = mutableListOf<String>()

        override suspend fun searchCities(query: String): Result<List<CitySearchResult>> {
            queries += query
            return handler(query)
        }
    }

    private class MutableLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }

    private companion object {
        const val CITY_ID = 524901L
        const val NOW_MILLIS = 1_800_000_000_000L

        val MOSCOW = CitySearchResult(
            id = CITY_ID,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.7558,
            longitude = 37.6173,
            timezone = "Europe/Moscow",
        )
        val MOSCOW_IDAHO = CitySearchResult(
            id = 5601538L,
            name = "Москва",
            country = "США",
            adminArea = "Айдахо",
            latitude = 46.7324,
            longitude = -117.0002,
            timezone = "America/Los_Angeles",
        )

        fun CitySearchResult.toFavoriteCity() = City(
            id = id,
            name = name,
            country = country.orEmpty(),
            adminArea = adminArea,
            latitude = latitude,
            longitude = longitude,
            timezone = timezone,
        )

        fun city(
            temperatureCelsius: Double?,
            weatherCode: Int?,
            updatedAt: Long?,
        ) = City(
            id = CITY_ID,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.7558,
            longitude = 37.6173,
            timezone = "Europe/Moscow",
            currentTemperatureCelsius = temperatureCelsius,
            currentWeatherCode = weatherCode,
            updatedAt = updatedAt,
        )
    }
}
