package com.tsygankov.weatherforecast.feature.settings

import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unit and theme selections update the immutable reactive state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val settingsRepository = FakeSettingsRepository()
            val viewModel = createViewModel(settingsRepository = settingsRepository)

            viewModel.setUnit(TemperatureUnit.FAHRENHEIT)
            viewModel.setTheme(ThemeMode.DARK)
            runCurrent()

            assertEquals(
                UserSettings(TemperatureUnit.FAHRENHEIT, ThemeMode.DARK),
                viewModel.uiState.value.settings,
            )
            assertEquals(null, viewModel.uiState.value.message)
        }

    @Test
    fun `automatic location setting updates state and can be disabled`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = createViewModel()
            assertTrue(viewModel.uiState.value.settings.automaticLocationWeather)

            viewModel.setAutomaticLocationWeather(false)
            runCurrent()
            assertFalse(viewModel.uiState.value.settings.automaticLocationWeather)

            viewModel.setAutomaticLocationWeather(true)
            runCurrent()
            assertTrue(viewModel.uiState.value.settings.automaticLocationWeather)
        }

    @Test
    fun `a failed preference write becomes a safe state and keeps old settings`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val settingsRepository = FakeSettingsRepository(failWrites = true)
            val viewModel = createViewModel(settingsRepository = settingsRepository)

            viewModel.setUnit(TemperatureUnit.FAHRENHEIT)
            runCurrent()

            assertEquals(UserSettings(), viewModel.uiState.value.settings)
            assertEquals(SettingsUserMessage.SAVE_FAILED, viewModel.uiState.value.message)
        }

    @Test
    fun `successful clear runs once emits one effect and reacts to the empty city flow`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val clearStarted = CompletableDeferred<Unit>()
            val finishClear = CompletableDeferred<Unit>()
            val favoritesRepository = FakeFavoriteCitiesRepository(
                initialCities = listOf(MOSCOW),
                clearHandler = {
                    clearStarted.complete(Unit)
                    finishClear.await()
                    cities.value = emptyList()
                },
            )
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)
            val nextEffect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.clearFavorites()
            viewModel.clearFavorites()
            runCurrent()
            clearStarted.await()

            assertEquals(1, favoritesRepository.clearCalls)
            assertEquals(ClearFavoritesUiState.CLEARING, viewModel.uiState.value.clearState)
            assertTrue(viewModel.uiState.value.hasFavorites)

            finishClear.complete(Unit)
            runCurrent()

            assertEquals(SettingsEffect.FavoritesCleared, nextEffect.await())
            assertEquals(1, favoritesRepository.clearCalls)
            assertEquals(ClearFavoritesUiState.IDLE, viewModel.uiState.value.clearState)
            assertFalse(viewModel.uiState.value.hasFavorites)
            assertEquals(null, viewModel.uiState.value.message)
        }

    @Test
    fun `failed clear keeps favorites and exposes no technical exception text`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val favoritesRepository = FakeFavoriteCitiesRepository(
                initialCities = listOf(MOSCOW),
                clearHandler = { throw IllegalStateException("SQLite secret details") },
            )
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)

            viewModel.clearFavorites()
            runCurrent()

            assertEquals(1, favoritesRepository.clearCalls)
            assertEquals(ClearFavoritesUiState.ERROR, viewModel.uiState.value.clearState)
            assertEquals(SettingsUserMessage.CLEAR_FAILED, viewModel.uiState.value.message)
            assertTrue(viewModel.uiState.value.hasFavorites)
        }

    @Test
    fun `clear is ignored when the favorite list is already empty`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val favoritesRepository = FakeFavoriteCitiesRepository()
            val viewModel = createViewModel(favoritesRepository = favoritesRepository)

            viewModel.clearFavorites()
            runCurrent()

            assertEquals(0, favoritesRepository.clearCalls)
            assertEquals(ClearFavoritesUiState.IDLE, viewModel.uiState.value.clearState)
        }

    private fun TestScope.createViewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        favoritesRepository: FakeFavoriteCitiesRepository = FakeFavoriteCitiesRepository(),
    ): SettingsViewModel = SettingsViewModel(
        settingsRepository = settingsRepository,
        favoritesRepository = favoritesRepository,
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
    }

    private class FakeSettingsRepository(
        private val failWrites: Boolean = false,
    ) : SettingsRepository {
        private val mutableSettings = MutableStateFlow(UserSettings())
        override val settings: StateFlow<UserSettings> = mutableSettings

        override fun setTemperatureUnit(unit: TemperatureUnit) {
            if (failWrites) error("SharedPreferences internals")
            mutableSettings.value = mutableSettings.value.copy(temperatureUnit = unit)
        }

        override fun setThemeMode(mode: ThemeMode) {
            if (failWrites) error("SharedPreferences internals")
            mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
        }

        override fun setAutomaticLocationWeather(enabled: Boolean) {
            if (failWrites) error("SharedPreferences internals")
            mutableSettings.value = mutableSettings.value.copy(automaticLocationWeather = enabled)
        }
    }

    private class FakeFavoriteCitiesRepository(
        initialCities: List<City> = emptyList(),
        private val clearHandler: suspend FakeFavoriteCitiesRepository.() -> Unit = {
            cities.value = emptyList()
        },
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
            clearHandler()
        }
        override suspend fun refreshCity(cityId: Long): RefreshResult = RefreshResult.EmptyFavorites
        override suspend fun refreshAll(): RefreshResult = RefreshResult.EmptyFavorites
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
