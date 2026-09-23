package com.tsygankov.weatherforecast.feature.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemporaryCityForecastViewModel(
    private val city: CitySearchResult,
    private val coordinatesWeatherRepository: CoordinatesWeatherRepository,
    settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val machine = MutableStateFlow(TemporaryCityForecastMachine())
    private var refreshJob: Job? = null

    val uiState: StateFlow<ForecastUiState> = combine(
        machine,
        settingsRepository.settings,
    ) { state, settings ->
        state.toForecastUiState(
            city = city,
            temperatureUnit = settings.temperatureUnit,
            nowMillis = clock.currentTimeMillis(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ForecastUiState.InitialLoading(cityName = city.name),
    )

    fun retry() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            machine.update { it.copy(refreshing = true, error = null) }
            val forecast = try {
                coordinatesWeatherRepository.getForecast(
                    latitude = city.latitude,
                    longitude = city.longitude,
                ).getOrNull()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            machine.update { state ->
                if (forecast == null) {
                    state.copy(refreshing = false, error = ForecastUserMessage.LOAD_FAILED)
                } else {
                    state.copy(forecast = forecast, refreshing = false, error = null)
                }
            }
        }
    }
}

private data class TemporaryCityForecastMachine(
    val forecast: Forecast? = null,
    val refreshing: Boolean = true,
    val error: ForecastUserMessage? = null,
)

private fun TemporaryCityForecastMachine.toForecastUiState(
    city: CitySearchResult,
    temperatureUnit: TemperatureUnit,
    nowMillis: Long,
): ForecastUiState {
    val availableForecast = forecast
    val content = availableForecast?.toForecastContentUiModel(
        fallbackTimezone = city.timezone,
        temperatureUnit = temperatureUnit,
        nowMillis = nowMillis,
    )
    return when {
        content != null && refreshing -> ForecastUiState.RefreshingWithContent(
            cityName = city.name,
            content = content,
            temperatureUnit = temperatureUnit,
        )

        content != null && error != null -> ForecastUiState.ErrorWithCache(
            cityName = city.name,
            content = content,
            message = error,
            temperatureUnit = temperatureUnit,
        )

        content != null -> ForecastUiState.Content(
            cityName = city.name,
            content = content,
            temperatureUnit = temperatureUnit,
        )

        error != null -> ForecastUiState.ErrorWithoutCache(
            cityName = city.name,
            message = error,
            temperatureUnit = temperatureUnit,
        )

        else -> ForecastUiState.InitialLoading(
            cityName = city.name,
            temperatureUnit = temperatureUnit,
        )
    }
}
