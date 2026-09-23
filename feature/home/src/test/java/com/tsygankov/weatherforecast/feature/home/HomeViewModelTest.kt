package com.tsygankov.weatherforecast.feature.home

import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.GeoCoordinates
import com.tsygankov.weatherforecast.domain.repository.RefreshFailure
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.TemporaryRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `latest resolved location is exposed only while automatic weather is enabled`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val location = FakeDeviceLocationRepository().apply {
                latestCoordinates.value = GeoCoordinates(
                    latitude = 54.99,
                    longitude = 73.37,
                    cityName = "Омск",
                )
            }
            val settings = FakeSettingsRepository()
            val viewModel = createViewModel(
                deviceLocationRepository = location,
                settingsRepository = settings,
            )

            assertTrue(viewModel.uiState.value.showCurrentLocation)
            assertEquals("Омск", viewModel.uiState.value.currentLocationCityName)

            settings.setAutomaticLocationWeather(false)
            runCurrent()

            assertEquals(false, viewModel.uiState.value.showCurrentLocation)
            assertEquals("Омск", viewModel.uiState.value.currentLocationCityName)
        }

    @Test
    fun `short query is idle and valid query waits exactly 400 ms before loading`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val response = CompletableDeferred<Result<List<CitySearchResult>>>()
            val searchRepository = FakeWeatherSearchRepository { response.await() }
            val viewModel = createViewModel(searchRepository = searchRepository)

            viewModel.updateQuery("М")
            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(searchRepository.queries.isEmpty())
            assertEquals(SearchUiState.Idle, viewModel.uiState.value.search)

            viewModel.updateQuery("Москва")
            advanceTimeBy(399)
            runCurrent()

            assertTrue(searchRepository.queries.isEmpty())
            assertEquals(SearchUiState.Idle, viewModel.uiState.value.search)

            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf("Москва"), searchRepository.queries)
            assertEquals(SearchUiState.Loading("Москва"), viewModel.uiState.value.search)

            response.complete(Result.success(listOf(MOSCOW)))
            runCurrent()

            assertEquals(
                SearchUiState.Results("Москва", listOf(MOSCOW)),
                viewModel.uiState.value.search,
            )
        }

    @Test
    fun `empty response is different from idle`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = createViewModel(
                searchRepository = FakeWeatherSearchRepository {
                    Result.success(emptyList())
                },
            )

            viewModel.updateQuery("Тверь")
            advanceTimeBy(400)
            runCurrent()

            assertEquals(SearchUiState.Empty("Тверь"), viewModel.uiState.value.search)
        }

    @Test
    fun `slow old response cannot replace results of a newer query`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val oldResponse = CompletableDeferred<Result<List<CitySearchResult>>>()
            val newResponse = CompletableDeferred<Result<List<CitySearchResult>>>()
            val searchRepository = FakeWeatherSearchRepository { query ->
                when (query) {
                    "Москва" -> withContext(NonCancellable) { oldResponse.await() }
                    "Тверь" -> newResponse.await()
                    else -> error("Unexpected query")
                }
            }
            val viewModel = createViewModel(searchRepository = searchRepository)

            viewModel.updateQuery("Москва")
            advanceTimeBy(400)
            runCurrent()
            assertEquals(SearchUiState.Loading("Москва"), viewModel.uiState.value.search)

            viewModel.updateQuery("Тверь")
            advanceTimeBy(400)
            runCurrent()
            newResponse.complete(Result.success(listOf(TVER)))
            runCurrent()

            assertEquals(
                SearchUiState.Results("Тверь", listOf(TVER)),
                viewModel.uiState.value.search,
            )

            oldResponse.complete(Result.success(listOf(MOSCOW)))
            runCurrent()

            assertEquals(
                SearchUiState.Results("Тверь", listOf(TVER)),
                viewModel.uiState.value.search,
            )
            assertEquals(listOf("Москва", "Тверь"), searchRepository.queries)
        }

    @Test
    fun `error is safe and retry immediately uses the current normalized query`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val retryResponse = CompletableDeferred<Result<List<CitySearchResult>>>()
            var attempt = 0
            val searchRepository = FakeWeatherSearchRepository {
                attempt += 1
                if (attempt == 1) {
                    throw IllegalStateException("HTTP 500: internal-secret")
                } else {
                    retryResponse.await()
                }
            }
            val viewModel = createViewModel(searchRepository = searchRepository)

            viewModel.updateQuery("  Москва  ")
            advanceTimeBy(400)
            runCurrent()

            assertEquals(
                SearchUiState.Error("Москва", HomeUserMessage.SEARCH_FAILED),
                viewModel.uiState.value.search,
            )

            viewModel.retrySearch()
            runCurrent()

            assertEquals(
                listOf("Москва", "Москва"),
                searchRepository.queries,
            )
            assertEquals(SearchUiState.Loading("Москва"), viewModel.uiState.value.search)

            retryResponse.complete(Result.success(listOf(MOSCOW)))
            runCurrent()

            assertEquals(
                SearchUiState.Results("Москва", listOf(MOSCOW)),
                viewModel.uiState.value.search,
            )
        }

    @Test
    fun `adding is visible and failed first weather refresh emits a one-time safe effect`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val addResponse = CompletableDeferred<AddFavoriteResult>()
            val favoritesRepository = FakeFavoriteCitiesRepository(
                addHandler = { addResponse.await() },
            )
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)
            val nextEffect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.updateQuery("Москва")
            viewModel.addCity(MOSCOW)
            viewModel.addCity(MOSCOW)
            runCurrent()

            assertEquals(AddCityUiState.Adding(MOSCOW.id), viewModel.uiState.value.adding)
            assertEquals(listOf(MOSCOW), favoritesRepository.addedCities)

            addResponse.complete(
                AddFavoriteResult(
                    cityId = MOSCOW.id,
                    refreshResult = RefreshResult.TemporaryFailure(
                        listOf(
                            RefreshFailure.Temporary(
                                cityId = MOSCOW.id,
                                reason = TemporaryRefreshFailureReason.NETWORK,
                            ),
                        ),
                    ),
                ),
            )
            runCurrent()

            assertEquals(
                HomeEffect.CityAddedWithoutWeather(MOSCOW.id),
                nextEffect.await(),
            )
            assertEquals(AddCityUiState.Idle, viewModel.uiState.value.adding)
            assertEquals("", viewModel.uiState.value.query)
            assertEquals(SearchUiState.Idle, viewModel.uiState.value.search)
        }

    @Test
    fun `successful add emits success once and clears search`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val favoritesRepository = FakeFavoriteCitiesRepository()
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)
            val nextEffect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.updateQuery("Москва")
            viewModel.addCity(MOSCOW)
            runCurrent()

            assertEquals(HomeEffect.CityAdded(MOSCOW.id), nextEffect.await())
            assertEquals(listOf(MOSCOW), favoritesRepository.addedCities)
            assertEquals(AddCityUiState.Idle, viewModel.uiState.value.adding)
            assertEquals("", viewModel.uiState.value.query)
            assertEquals(SearchUiState.Idle, viewModel.uiState.value.search)
        }

    @Test
    fun `add exception emits safe failure effect and keeps current search`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val favoritesRepository = FakeFavoriteCitiesRepository(
                addHandler = { throw IllegalStateException("database internals") },
            )
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)
            val nextEffect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.updateQuery("Москва")
            viewModel.addCity(MOSCOW)
            runCurrent()

            assertEquals(HomeEffect.AddCityFailed(MOSCOW.id), nextEffect.await())
            assertEquals(AddCityUiState.Idle, viewModel.uiState.value.adding)
            assertEquals("Москва", viewModel.uiState.value.query)
        }

    @Test
    fun `resume refreshes only missing and stale cities at the exact threshold`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val favoritesRepository = FakeFavoriteCitiesRepository().apply {
                cities.value = listOf(
                    city(
                        id = 1L,
                        temperatureCelsius = 10.0,
                        weatherCode = 1,
                        updatedAt = FIXED_NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS + 1L,
                    ),
                    city(
                        id = 2L,
                        temperatureCelsius = 20.0,
                        weatherCode = 2,
                        updatedAt = FIXED_NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS,
                    ),
                    city(
                        id = 3L,
                        temperatureCelsius = null,
                        weatherCode = null,
                        updatedAt = null,
                    ),
                )
            }
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)

            assertTrue(favoritesRepository.refreshedCityIds.isEmpty())

            viewModel.onResume()
            runCurrent()

            assertEquals(listOf(2L, 3L), favoritesRepository.refreshedCityIds)
            assertEquals(false, viewModel.uiState.value.refreshing)
        }

    @Test
    fun `repeated resume during refresh starts one request and flow replaces stale cache`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseRequest = CompletableDeferred<Unit>()
            lateinit var favoritesRepository: FakeFavoriteCitiesRepository
            favoritesRepository = FakeFavoriteCitiesRepository(
                refreshHandler = { cityId ->
                    requestStarted.complete(Unit)
                    releaseRequest.await()
                    favoritesRepository.cities.value = listOf(
                        city(
                            id = cityId,
                            temperatureCelsius = 25.0,
                            weatherCode = 2,
                            updatedAt = FIXED_NOW_MILLIS,
                        ),
                    )
                    RefreshResult.Success(setOf(cityId))
                },
            ).apply {
                cities.value = listOf(
                    city(
                        temperatureCelsius = 10.0,
                        weatherCode = 61,
                        updatedAt = FIXED_NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS,
                    ),
                )
            }
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)

            viewModel.onResume()
            runCurrent()
            requestStarted.await()

            assertEquals(true, viewModel.uiState.value.refreshing)
            assertEquals(10, viewModel.uiState.value.cityItems.single().temperature)
            assertEquals(true, viewModel.uiState.value.cityItems.single().isStale)

            viewModel.onResume()
            runCurrent()
            assertEquals(listOf(CITY_ID), favoritesRepository.refreshedCityIds)

            releaseRequest.complete(Unit)
            runCurrent()

            assertEquals(false, viewModel.uiState.value.refreshing)
            assertEquals(25, viewModel.uiState.value.cityItems.single().temperature)
            assertEquals(false, viewModel.uiState.value.cityItems.single().isStale)
            assertEquals(listOf(CITY_ID), favoritesRepository.refreshedCityIds)
        }

    @Test
    fun `failed stale refresh keeps cache and emits one safe effect`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
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
                        updatedAt = FIXED_NOW_MILLIS - HOME_STALE_THRESHOLD_MILLIS,
                    ),
                )
            }
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)
            val nextEffect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.onResume()
            runCurrent()

            assertEquals(HomeEffect.StaleRefreshFailed(), nextEffect.await())
            assertEquals(false, viewModel.uiState.value.refreshing)
            assertEquals(10, viewModel.uiState.value.cityItems.single().temperature)
            assertEquals(true, viewModel.uiState.value.cityItems.single().isStale)
            assertEquals(listOf(CITY_ID), favoritesRepository.refreshedCityIds)
        }

    private fun TestScope.createViewModel(
        favoritesRepository: FakeFavoriteCitiesRepository = FakeFavoriteCitiesRepository(),
        searchRepository: FakeWeatherSearchRepository = FakeWeatherSearchRepository(),
        deviceLocationRepository: FakeDeviceLocationRepository = FakeDeviceLocationRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ): HomeViewModel = HomeViewModel(
        favoritesRepository = favoritesRepository,
        weatherRepository = searchRepository,
        deviceLocationRepository = deviceLocationRepository,
        settingsRepository = settingsRepository,
        clock = AppClock { FIXED_NOW_MILLIS },
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
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

    private class FakeDeviceLocationRepository : DeviceLocationRepository {
        override val latestCoordinates = MutableStateFlow<GeoCoordinates?>(null)
        override suspend fun currentCoordinates(): Result<GeoCoordinates> = Result.failure(
            IllegalStateException("Location is not requested by HomeViewModel"),
        )
    }

    private class FakeFavoriteCitiesRepository(
        private val addHandler: suspend (CitySearchResult) -> AddFavoriteResult = {
            AddFavoriteResult(it.id, RefreshResult.Success(setOf(it.id)))
        },
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
            return addHandler(city)
        }
        override suspend fun clear() = Unit
        override suspend fun refreshCity(cityId: Long): RefreshResult {
            refreshedCityIds += cityId
            return refreshHandler(cityId)
        }

        override suspend fun refreshAll(): RefreshResult = RefreshResult.EmptyFavorites
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val mutableSettings = MutableStateFlow(UserSettings())
        override val settings: StateFlow<UserSettings> = mutableSettings

        override fun setTemperatureUnit(unit: TemperatureUnit) = Unit

        override fun setThemeMode(mode: ThemeMode) = Unit

        override fun setAutomaticLocationWeather(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                automaticLocationWeather = enabled,
            )
        }
    }

    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val CITY_ID = 524901L
        const val FIXED_NOW_MILLIS = 1_800_000_000_000L
        val MOSCOW = CitySearchResult(
            id = CITY_ID,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.7558,
            longitude = 37.6173,
            timezone = "Europe/Moscow",
        )
        val TVER = CitySearchResult(
            id = 480060L,
            name = "Тверь",
            country = "Россия",
            adminArea = "Тверская область",
            latitude = 56.8587,
            longitude = 35.9176,
            timezone = "Europe/Moscow",
        )

        fun city(
            id: Long = CITY_ID,
            temperatureCelsius: Double?,
            weatherCode: Int?,
            updatedAt: Long?,
        ) = City(
            id = id,
            name = "Город $id",
            country = "Россия",
            adminArea = "Регион",
            latitude = 55.7558,
            longitude = 37.6173,
            timezone = "Europe/Moscow",
            currentTemperatureCelsius = temperatureCelsius,
            currentWeatherCode = weatherCode,
            updatedAt = updatedAt,
        )
    }
}
