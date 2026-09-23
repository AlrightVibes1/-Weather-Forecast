package com.tsygankov.weatherforecast.feature.forecast

import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class TemporaryCityForecastViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `temporary city loads by coordinates without adding a favorite`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val weather = FakeCoordinatesWeatherRepository { Result.success(FORECAST) }
            val settings = FakeSettingsRepository()
            val viewModel = createViewModel(weather, settings)

            viewModel.retry()
            runCurrent()

            val celsius = viewModel.uiState.value as ForecastUiState.Content
            assertEquals("Москва", celsius.cityName)
            assertEquals(10, celsius.content.current.temperature)
            assertEquals(55.75 to 37.61, weather.lastCoordinates)

            settings.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
            runCurrent()
            val fahrenheit = viewModel.uiState.value as ForecastUiState.Content
            assertEquals(50, fahrenheit.content.current.temperature)
            assertEquals(1, weather.calls)
        }

    @Test
    fun `failed reload keeps an already shown temporary forecast`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            var fail = false
            val weather = FakeCoordinatesWeatherRepository {
                if (fail) Result.failure(IllegalStateException("network details"))
                else Result.success(FORECAST)
            }
            val viewModel = createViewModel(weather)
            viewModel.retry()
            runCurrent()
            assertTrue(viewModel.uiState.value is ForecastUiState.Content)

            fail = true
            viewModel.retry()
            runCurrent()

            val cached = viewModel.uiState.value as ForecastUiState.ErrorWithCache
            assertEquals("Москва", cached.cityName)
            assertEquals(10, cached.content.current.temperature)
            assertEquals(ForecastUserMessage.LOAD_FAILED, cached.message)
        }

    @Test
    fun `parallel retry taps start only one temporary city request`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val weather = FakeCoordinatesWeatherRepository {
                gate.await()
                Result.success(FORECAST)
            }
            val viewModel = createViewModel(weather)

            viewModel.retry()
            runCurrent()
            viewModel.retry()
            runCurrent()
            assertEquals(1, weather.calls)

            gate.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value is ForecastUiState.Content)
        }

    private fun createViewModel(
        weather: CoordinatesWeatherRepository,
        settings: SettingsRepository = FakeSettingsRepository(),
    ) = TemporaryCityForecastViewModel(
        city = CITY,
        coordinatesWeatherRepository = weather,
        settingsRepository = settings,
        clock = AppClock { 1_780_000_000_000L },
    )

    private class FakeCoordinatesWeatherRepository(
        private val response: suspend () -> Result<Forecast>,
    ) : CoordinatesWeatherRepository {
        var calls = 0
        var lastCoordinates: Pair<Double, Double>? = null

        override suspend fun getForecast(latitude: Double, longitude: Double): Result<Forecast> {
            calls += 1
            lastCoordinates = latitude to longitude
            return response()
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val mutableSettings = MutableStateFlow(UserSettings())
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

    private companion object {
        val CITY = CitySearchResult(
            id = 524901L,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.75,
            longitude = 37.61,
            timezone = "Europe/Moscow",
        )
        val FORECAST = Forecast(
            timezone = "Europe/Moscow",
            current = CurrentWeather(
                time = "2026-09-21T12:00",
                temperatureCelsius = 10.0,
                apparentTemperatureCelsius = 8.0,
                relativeHumidity = 60,
                pressure = 1_010.0,
                windSpeed = 5.0,
                weatherCode = 2,
                isDay = true,
            ),
            hourly = emptyList(),
            daily = emptyList(),
        )
    }
}
