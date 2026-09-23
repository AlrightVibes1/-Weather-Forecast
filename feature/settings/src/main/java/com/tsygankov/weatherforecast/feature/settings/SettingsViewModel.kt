package com.tsygankov.weatherforecast.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ClearFavoritesUiState {
    IDLE,
    CLEARING,
    ERROR,
}

enum class SettingsUserMessage {
    SAVE_FAILED,
    CLEAR_FAILED,
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val hasFavorites: Boolean = false,
    val clearState: ClearFavoritesUiState = ClearFavoritesUiState.IDLE,
    val message: SettingsUserMessage? = null,
)

sealed interface SettingsEffect {
    data object FavoritesCleared : SettingsEffect
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoriteCitiesRepository,
) : ViewModel() {
    private val clearState = MutableStateFlow(ClearFavoritesUiState.IDLE)
    private val message = MutableStateFlow<SettingsUserMessage?>(null)
    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)

    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        favoritesRepository.observeCities().map { cities -> cities.isNotEmpty() },
        clearState,
        message,
    ) { settings, hasFavorites, clearOperation, userMessage ->
        SettingsUiState(
            settings = settings,
            hasFavorites = hasFavorites,
            clearState = clearOperation,
            message = userMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(settings = settingsRepository.settings.value),
    )

    fun setUnit(unit: TemperatureUnit) {
        updateSetting { settingsRepository.setTemperatureUnit(unit) }
    }

    fun setTheme(mode: ThemeMode) {
        updateSetting { settingsRepository.setThemeMode(mode) }
    }

    fun setAutomaticLocationWeather(enabled: Boolean) {
        updateSetting { settingsRepository.setAutomaticLocationWeather(enabled) }
    }

    fun clearFavorites() {
        if (!uiState.value.hasFavorites) return
        val previousState = clearState.value
        if (previousState == ClearFavoritesUiState.CLEARING) return
        if (!clearState.compareAndSet(previousState, ClearFavoritesUiState.CLEARING)) return
        message.value = null

        viewModelScope.launch {
            try {
                favoritesRepository.clear()
                clearState.value = ClearFavoritesUiState.IDLE
                effectChannel.send(SettingsEffect.FavoritesCleared)
            } catch (cancellation: CancellationException) {
                clearState.value = ClearFavoritesUiState.IDLE
                throw cancellation
            } catch (_: Throwable) {
                clearState.value = ClearFavoritesUiState.ERROR
                message.value = SettingsUserMessage.CLEAR_FAILED
            }
        }
    }

    private inline fun updateSetting(update: () -> Unit) {
        try {
            update()
            message.value = null
        } catch (_: Throwable) {
            message.value = SettingsUserMessage.SAVE_FAILED
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
