package com.tsygankov.weatherforecast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun WeatherForecastAppRoute(
    viewModel: AppThemeViewModel = koinViewModel(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Корневой Composable читает настройки и передаёт выбранную тему всему графу навигации.
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    WeatherForecastApp(
        themeMode = settings.themeMode,
        dynamicColor = dynamicColor,
        content = content,
    )
}

@Composable
internal fun WeatherForecastApp(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    WeatherForecastTheme(
        darkTheme = themeMode == ThemeMode.DARK,
        dynamicColor = dynamicColor,
        content = content,
    )
}
