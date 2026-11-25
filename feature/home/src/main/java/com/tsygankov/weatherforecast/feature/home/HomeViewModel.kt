package com.tsygankov.weatherforecast.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import com.tsygankov.weatherforecast.core.model.roundedInUnit
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class HomeUiState(
    val cityItems: List<HomeCityUiModel> = emptyList(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val refreshing: Boolean = false,
    val query: String = "",
    val search: SearchUiState = SearchUiState.Idle,
    val adding: AddCityUiState = AddCityUiState.Idle,
    val showCurrentLocation: Boolean = false,
    val currentLocationCityName: String? = null,
)

data class HomeCityUiModel(
    val id: Long,
    val name: String,
    val location: String?,
    val temperature: Int?,
    val temperatureUnitSymbol: String,
    val weatherCondition: WeatherCondition?,
    val updatedAtMillis: Long?,
    val timezone: String,
    val isStale: Boolean,
)

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(val query: String) : SearchUiState
    data class Results(
        val query: String,
        val cities: List<CitySearchResult>,
    ) : SearchUiState

    data class Empty(val query: String) : SearchUiState
    data class Error(
        val query: String,
        val message: HomeUserMessage,
    ) : SearchUiState
}

sealed interface AddCityUiState {
    data object Idle : AddCityUiState
    data class Adding(val cityId: Long) : AddCityUiState
}

enum class HomeUserMessage {
    SEARCH_FAILED,
    ADD_CITY_FAILED,
    WEATHER_REFRESH_FAILED,
    STALE_REFRESH_FAILED,
}

sealed interface HomeEffect {
    data class CityAdded(val cityId: Long) : HomeEffect
    data class CityAddedWithoutWeather(
        val cityId: Long,
        val message: HomeUserMessage = HomeUserMessage.WEATHER_REFRESH_FAILED,
    ) : HomeEffect

    data class AddCityFailed(
        val cityId: Long,
        val message: HomeUserMessage = HomeUserMessage.ADD_CITY_FAILED,
    ) : HomeEffect

    data class StaleRefreshFailed(
        val message: HomeUserMessage = HomeUserMessage.STALE_REFRESH_FAILED,
    ) : HomeEffect
}

class HomeViewModel(
    private val favoritesRepository: FavoriteCitiesRepository,
    private val weatherRepository: WeatherSearchRepository,
    deviceLocationRepository: DeviceLocationRepository? = null,
    settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val interaction = MutableStateFlow(HomeInteractionState())
    private val effectsChannel = Channel<HomeEffect>(capacity = Channel.BUFFERED)
    private var searchJob: Job? = null
    private var staleRefreshJob: Job? = null
    private var searchGeneration = 0L

    // combine пересчитывает состояние при изменении любого из источников.
    // stateIn хранит последнее значение в viewModelScope и управляет сбором по подписчикам.
    val uiState: StateFlow<HomeUiState> = combine(
        favoritesRepository.observeCities(),
        settingsRepository.settings,
        deviceLocationRepository?.latestCoordinates ?: flowOf(null),
        interaction,
    ) { cities, settings, latestCoordinates, currentInteraction ->
        val nowMillis = clock.currentTimeMillis()
        HomeUiState(
            cityItems = cities.map { city ->
                city.toHomeCityUiModel(
                    unit = settings.temperatureUnit,
                    nowMillis = nowMillis,
                )
            },
            temperatureUnit = settings.temperatureUnit,
            refreshing = currentInteraction.refreshing,
            query = currentInteraction.query,
            search = currentInteraction.search,
            adding = currentInteraction.adding,
            showCurrentLocation = settings.automaticLocationWeather,
            currentLocationCityName = latestCoordinates?.cityName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    // Channel передаёт одноразовые события. Новый подписчик не получает событие,
    // уже обработанное предыдущим, как получил бы последнее значение StateFlow.
    val effects: Flow<HomeEffect> = effectsChannel.receiveAsFlow()

    fun updateQuery(value: String) {
        val normalizedQuery = value.trim()
        val generation = invalidateSearch()
        interaction.update {
            it.copy(
                query = value,
                search = SearchUiState.Idle,
            )
        }
        if (normalizedQuery.length < MIN_SEARCH_LENGTH) return

        searchJob = launchSearch(
            query = normalizedQuery,
            generation = generation,
            delayMillis = SEARCH_DEBOUNCE_MILLIS,
        )
    }

    fun retrySearch() {
        val normalizedQuery = interaction.value.query.trim()
        val generation = invalidateSearch()
        if (normalizedQuery.length < MIN_SEARCH_LENGTH) {
            interaction.update { it.copy(search = SearchUiState.Idle) }
            return
        }

        searchJob = launchSearch(
            query = normalizedQuery,
            generation = generation,
            delayMillis = 0L,
        )
    }

    fun addCity(city: CitySearchResult) {
        if (interaction.value.adding is AddCityUiState.Adding) return
        interaction.update { it.copy(adding = AddCityUiState.Adding(city.id)) }

        viewModelScope.launch {
            try {
                val result = favoritesRepository.add(city)
                clearSearch()
                val effect = if (result.cityId in result.refreshResult.refreshedCityIds) {
                    HomeEffect.CityAdded(result.cityId)
                } else {
                    HomeEffect.CityAddedWithoutWeather(result.cityId)
                }
                effectsChannel.send(effect)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                effectsChannel.send(HomeEffect.AddCityFailed(city.id))
            } finally {
                interaction.update { current ->
                    if (current.adding == AddCityUiState.Adding(city.id)) {
                        current.copy(adding = AddCityUiState.Idle)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun onResume() {
        if (staleRefreshJob?.isActive == true) return
        staleRefreshJob = viewModelScope.launch {
            var progressShown = false
            try {
                val nowMillis = clock.currentTimeMillis()
                val staleCityIds = favoritesRepository.observeCities()
                    .first()
                    .asSequence()
                    .filter { city -> city.needsHomeRefresh(nowMillis) }
                    .map(City::id)
                    .distinct()
                    .toList()
                if (staleCityIds.isEmpty()) return@launch

                interaction.update { it.copy(refreshing = true) }
                progressShown = true
                val hasFailures = supervisorScope {
                    staleCityIds.map { cityId ->
                        async {
                            try {
                                favoritesRepository.refreshCity(cityId).hasFailures()
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Throwable) {
                                true
                            }
                        }
                    }.awaitAll().any { it }
                }
                if (hasFailures) {
                    effectsChannel.send(HomeEffect.StaleRefreshFailed())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                effectsChannel.send(HomeEffect.StaleRefreshFailed())
            } finally {
                if (progressShown) {
                    interaction.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    private fun launchSearch(
        query: String,
        generation: Long,
        delayMillis: Long,
    ): Job = viewModelScope.launch {
        // delay приостанавливает корутину без блокировки потока. Повторный ввод может
        // отменить эту Job до запроса. Проверка поколения отсекает запоздалый ответ.
        if (delayMillis > 0L) delay(delayMillis)
        if (!isCurrentSearch(query, generation)) return@launch

        interaction.update { it.copy(search = SearchUiState.Loading(query)) }
        val result: Result<List<CitySearchResult>>? = try {
            weatherRepository.searchCities(query)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        if (!isCurrentSearch(query, generation)) return@launch

        val nextSearchState = if (result == null) {
            SearchUiState.Error(query, HomeUserMessage.SEARCH_FAILED)
        } else {
            result.fold(
                onSuccess = { cities ->
                    if (cities.isEmpty()) {
                        SearchUiState.Empty(query)
                    } else {
                        SearchUiState.Results(query, cities)
                    }
                },
                onFailure = {
                    SearchUiState.Error(query, HomeUserMessage.SEARCH_FAILED)
                },
            )
        }
        interaction.update { current ->
            current.copy(search = nextSearchState)
        }
    }

    private fun isCurrentSearch(query: String, generation: Long): Boolean =
        generation == searchGeneration && interaction.value.query.trim() == query

    private fun clearSearch() {
        invalidateSearch()
        interaction.update {
            it.copy(
                query = "",
                search = SearchUiState.Idle,
            )
        }
    }

    private fun invalidateSearch(): Long {
        searchGeneration += 1
        // Отмена Job прекращает ожидание предыдущего поиска. Номер поколения выше
        // помечает предыдущий результат устаревшим даже при запоздалом завершении.
        searchJob?.cancel()
        searchJob = null
        return searchGeneration
    }

    private data class HomeInteractionState(
        val refreshing: Boolean = false,
        val query: String = "",
        val search: SearchUiState = SearchUiState.Idle,
        val adding: AddCityUiState = AddCityUiState.Idle,
    )

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_DEBOUNCE_MILLIS = 400L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun City.toHomeCityUiModel(
    unit: TemperatureUnit,
    nowMillis: Long,
): HomeCityUiModel {
    val temperature = currentTemperatureCelsius?.roundedInUnit(unit)
    val hasCachedWeather = temperature != null
    return HomeCityUiModel(
        id = id,
        name = name,
        location = listOfNotNull(
            adminArea?.takeIf(String::isNotBlank),
            country.takeIf(String::isNotBlank),
        )
            .distinct()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(),
        temperature = temperature,
        temperatureUnitSymbol = unit.symbol,
        weatherCondition = if (hasCachedWeather) {
            WeatherCondition.fromWmo(currentWeatherCode)
        } else {
            null
        },
        updatedAtMillis = updatedAt,
        timezone = timezone,
        isStale = hasCachedWeather && isHomeTimestampStale(updatedAt, nowMillis),
    )
}

internal fun City.needsHomeRefresh(nowMillis: Long): Boolean =
    updatedAt == null || isHomeTimestampStale(updatedAt, nowMillis)

private fun isHomeTimestampStale(updatedAt: Long?, nowMillis: Long): Boolean =
    updatedAt != null &&
        nowMillis >= updatedAt &&
        nowMillis - updatedAt >= HOME_STALE_THRESHOLD_MILLIS

private fun RefreshResult.hasFailures(): Boolean =
    temporaryFailures.isNotEmpty() || permanentFailures.isNotEmpty()

internal const val HOME_STALE_THRESHOLD_MILLIS = 60L * 60L * 1_000L
