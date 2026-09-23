package com.tsygankov.weatherforecast.feature.forecast

import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.RefreshFailure
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.TemporaryRefreshFailureReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `existing city loads once and publishes only the forecast emitted by Room`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val refreshCanFinish = CompletableDeferred<Unit>()
            val repository = FakeFavoriteCitiesRepository { call ->
                assertEquals(1, call)
                refreshCanFinish.await()
                forecast.value = UPDATED_FORECAST
                RefreshResult.Success(setOf(CITY_ID))
            }
            val states = mutableListOf<ForecastUiState>()
            val viewModel = createViewModel(repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect(states::add)
            }

            runCurrent()

            assertEquals(1, repository.getCityCalls)
            assertEquals(1, repository.refreshCalls)
            assertTrue(viewModel.uiState.value is ForecastUiState.InitialLoading)
            assertFalse(states.any { it is ForecastUiState.CityNotFound })

            refreshCanFinish.complete(Unit)
            runCurrent()

            val content = viewModel.uiState.value as ForecastUiState.Content
            assertEquals(MOSCOW.name, content.cityName)
            assertEquals(18, content.content.current.temperature)
            assertEquals("2026-09-09T13:00", content.content.hourly.first().sourceTime)
            assertEquals("2026-09-09", content.content.weekDays.first().sourceDate)
            assertEquals(1, repository.refreshCalls)
            assertFalse(states.any { it is ForecastUiState.CityNotFound })
        }

    @Test
    fun `missing city becomes CityNotFound without a weather request`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeFavoriteCitiesRepository(initialCity = null)
            val viewModel = createViewModel(repository)

            runCurrent()

            assertTrue(viewModel.uiState.value is ForecastUiState.CityNotFound)
            assertEquals(1, repository.getCityCalls)
            assertEquals(0, repository.refreshCalls)
        }

    @Test
    fun `failed refresh keeps cached forecast in ErrorWithCache`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val refreshCanFinish = CompletableDeferred<Unit>()
            val repository = FakeFavoriteCitiesRepository(
                initialForecast = CACHED_FORECAST,
            ) {
                refreshCanFinish.await()
                temporaryFailure()
            }
            val viewModel = createViewModel(repository)

            runCurrent()

            val refreshing = viewModel.uiState.value as ForecastUiState.RefreshingWithContent
            assertEquals(MOSCOW.name, refreshing.cityName)
            assertEquals(12, refreshing.content.current.temperature)

            refreshCanFinish.complete(Unit)
            runCurrent()

            val error = viewModel.uiState.value as ForecastUiState.ErrorWithCache
            assertEquals(MOSCOW.name, error.cityName)
            assertEquals(refreshing.content, error.content)
            assertEquals(ForecastUserMessage.LOAD_FAILED, error.message)
            assertEquals(1, repository.refreshCalls)
        }

    @Test
    fun `retry moves no-cache error through loading to Room content`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val retryCanFinish = CompletableDeferred<Unit>()
            val repository = FakeFavoriteCitiesRepository { call ->
                if (call == 1) {
                    temporaryFailure()
                } else {
                    retryCanFinish.await()
                    forecast.value = UPDATED_FORECAST
                    RefreshResult.Success(setOf(CITY_ID))
                }
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            val firstError = viewModel.uiState.value as ForecastUiState.ErrorWithoutCache
            assertEquals(MOSCOW.name, firstError.cityName)
            assertEquals(ForecastUserMessage.LOAD_FAILED, firstError.message)

            viewModel.retry()
            runCurrent()

            val loading = viewModel.uiState.value as ForecastUiState.InitialLoading
            assertEquals(MOSCOW.name, loading.cityName)
            assertEquals(2, repository.refreshCalls)

            retryCanFinish.complete(Unit)
            runCurrent()

            val content = viewModel.uiState.value as ForecastUiState.Content
            assertEquals(18, content.content.current.temperature)
            assertEquals(2, repository.getCityCalls)
            assertEquals(2, repository.refreshCalls)
        }

    @Test
    fun `new load cancels the old request before starting its replacement`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeFavoriteCitiesRepository { call ->
                if (call == 1) {
                    awaitCancellation()
                }
                forecast.value = UPDATED_FORECAST
                RefreshResult.Success(setOf(CITY_ID))
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            assertEquals(1, repository.refreshCalls)
            assertEquals(1, repository.activeRefreshes)

            viewModel.load()
            runCurrent()

            assertEquals(2, repository.refreshCalls)
            assertEquals(1, repository.cancelledRefreshes)
            assertEquals(1, repository.maximumActiveRefreshes)
            assertEquals(0, repository.activeRefreshes)
            assertTrue(viewModel.uiState.value is ForecastUiState.Content)
        }

    @Test
    fun `deleting an observed city replaces content with CityNotFound`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeFavoriteCitiesRepository(
                initialForecast = CACHED_FORECAST,
            ) {
                RefreshResult.Success(setOf(CITY_ID))
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            assertTrue(viewModel.uiState.value is ForecastUiState.Content)

            repository.city.value = null
            repository.forecast.value = null
            runCurrent()

            assertTrue(viewModel.uiState.value is ForecastUiState.CityNotFound)
        }

    @Test
    fun `temperature setting remaps current hourly and daily values without another refresh`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val settingsRepository = FakeSettingsRepository()
            val repository = FakeFavoriteCitiesRepository(
                initialForecast = CACHED_FORECAST,
            ) {
                RefreshResult.Success(setOf(CITY_ID))
            }
            val viewModel = createViewModel(repository, settingsRepository)
            runCurrent()

            val celsius = viewModel.uiState.value as ForecastUiState.Content
            assertEquals(12, celsius.content.current.temperature)
            assertEquals(13, celsius.content.hourly.first().temperature)
            assertEquals(8, celsius.content.weekDays.first().minTemperature)
            assertEquals(14, celsius.content.weekDays.first().maxTemperature)
            assertEquals(8, celsius.content.monthDays.first().minTemperature)
            assertEquals(14, celsius.content.monthDays.first().maxTemperature)

            settingsRepository.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
            runCurrent()

            val fahrenheit = viewModel.uiState.value as ForecastUiState.Content
            assertEquals(54, fahrenheit.content.current.temperature)
            assertEquals(55, fahrenheit.content.hourly.first().temperature)
            assertEquals(46, fahrenheit.content.weekDays.first().minTemperature)
            assertEquals(57, fahrenheit.content.weekDays.first().maxTemperature)
            assertEquals(46, fahrenheit.content.monthDays.first().minTemperature)
            assertEquals(57, fahrenheit.content.monthDays.first().maxTemperature)
            assertEquals(TemperatureUnit.FAHRENHEIT, fahrenheit.temperatureUnit)
            assertEquals(1, repository.refreshCalls)
        }

    private fun createViewModel(
        repository: FavoriteCitiesRepository,
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
    ) = ForecastViewModel(
        cityId = CITY_ID,
        favoritesRepository = repository,
        settingsRepository = settingsRepository,
        appClock = AppClock { FIXED_NOW_MILLIS },
    )

    private class FakeFavoriteCitiesRepository(
        initialCity: City? = MOSCOW,
        initialForecast: Forecast? = null,
        private val refreshHandler: suspend FakeFavoriteCitiesRepository.(Int) -> RefreshResult = {
            RefreshResult.Success(setOf(CITY_ID))
        },
    ) : FavoriteCitiesRepository {
        val city = MutableStateFlow(initialCity)
        val forecast = MutableStateFlow(initialForecast)
        var getCityCalls = 0
        var refreshCalls = 0
        var activeRefreshes = 0
        var maximumActiveRefreshes = 0
        var cancelledRefreshes = 0

        override fun observeCities(): Flow<List<City>> = MutableStateFlow(emptyList())

        override fun observeCity(id: Long): Flow<City?> = city

        override fun observeForecast(id: Long): Flow<Forecast?> = forecast

        override suspend fun getCity(id: Long): City? {
            getCityCalls += 1
            return city.value
        }

        override suspend fun add(city: CitySearchResult): AddFavoriteResult =
            error("Not used in ForecastViewModel tests")

        override suspend fun clear() = Unit

        override suspend fun refreshCity(cityId: Long): RefreshResult {
            refreshCalls += 1
            activeRefreshes += 1
            maximumActiveRefreshes = maxOf(maximumActiveRefreshes, activeRefreshes)
            return try {
                refreshHandler(refreshCalls)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                cancelledRefreshes += 1
                throw cancellation
            } finally {
                activeRefreshes -= 1
            }
        }

        override suspend fun refreshAll(): RefreshResult = RefreshResult.EmptyFavorites

        fun temporaryFailure(): RefreshResult = RefreshResult.TemporaryFailure(
            listOf(
                RefreshFailure.Temporary(
                    cityId = CITY_ID,
                    reason = TemporaryRefreshFailureReason.NETWORK,
                ),
            ),
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val mutableSettings = MutableStateFlow(UserSettings())
        override val settings: StateFlow<UserSettings> = mutableSettings

        override fun setTemperatureUnit(unit: TemperatureUnit) {
            mutableSettings.value = mutableSettings.value.copy(temperatureUnit = unit)
        }

        override fun setThemeMode(mode: ThemeMode) = Unit

        override fun setAutomaticLocationWeather(enabled: Boolean) = Unit
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
        const val FIXED_NOW_MILLIS = 1_788_946_200_000L

        val MOSCOW = City(
            id = CITY_ID,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.7558,
            longitude = 37.6173,
            timezone = "Europe/Moscow",
        )

        val CACHED_FORECAST = forecast("cached", 12.0)
        val UPDATED_FORECAST = forecast("updated", 18.0)

        fun forecast(label: String, temperature: Double) = Forecast(
            timezone = "Europe/Moscow",
            current = CurrentWeather(
                time = "$label-now",
                temperatureCelsius = temperature,
                apparentTemperatureCelsius = temperature - 1.0,
                relativeHumidity = 60,
                pressure = 1_005.0,
                windSpeed = 8.0,
                weatherCode = 2,
                isDay = true,
            ),
            hourly = listOf(
                HourlyWeather(
                    time = "2026-09-09T12:00",
                    temperatureCelsius = temperature,
                    apparentTemperatureCelsius = temperature - 1.0,
                    humidity = 60,
                    precipitationProbability = 10,
                    weatherCode = 2,
                    windSpeed = 8.0,
                ),
                HourlyWeather(
                    time = "2026-09-09T13:00",
                    temperatureCelsius = temperature + 1.0,
                    apparentTemperatureCelsius = temperature,
                    humidity = 58,
                    precipitationProbability = 20,
                    weatherCode = 3,
                    windSpeed = 9.0,
                ),
                HourlyWeather(
                    time = "2026-09-09T14:00",
                    temperatureCelsius = temperature + 2.0,
                    apparentTemperatureCelsius = temperature + 1.0,
                    humidity = 56,
                    precipitationProbability = 30,
                    weatherCode = 61,
                    windSpeed = 10.0,
                ),
            ),
            daily = listOf(
                DailyWeather(
                    date = "2026-09-09",
                    maxTemperatureCelsius = temperature + 2.0,
                    minTemperatureCelsius = temperature - 4.0,
                    weatherCode = 2,
                    precipitationProbability = 20,
                    sunrise = "05:30",
                    sunset = "19:20",
                ),
            ),
        )
    }
}
