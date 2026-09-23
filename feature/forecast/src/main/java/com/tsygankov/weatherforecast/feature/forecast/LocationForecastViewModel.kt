package com.tsygankov.weatherforecast.feature.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
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

class LocationForecastViewModel(
    private val deviceLocationRepository: DeviceLocationRepository,
    private val coordinatesWeatherRepository: CoordinatesWeatherRepository,
    settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val machine = MutableStateFlow(LocationForecastMachine())
    private var refreshJob: Job? = null

    val uiState: StateFlow<ForecastUiState> = combine(
        machine,
        settingsRepository.settings,
    ) { state, settings ->
        state.toForecastUiState(
            temperatureUnit = settings.temperatureUnit,
            nowMillis = clock.currentTimeMillis(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ForecastUiState.InitialLoading(),
    )

    fun retry() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            machine.update { it.copy(refreshing = true, error = null) }

            val coordinates = try {
                deviceLocationRepository.currentCoordinates().getOrNull()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (coordinates == null) {
                machine.update {
                    it.copy(
                        refreshing = false,
                        error = ForecastUserMessage.LOCATION_UNAVAILABLE,
                    )
                }
                return@launch
            }
            machine.update { it.copy(cityName = coordinates.cityName) }

            val forecast = try {
                coordinatesWeatherRepository.getForecast(
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
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

private data class LocationForecastMachine(
    val forecast: Forecast? = null,
    val cityName: String? = null,
    val refreshing: Boolean = true,
    val error: ForecastUserMessage? = null,
)

private fun LocationForecastMachine.toForecastUiState(
    temperatureUnit: TemperatureUnit,
    nowMillis: Long,
): ForecastUiState {
    val availableForecast = forecast
    val content = availableForecast?.toForecastContentUiModel(
        fallbackTimezone = availableForecast.timezone,
        temperatureUnit = temperatureUnit,
        nowMillis = nowMillis,
    )
    return when {
        content != null && refreshing -> ForecastUiState.RefreshingWithContent(
            cityName = cityName.orEmpty(),
            content = content,
            temperatureUnit = temperatureUnit,
        )

        content != null && error != null -> ForecastUiState.ErrorWithCache(
            cityName = cityName.orEmpty(),
            content = content,
            message = error,
            temperatureUnit = temperatureUnit,
        )

        content != null -> ForecastUiState.Content(
            cityName = cityName.orEmpty(),
            content = content,
            temperatureUnit = temperatureUnit,
        )

        error != null -> ForecastUiState.ErrorWithoutCache(
            cityName = cityName,
            message = error,
            temperatureUnit = temperatureUnit,
        )

        else -> ForecastUiState.InitialLoading(
            cityName = cityName,
            temperatureUnit = temperatureUnit,
        )
    }
}
