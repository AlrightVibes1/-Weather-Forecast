package com.tsygankov.weatherforecast.feature.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface WeatherStartUiState {
    data object Loading : WeatherStartUiState
    data object Empty : WeatherStartUiState
    data object Location : WeatherStartUiState
    data class City(val id: Long) : WeatherStartUiState
}

class WeatherStartViewModel(
    favoritesRepository: FavoriteCitiesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<WeatherStartUiState> = combine(
        favoritesRepository.observeCities(),
        settingsRepository.settings,
    ) { cities, settings ->
        if (settings.automaticLocationWeather) {
            WeatherStartUiState.Location
        } else {
            cities.firstOrNull()?.let { WeatherStartUiState.City(it.id) }
                ?: WeatherStartUiState.Empty
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = WeatherStartUiState.Loading,
    )

    fun disableAutomaticLocationWeather() {
        runCatching { settingsRepository.setAutomaticLocationWeather(false) }
    }
}
