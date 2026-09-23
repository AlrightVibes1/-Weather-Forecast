package com.tsygankov.weatherforecast.feature.forecast

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherStartViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `first saved city becomes start forecast and clearing returns to empty`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val favorites = FakeFavoriteCitiesRepository()
            val viewModel = WeatherStartViewModel(favorites, FakeSettingsRepository())
            assertEquals(WeatherStartUiState.Loading, viewModel.uiState.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            runCurrent()
            assertEquals(WeatherStartUiState.Empty, viewModel.uiState.value)

            favorites.cities.value = listOf(city(11L), city(22L))
            runCurrent()
            assertEquals(WeatherStartUiState.City(11L), viewModel.uiState.value)

            favorites.cities.value = listOf(city(22L))
            runCurrent()
            assertEquals(WeatherStartUiState.City(22L), viewModel.uiState.value)

            favorites.cities.value = emptyList()
            runCurrent()
            assertEquals(WeatherStartUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `automatic location takes priority and disabling restores saved city`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val favorites = FakeFavoriteCitiesRepository().apply {
                cities.value = listOf(city(11L))
            }
            val settings = FakeSettingsRepository(automaticLocationWeather = true)
            val viewModel = WeatherStartViewModel(favorites, settings)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            runCurrent()
            assertEquals(WeatherStartUiState.Location, viewModel.uiState.value)
            viewModel.disableAutomaticLocationWeather()
            runCurrent()
            assertEquals(WeatherStartUiState.City(11L), viewModel.uiState.value)
            assertEquals(false, settings.settings.value.automaticLocationWeather)
        }

    private class FakeFavoriteCitiesRepository : FavoriteCitiesRepository {
        val cities = MutableStateFlow<List<City>>(emptyList())

        override fun observeCities(): Flow<List<City>> = cities
        override fun observeCity(id: Long): Flow<City?> = flowOf(null)
        override fun observeForecast(id: Long): Flow<Forecast?> = flowOf(null)
        override suspend fun getCity(id: Long): City? = cities.value.firstOrNull { it.id == id }
        override suspend fun add(city: CitySearchResult): AddFavoriteResult =
            AddFavoriteResult(city.id, RefreshResult.Success(setOf(city.id)))

        override suspend fun clear() {
            cities.value = emptyList()
        }

        override suspend fun refreshCity(cityId: Long): RefreshResult =
            RefreshResult.Success(setOf(cityId))

        override suspend fun refreshAll(): RefreshResult = RefreshResult.EmptyFavorites
    }

    private fun city(id: Long) = City(
        id = id,
        name = "Город $id",
        country = "Россия",
        adminArea = null,
        latitude = 55.0,
        longitude = 37.0,
        timezone = "Europe/Moscow",
    )

    private class FakeSettingsRepository(
        automaticLocationWeather: Boolean = false,
    ) : SettingsRepository {
        private val mutableSettings = MutableStateFlow(
            UserSettings(automaticLocationWeather = automaticLocationWeather),
        )
        override val settings: StateFlow<UserSettings> = mutableSettings

        override fun setTemperatureUnit(unit: TemperatureUnit) {
            mutableSettings.value = mutableSettings.value.copy(temperatureUnit = unit)
        }

        override fun setThemeMode(mode: ThemeMode) {
            mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
        }

        override fun setAutomaticLocationWeather(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(automaticLocationWeather = enabled)
        }
    }

    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
