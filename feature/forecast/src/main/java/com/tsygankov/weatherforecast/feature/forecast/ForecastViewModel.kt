package com.tsygankov.weatherforecast.feature.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.PermanentRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ForecastUiState {
    val cityName: String?
    val temperatureUnit: TemperatureUnit

    data class InitialLoading(
        override val cityName: String? = null,
        override val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    ) : ForecastUiState

    data class Content(
        override val cityName: String,
        val content: ForecastContentUiModel,
        override val temperatureUnit: TemperatureUnit,
    ) : ForecastUiState

    data class RefreshingWithContent(
        override val cityName: String,
        val content: ForecastContentUiModel,
        override val temperatureUnit: TemperatureUnit,
    ) : ForecastUiState

    data class ErrorWithoutCache(
        override val cityName: String?,
        val message: ForecastUserMessage,
        override val temperatureUnit: TemperatureUnit,
    ) : ForecastUiState

    data class ErrorWithCache(
        override val cityName: String,
        val content: ForecastContentUiModel,
        val message: ForecastUserMessage,
        override val temperatureUnit: TemperatureUnit,
    ) : ForecastUiState

    data class CityNotFound(
        override val temperatureUnit: TemperatureUnit,
    ) : ForecastUiState {
        override val cityName: String? = null
    }
}

enum class ForecastUserMessage {
    LOAD_FAILED,
    LOCATION_UNAVAILABLE,
}

class ForecastViewModel(
    private val cityId: Long,
    private val favoritesRepository: FavoriteCitiesRepository,
    settingsRepository: SettingsRepository,
    private val appClock: AppClock,
) : ViewModel() {
    private val machineState = MutableStateFlow(ForecastMachineState())
    private val loadRequests = MutableStateFlow(0L)
    private var roomObservationJob: Job? = null

    val uiState: StateFlow<ForecastUiState> = combine(
        machineState,
        settingsRepository.settings,
    ) { state, settings ->
        state.toUiState(
            temperatureUnit = settings.temperatureUnit,
            nowMillis = appClock.currentTimeMillis(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ForecastUiState.InitialLoading(),
    )

    init {
        viewModelScope.launch {
            loadRequests.collectLatest {
                loadOnce()
            }
        }
    }

    fun load() {
        loadRequests.update { request -> request + 1L }
    }

    fun retry() = load()

    private suspend fun loadOnce() {
        machineState.update { state -> state.copy(refreshStatus = RefreshStatus.RUNNING) }

        val city = try {
            favoritesRepository.getCity(cityId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            machineState.update { state ->
                state.copy(
                    cityStatus = CityStatus.FAILED,
                    refreshStatus = RefreshStatus.FAILED,
                )
            }
            return
        }

        if (city == null) {
            roomObservationJob?.cancelAndJoin()
            roomObservationJob = null
            machineState.value = ForecastMachineState(
                cityStatus = CityStatus.MISSING,
                refreshStatus = RefreshStatus.IDLE,
            )
            return
        }

        machineState.update { state ->
            state.copy(
                city = city,
                cityStatus = CityStatus.FOUND,
                refreshStatus = RefreshStatus.RUNNING,
            )
        }
        startRoomObservation()

        val refreshResult = try {
            favoritesRepository.refreshCity(cityId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            machineState.update { state -> state.copy(refreshStatus = RefreshStatus.FAILED) }
            return
        }

        when {
            cityId in refreshResult.refreshedCityIds -> {
                machineState.update { state -> state.copy(refreshStatus = RefreshStatus.IDLE) }
            }

            refreshResult.permanentFailures.any { failure ->
                failure.cityId == cityId &&
                    failure.reason == PermanentRefreshFailureReason.CITY_NOT_FOUND
            } -> {
                roomObservationJob?.cancelAndJoin()
                roomObservationJob = null
                machineState.value = ForecastMachineState(
                    cityStatus = CityStatus.MISSING,
                    refreshStatus = RefreshStatus.IDLE,
                )
            }

            else -> {
                machineState.update { state -> state.copy(refreshStatus = RefreshStatus.FAILED) }
            }
        }
    }

    private fun startRoomObservation() {
        if (roomObservationJob != null) return

        roomObservationJob = viewModelScope.launch {
            combine(
                favoritesRepository.observeCity(cityId),
                favoritesRepository.observeForecast(cityId),
            ) { city, forecast -> city to forecast }
                .collect { (city, forecast) ->
                    machineState.update { state ->
                        if (city == null) {
                            ForecastMachineState(
                                cityStatus = CityStatus.MISSING,
                                refreshStatus = RefreshStatus.IDLE,
                            )
                        } else {
                            state.copy(
                                city = city,
                                cityStatus = CityStatus.FOUND,
                                forecast = forecast,
                            )
                        }
                    }
                }
        }
    }
}

private data class ForecastMachineState(
    val city: City? = null,
    val cityStatus: CityStatus = CityStatus.LOADING,
    val forecast: Forecast? = null,
    val refreshStatus: RefreshStatus = RefreshStatus.RUNNING,
)

private enum class CityStatus {
    LOADING,
    FOUND,
    MISSING,
    FAILED,
}

private enum class RefreshStatus {
    IDLE,
    RUNNING,
    FAILED,
}

private fun ForecastMachineState.toUiState(
    temperatureUnit: TemperatureUnit,
    nowMillis: Long,
): ForecastUiState {
    if (cityStatus == CityStatus.MISSING) {
        return ForecastUiState.CityNotFound(temperatureUnit)
    }

    val availableCity = city
    val cachedForecast = forecast
    val content = if (availableCity != null && cachedForecast != null) {
        cachedForecast.toForecastContentUiModel(
            city = availableCity,
            temperatureUnit = temperatureUnit,
            nowMillis = nowMillis,
        )
    } else {
        null
    }
    return when {
        refreshStatus == RefreshStatus.FAILED && availableCity != null && content != null ->
            ForecastUiState.ErrorWithCache(
                cityName = availableCity.name,
                content = content,
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = temperatureUnit,
            )

        refreshStatus == RefreshStatus.FAILED ->
            ForecastUiState.ErrorWithoutCache(
                cityName = availableCity?.name,
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = temperatureUnit,
            )

        refreshStatus == RefreshStatus.RUNNING && availableCity != null && content != null ->
            ForecastUiState.RefreshingWithContent(
                cityName = availableCity.name,
                content = content,
                temperatureUnit = temperatureUnit,
            )

        refreshStatus == RefreshStatus.RUNNING ->
            ForecastUiState.InitialLoading(
                cityName = availableCity?.name,
                temperatureUnit = temperatureUnit,
            )

        availableCity != null && content != null ->
            ForecastUiState.Content(
                cityName = availableCity.name,
                content = content,
                temperatureUnit = temperatureUnit,
            )

        else -> ForecastUiState.InitialLoading(
            cityName = availableCity?.name,
            temperatureUnit = temperatureUnit,
        )
    }
}
