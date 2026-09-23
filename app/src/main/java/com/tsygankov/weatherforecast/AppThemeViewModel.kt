package com.tsygankov.weatherforecast

import androidx.lifecycle.ViewModel
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class AppThemeViewModel(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
}
