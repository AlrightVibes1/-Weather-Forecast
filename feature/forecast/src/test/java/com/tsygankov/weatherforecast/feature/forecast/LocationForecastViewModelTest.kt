package com.tsygankov.weatherforecast.feature.forecast

import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.GeoCoordinates
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
class LocationForecastViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unavailable location does not request weather and offers a safe error`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val location = FakeLocationRepository(Result.failure(IllegalStateException("private")))
            val weather = FakeCoordinatesWeatherRepository { Result.success(FORECAST) }
            val viewModel = createViewModel(location, weather)

            viewModel.retry()
            runCurrent()

            val state = viewModel.uiState.value as ForecastUiState.ErrorWithoutCache
            assertEquals(ForecastUserMessage.LOCATION_UNAVAILABLE, state.message)
            assertEquals(1, location.calls)
            assertEquals(0, weather.calls)
        }

    @Test
    fun `location forecast uses granted coordinates and unit changes do not fetch again`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val location = FakeLocationRepository(
                Result.success(GeoCoordinates(55.75, 37.61, cityName = "Москва")),
            )
            val weather = FakeCoordinatesWeatherRepository { Result.success(FORECAST) }
            val settings = FakeSettingsRepository()
            val viewModel = createViewModel(location, weather, settings)

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
    fun `retry keeps successful forecast when location or network later fails`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val location = FakeLocationRepository(Result.success(GeoCoordinates(55.75, 37.61)))
            var failWeather = false
            val weather = FakeCoordinatesWeatherRepository {
                if (failWeather) Result.failure(IllegalStateException("network details"))
                else Result.success(FORECAST)
            }
            val viewModel = createViewModel(location, weather)
            viewModel.retry()
            runCurrent()
            assertTrue(viewModel.uiState.value is ForecastUiState.Content)

            failWeather = true
            viewModel.retry()
            runCurrent()
            val cached = viewModel.uiState.value as ForecastUiState.ErrorWithCache
            assertEquals(10, cached.content.current.temperature)
            assertEquals(ForecastUserMessage.LOAD_FAILED, cached.message)
        }

    @Test
    fun `parallel retry taps start only one location request`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val locationGate = CompletableDeferred<Unit>()
            val location = FakeLocationRepository(Result.success(GeoCoordinates(55.75, 37.61))) {
                locationGate.await()
            }
            val weather = FakeCoordinatesWeatherRepository { Result.success(FORECAST) }
            val viewModel = createViewModel(location, weather)

            viewModel.retry()
            runCurrent()
            viewModel.retry()
            runCurrent()
            assertEquals(1, location.calls)
            assertEquals(0, weather.calls)

            locationGate.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value is ForecastUiState.Content)
            assertEquals(1, weather.calls)
        }

    private fun createViewModel(
        location: DeviceLocationRepository,
        weather: CoordinatesWeatherRepository,
        settings: SettingsRepository = FakeSettingsRepository(),
    ) = LocationForecastViewModel(
        deviceLocationRepository = location,
        coordinatesWeatherRepository = weather,
        settingsRepository = settings,
        clock = AppClock { 1_780_000_000_000L },
    )

    private class FakeLocationRepository(
        private val result: Result<GeoCoordinates>,
        private val beforeResult: suspend () -> Unit = {},
    ) : DeviceLocationRepository {
        var calls = 0

        override suspend fun currentCoordinates(): Result<GeoCoordinates> {
            calls += 1
            beforeResult()
            return result
        }
    }

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
