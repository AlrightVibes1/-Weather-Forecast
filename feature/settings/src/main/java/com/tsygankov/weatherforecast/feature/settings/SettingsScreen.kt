package com.tsygankov.weatherforecast.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tsygankov.weatherforecast.feature.settings.R
import com.tsygankov.weatherforecast.ui.R as CoreUiR
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setAutomaticLocationWeather(granted)
    }

    LaunchedEffect(viewModel, onBackClick) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.FavoritesCleared -> onBackClick()
            }
        }
    }

    SettingsScreen(
        state = state,
        onUnitSelected = viewModel::setUnit,
        onThemeSelected = viewModel::setTheme,
        onClearFavorites = viewModel::clearFavorites,
        onBackClick = onBackClick,
        onAutomaticLocationSelected = { enabled ->
            when {
                !enabled -> viewModel.setAutomaticLocationWeather(false)
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ->
                    viewModel.setAutomaticLocationWeather(true)

                else -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUnitSelected: (TemperatureUnit) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onClearFavorites: () -> Unit,
    onBackClick: () -> Unit,
    onAutomaticLocationSelected: (Boolean) -> Unit = {},
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirmation && state.hasFavorites) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.settings_clear_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearFavorites()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_clear_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionTitle(text = stringResource(R.string.settings_temperature_title))
            Column(modifier = Modifier.selectableGroup()) {
                PreferenceRadioRow(
                    label = stringResource(R.string.settings_temperature_celsius),
                    selected = state.settings.temperatureUnit == TemperatureUnit.CELSIUS,
                    onClick = { onUnitSelected(TemperatureUnit.CELSIUS) },
                )
                PreferenceRadioRow(
                    label = stringResource(R.string.settings_temperature_fahrenheit),
                    selected = state.settings.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                    onClick = { onUnitSelected(TemperatureUnit.FAHRENHEIT) },
                )
            }

            HorizontalDivider()

            SettingsSectionTitle(text = stringResource(R.string.settings_theme_title))
            Column(modifier = Modifier.selectableGroup()) {
                PreferenceRadioRow(
                    label = stringResource(R.string.settings_theme_light),
                    selected = state.settings.themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeSelected(ThemeMode.LIGHT) },
                )
                PreferenceRadioRow(
                    label = stringResource(R.string.settings_theme_dark),
                    selected = state.settings.themeMode == ThemeMode.DARK,
                    onClick = { onThemeSelected(ThemeMode.DARK) },
                )
            }

            HorizontalDivider()

            SettingsSectionTitle(text = stringResource(R.string.settings_location_title))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = state.settings.automaticLocationWeather,
                        role = Role.Checkbox,
                        onValueChange = onAutomaticLocationSelected,
                    )
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.settings.automaticLocationWeather,
                    onCheckedChange = null,
                )
                Text(stringResource(R.string.settings_automatic_location_weather))
            }
            Text(
                text = stringResource(R.string.settings_location_permission_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            SettingsSectionTitle(text = stringResource(R.string.settings_data_title))
            Text(
                text = stringResource(R.string.settings_clear_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { showClearConfirmation = true },
                enabled = state.hasFavorites && state.clearState != ClearFavoritesUiState.CLEARING,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.settings_clear_favorites))
            }

            when {
                state.clearState == ClearFavoritesUiState.CLEARING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.settings_clearing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                !state.hasFavorites -> Text(
                    text = stringResource(R.string.settings_nothing_to_clear),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.message?.let { userMessage ->
                Text(
                    text = stringResource(
                        when (userMessage) {
                            SettingsUserMessage.SAVE_FAILED -> R.string.settings_save_failed
                            SettingsUserMessage.CLEAR_FAILED -> R.string.settings_clear_failed
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PreferenceRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
