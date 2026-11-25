package com.tsygankov.weatherforecast.feature.forecast

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import com.tsygankov.weatherforecast.feature.forecast.R
import com.tsygankov.weatherforecast.ui.R as CoreUiR
import com.tsygankov.weatherforecast.ui.weather.formatTemperature
import com.tsygankov.weatherforecast.ui.weather.toWeatherPresentation
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

enum class ForecastTab {
    NOW,
    WEEK,
    MONTH,
}

internal const val FORECAST_HOURLY_LIST_TEST_TAG = "forecast_hourly_list"
internal const val FORECAST_TEMPERATURE_CHART_TEST_TAG = "forecast_temperature_chart"
internal const val FORECAST_PRECIPITATION_CHART_TEST_TAG = "forecast_precipitation_chart"
internal const val FORECAST_DAILY_LIST_TEST_TAG = "forecast_daily_list"
internal const val FORECAST_WEEK_CARD_TEST_TAG_PREFIX = "forecast_week_card_"
internal const val FORECAST_MONTH_LIST_TEST_TAG = "forecast_month_list"
internal const val FORECAST_MONTH_DAY_TEST_TAG_PREFIX = "forecast_month_day_"
internal const val FORECAST_DAY_DETAIL_TEST_TAG = "forecast_day_detail"
internal const val FORECAST_TAB_ANIMATION_DURATION_MILLIS = 160
private const val FORECAST_WEEK_CARD_WIDTH_FRACTION = 0.9f

@Composable
fun ForecastRoute(
    cityId: Long,
    onBackClick: () -> Unit,
    onCitiesClick: () -> Unit = onBackClick,
    isStartScreen: Boolean = false,
    onSettingsClick: () -> Unit = {},
    viewModel: ForecastViewModel = koinViewModel(
        key = "forecast_$cityId",
        parameters = { parametersOf(cityId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Индекс вкладки и дата выбранного дня сохраняются через SavedStateRegistry
    // и восстанавливаются, например, после поворота экрана.
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(ForecastTab.NOW.ordinal) }
    var selectedDaySourceDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTab = remember(selectedTabIndex) {
        ForecastTab.entries.getOrElse(selectedTabIndex) { ForecastTab.NOW }
    }

    ForecastScreen(
        state = state,
        selectedTab = selectedTab,
        onTabSelected = { tab -> selectedTabIndex = tab.ordinal },
        onRetry = viewModel::retry,
        onBackClick = onBackClick,
        isStartScreen = isStartScreen,
        onSettingsClick = onSettingsClick,
        selectedDaySourceDate = selectedDaySourceDate,
        onDaySelected = { sourceDate -> selectedDaySourceDate = sourceDate },
        onDayDetailBack = { selectedDaySourceDate = null },
        onCityTitleClick = onCitiesClick,
    )
}

@Composable
fun WeatherStartRoute(
    onCitiesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: WeatherStartViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
        if (!granted) viewModel.disableAutomaticLocationWeather()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocationPermission =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    when (val currentState = state) {
        WeatherStartUiState.Loading,
        WeatherStartUiState.Empty -> WeatherStartPlaceholderScreen(
            loading = currentState == WeatherStartUiState.Loading,
            onCitiesClick = onCitiesClick,
            onSettingsClick = onSettingsClick,
        )

        is WeatherStartUiState.City -> ForecastRoute(
            cityId = currentState.id,
            onBackClick = onCitiesClick,
            onCitiesClick = onCitiesClick,
            isStartScreen = true,
            onSettingsClick = onSettingsClick,
        )

        WeatherStartUiState.Location -> if (hasLocationPermission) {
            LocationForecastRoute(
                onCitiesClick = onCitiesClick,
                onSettingsClick = onSettingsClick,
            )
        } else {
            WeatherStartPlaceholderScreen(
                loading = false,
                permissionRequired = true,
                onCitiesClick = {
                    viewModel.disableAutomaticLocationWeather()
                    onCitiesClick()
                },
                onSettingsClick = onSettingsClick,
                onPermissionClick = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
            )
        }
    }
}

@Composable
fun LocationForecastRoute(
    onCitiesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LocationForecastViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(ForecastTab.NOW.ordinal) }
    var selectedDaySourceDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTab = remember(selectedTabIndex) {
        ForecastTab.entries.getOrElse(selectedTabIndex) { ForecastTab.NOW }
    }
    LaunchedEffect(viewModel) { viewModel.retry() }

    ForecastScreen(
        state = state,
        selectedTab = selectedTab,
        onTabSelected = { tab -> selectedTabIndex = tab.ordinal },
        onRetry = viewModel::retry,
        onBackClick = onCitiesClick,
        isStartScreen = true,
        onSettingsClick = onSettingsClick,
        selectedDaySourceDate = selectedDaySourceDate,
        onDaySelected = { sourceDate -> selectedDaySourceDate = sourceDate },
        onDayDetailBack = { selectedDaySourceDate = null },
        onCityTitleClick = onCitiesClick,
        titleOverride = state.cityName
            ?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.forecast_current_location),
    )
}

@Composable
fun TemporaryCityForecastRoute(
    city: CitySearchResult,
    onBackClick: () -> Unit,
    onCitiesClick: () -> Unit = onBackClick,
    viewModel: TemporaryCityForecastViewModel = koinViewModel(
        key = "temporary_forecast_${city.id}",
        parameters = { parametersOf(city) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(ForecastTab.NOW.ordinal) }
    var selectedDaySourceDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTab = remember(selectedTabIndex) {
        ForecastTab.entries.getOrElse(selectedTabIndex) { ForecastTab.NOW }
    }
    LaunchedEffect(viewModel) { viewModel.retry() }

    ForecastScreen(
        state = state,
        selectedTab = selectedTab,
        onTabSelected = { tab -> selectedTabIndex = tab.ordinal },
        onRetry = viewModel::retry,
        onBackClick = onBackClick,
        selectedDaySourceDate = selectedDaySourceDate,
        onDaySelected = { sourceDate -> selectedDaySourceDate = sourceDate },
        onDayDetailBack = { selectedDaySourceDate = null },
        onCityTitleClick = onCitiesClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherStartPlaceholderScreen(
    loading: Boolean,
    onCitiesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    permissionRequired: Boolean = false,
    onPermissionClick: () -> Unit = {},
) {
    ForecastGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.forecast_title)) },
                    actions = {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.forecast_open_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = ForecastPrimaryText,
                        actionIconContentColor = ForecastPrimaryText,
                    ),
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(24.dp),
            ) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(color = ForecastPrimaryText)
                                Text(stringResource(R.string.forecast_start_loading))
                            } else if (permissionRequired) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                )
                                Text(
                                    text = stringResource(R.string.forecast_permission_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.forecast_permission_hint),
                                    color = ForecastSecondaryText,
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = onPermissionClick,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ForecastPrimaryText,
                                        contentColor = ForecastActionContent,
                                    ),
                                ) {
                                    Text(stringResource(R.string.forecast_allow_location))
                                }
                                TextButton(
                                    onClick = onCitiesClick,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.forecast_choose_city_manually),
                                        color = ForecastPrimaryText,
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                )
                                Text(
                                    text = stringResource(R.string.forecast_start_empty),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.forecast_start_hint),
                                    color = ForecastSecondaryText,
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = onCitiesClick,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ForecastPrimaryText,
                                        contentColor = ForecastActionContent,
                                    ),
                                ) {
                                    Text(stringResource(R.string.forecast_open_cities))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    state: ForecastUiState,
    selectedTab: ForecastTab,
    onTabSelected: (ForecastTab) -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    isStartScreen: Boolean = false,
    onSettingsClick: () -> Unit = {},
    titleOverride: String? = null,
    selectedDaySourceDate: String? = null,
    onDaySelected: (String) -> Unit = {},
    onDayDetailBack: () -> Unit = {},
    onCityTitleClick: () -> Unit = {},
) {
    val selectedDay = state.findDay(selectedDaySourceDate)
    BackHandler(enabled = selectedDay != null, onBack = onDayDetailBack)
    ForecastGradientBackground(
        condition = selectedDay?.condition ?: state.backgroundWeatherCondition(),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        TextButton(
                            onClick = onCityTitleClick,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { heading() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = ForecastPrimaryText,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = titleOverride ?: state.cityName
                                    ?: stringResource(R.string.forecast_title),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = if (selectedDay != null) onDayDetailBack else onBackClick,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (isStartScreen && selectedDay == null) {
                                    Icons.Outlined.Search
                                } else {
                                    Icons.AutoMirrored.Outlined.ArrowBack
                                },
                                contentDescription = if (isStartScreen && selectedDay == null) {
                                    stringResource(R.string.forecast_open_cities)
                                } else {
                                    stringResource(CoreUiR.string.navigate_back)
                                },
                            )
                        }
                    },
                    actions = {
                        if (isStartScreen) {
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(
                                        R.string.forecast_open_settings,
                                    ),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = ForecastPrimaryText,
                        titleContentColor = ForecastPrimaryText,
                        actionIconContentColor = ForecastPrimaryText,
                    ),
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                if (selectedDay != null) {
                    ForecastDayDetailsForState(
                        state = state,
                        day = selectedDay,
                        onRetry = onRetry,
                    )
                } else {
                    ForecastTabs(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                    )
                    ForecastStateContent(
                        state = state,
                        selectedTab = selectedTab,
                        onRetry = onRetry,
                        onDaySelected = onDaySelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastStateContent(
    state: ForecastUiState,
    selectedTab: ForecastTab,
    onRetry: () -> Unit,
    onDaySelected: (String) -> Unit,
) {
    when (state) {
        is ForecastUiState.InitialLoading -> ForecastLoading()
        is ForecastUiState.Content -> ForecastContent(
            content = state.content,
            temperatureUnitSymbol = state.temperatureUnit.symbol,
            selectedTab = selectedTab,
            notice = ForecastContentNotice.NONE,
            onRetry = onRetry,
            onDaySelected = onDaySelected,
        )

        is ForecastUiState.RefreshingWithContent -> ForecastContent(
            content = state.content,
            temperatureUnitSymbol = state.temperatureUnit.symbol,
            selectedTab = selectedTab,
            notice = ForecastContentNotice.REFRESHING,
            onRetry = onRetry,
            onDaySelected = onDaySelected,
        )

        is ForecastUiState.ErrorWithoutCache -> ForecastBlockingError(
            message = state.message,
            onRetry = onRetry,
        )
        is ForecastUiState.ErrorWithCache -> ForecastContent(
            content = state.content,
            temperatureUnitSymbol = state.temperatureUnit.symbol,
            selectedTab = selectedTab,
            notice = ForecastContentNotice.CACHED_ERROR,
            onRetry = onRetry,
            onDaySelected = onDaySelected,
        )

        is ForecastUiState.CityNotFound -> ForecastCityNotFound()
    }
}

@Composable
private fun ForecastTabs(
    selectedTab: ForecastTab,
    onTabSelected: (ForecastTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(ForecastTabBarShape)
            .background(ForecastGlassSoft)
            .padding(4.dp),
    ) {
        ForecastTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Tab(
                selected = selected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(ForecastTabShape)
                    .background(
                        if (selected) ForecastGlassSelected else Color.Transparent,
                    ),
                selectedContentColor = ForecastPrimaryText,
                unselectedContentColor = ForecastSecondaryText,
                text = {
                    Text(
                        when (tab) {
                            ForecastTab.NOW -> stringResource(R.string.forecast_tab_now)
                            ForecastTab.WEEK -> stringResource(R.string.forecast_tab_week)
                            ForecastTab.MONTH -> stringResource(R.string.forecast_tab_month)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ForecastContent(
    content: ForecastContentUiModel,
    temperatureUnitSymbol: String,
    selectedTab: ForecastTab,
    notice: ForecastContentNotice,
    onRetry: () -> Unit,
    onDaySelected: (String) -> Unit,
) {
    AnimatedContent(
        targetState = selectedTab,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(tween(durationMillis = FORECAST_TAB_ANIMATION_DURATION_MILLIS)) togetherWith
                fadeOut(tween(durationMillis = FORECAST_TAB_ANIMATION_DURATION_MILLIS))
        },
        label = "forecast_tab_content",
    ) { animatedTab ->
        when (animatedTab) {
            ForecastTab.NOW -> ForecastNowContent(
                content = content,
                temperatureUnitSymbol = temperatureUnitSymbol,
                notice = notice,
                onRetry = onRetry,
            )

            ForecastTab.WEEK -> ForecastWeekContent(
                content = content,
                temperatureUnitSymbol = temperatureUnitSymbol,
                notice = notice,
                onRetry = onRetry,
                onDaySelected = onDaySelected,
            )

            ForecastTab.MONTH -> ForecastMonthContent(
                content = content,
                temperatureUnitSymbol = temperatureUnitSymbol,
                notice = notice,
                onRetry = onRetry,
                onDaySelected = onDaySelected,
            )
        }
    }
}

@Composable
private fun ForecastLoading() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        item {
            CircularProgressIndicator(color = ForecastPrimaryText)
        }
        item {
            Text(
                text = stringResource(R.string.forecast_loading),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ForecastBlockingError(
    message: ForecastUserMessage,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        item {
            Text(
                text = stringResource(
                    when (message) {
                        ForecastUserMessage.LOAD_FAILED -> R.string.forecast_load_failed
                        ForecastUserMessage.LOCATION_UNAVAILABLE ->
                            R.string.forecast_location_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForecastPrimaryText,
                    contentColor = ForecastActionContent,
                ),
            ) {
                Text(stringResource(CoreUiR.string.retry))
            }
        }
    }
}

@Composable
private fun ForecastCityNotFound() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        item {
            Text(
                text = stringResource(R.string.forecast_city_not_found),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                text = stringResource(R.string.forecast_city_not_found_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ForecastNowContent(
    content: ForecastContentUiModel,
    temperatureUnitSymbol: String,
    notice: ForecastContentNotice,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForecastNotice(notice = notice, onRetry = onRetry)

        CurrentConditionsCard(
            current = content.current,
            temperatureUnitSymbol = temperatureUnitSymbol,
        )

        Text(
            text = stringResource(R.string.forecast_hourly_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (content.hourly.isEmpty()) {
            Text(
                text = stringResource(R.string.forecast_hourly_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FORECAST_HOURLY_LIST_TEST_TAG),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = content.hourly,
                        key = HourlyForecastUiModel::sourceTime,
                    ) { hour ->
                        HourlyForecastCard(
                            hour = hour,
                            temperatureUnitSymbol = temperatureUnitSymbol,
                        )
                    }
                }
            }
            HourlyForecastCharts(
                hours = content.hourly,
                temperatureUnitSymbol = temperatureUnitSymbol,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun HourlyForecastCharts(
    hours: List<HourlyForecastUiModel>,
    temperatureUnitSymbol: String,
) {
    val chartHours = remember(hours) { hours.take(FORECAST_CHART_HOURLY_LIMIT) }
    val temperaturePointDescriptions = mutableListOf<String>()
    val precipitationPointDescriptions = mutableListOf<String>()
    chartHours.forEach { hour ->
        temperaturePointDescriptions += stringResource(
            R.string.forecast_temperature_chart_point,
            hour.compactTimeLabel,
            formatTemperature(hour.temperature, temperatureUnitSymbol),
        )
        precipitationPointDescriptions += stringResource(
            R.string.forecast_precipitation_chart_point,
            hour.compactTimeLabel,
            hour.precipitationProbability,
        )
    }
    val temperatureSummary = temperaturePointDescriptions.joinToString(separator = "; ")
    val precipitationSummary = precipitationPointDescriptions.joinToString(separator = "; ")

    TemperatureBarChart(
        hours = chartHours,
        temperatureUnitSymbol = temperatureUnitSymbol,
        accessibilitySummary = stringResource(
            R.string.forecast_temperature_chart_accessibility,
            temperatureSummary,
        ),
    )
    PrecipitationBarChart(
        hours = chartHours,
        accessibilitySummary = stringResource(
            R.string.forecast_precipitation_chart_accessibility,
            precipitationSummary,
        ),
    )
}

@Composable
private fun TemperatureBarChart(
    hours: List<HourlyForecastUiModel>,
    temperatureUnitSymbol: String,
    accessibilitySummary: String,
) {
    val minimum = hours.minOf(HourlyForecastUiModel::temperature)
    val maximum = hours.maxOf(HourlyForecastUiModel::temperature)
    val range = (maximum - minimum).coerceAtLeast(1)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FORECAST_TEMPERATURE_CHART_TEST_TAG)
            .clearAndSetSemantics { contentDescription = accessibilitySummary },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChartHeader(
                title = stringResource(R.string.forecast_temperature_chart_title),
                hours = hours.size,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_CONTENT_HEIGHT)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                hours.forEach { hour ->
                    val normalized = (hour.temperature - minimum).toFloat() / range
                    val barHeight = CHART_BAR_MIN_HEIGHT +
                        (CHART_BAR_MAX_HEIGHT - CHART_BAR_MIN_HEIGHT) * normalized
                    HourlyChartColumn(
                        value = formatTemperature(hour.temperature, temperatureUnitSymbol),
                        time = hour.compactTimeLabel,
                        barHeight = barHeight,
                        barColor = ForecastPrimaryText,
                        modifier = Modifier.widthIn(min = CHART_POINT_MIN_WIDTH),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecipitationBarChart(
    hours: List<HourlyForecastUiModel>,
    accessibilitySummary: String,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FORECAST_PRECIPITATION_CHART_TEST_TAG)
            .clearAndSetSemantics { contentDescription = accessibilitySummary },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChartHeader(
                title = stringResource(R.string.forecast_precipitation_chart_title),
                hours = hours.size,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_CONTENT_HEIGHT)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                hours.forEach { hour ->
                    val normalized = hour.precipitationProbability.coerceIn(0, 100) / 100f
                    val barHeight = CHART_BAR_MIN_HEIGHT +
                        (CHART_BAR_MAX_HEIGHT - CHART_BAR_MIN_HEIGHT) * normalized
                    HourlyChartColumn(
                        value = stringResource(
                            R.string.forecast_precipitation_value,
                            hour.precipitationProbability,
                        ),
                        time = hour.compactTimeLabel,
                        barHeight = barHeight,
                        barColor = ForecastPrimaryText.copy(alpha = 0.66f),
                        modifier = Modifier.widthIn(min = CHART_POINT_MIN_WIDTH),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartHeader(
    title: String,
    hours: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.forecast_hourly_chart_period, hours),
            style = MaterialTheme.typography.labelMedium,
            color = ForecastSecondaryText,
        )
    }
}

@Composable
private fun HourlyChartColumn(
    value: String,
    time: String,
    barHeight: androidx.compose.ui.unit.Dp,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(barColor),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = ForecastSecondaryText,
            maxLines = 1,
        )
    }
}

@Composable
private fun ForecastDayDetailsForState(
    state: ForecastUiState,
    day: DailyForecastUiModel,
    onRetry: () -> Unit,
) {
    when (state) {
        is ForecastUiState.Content -> ForecastDayDetails(
            day = day,
            temperatureUnitSymbol = state.temperatureUnit.symbol,
            notice = ForecastContentNotice.NONE,
            onRetry = onRetry,
        )
        is ForecastUiState.RefreshingWithContent -> ForecastDayDetails(
            day = day,
            temperatureUnitSymbol = state.temperatureUnit.symbol,
            notice = ForecastContentNotice.REFRESHING,
            onRetry = onRetry,
        )
        is ForecastUiState.ErrorWithCache -> ForecastDayDetails(
            day = day,
            temperatureUnitSymbol = state.temperatureUnit.symbol,
            notice = ForecastContentNotice.CACHED_ERROR,
            onRetry = onRetry,
        )
        is ForecastUiState.InitialLoading,
        is ForecastUiState.ErrorWithoutCache,
        is ForecastUiState.CityNotFound,
        -> Unit
    }
}

@Composable
private fun ForecastDayDetails(
    day: DailyForecastUiModel,
    temperatureUnitSymbol: String,
    notice: ForecastContentNotice,
    onRetry: () -> Unit,
) {
    val presentation = day.condition.toWeatherPresentation()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(FORECAST_DAY_DETAIL_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForecastNotice(notice = notice, onRetry = onRetry)
        Text(
            text = if (day.isToday) {
                stringResource(R.string.forecast_today)
            } else {
                day.dayOfWeekLabel
            },
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = day.dateLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = ForecastSecondaryText,
        )
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = ForecastPrimaryText,
        )
        Text(
            text = stringResource(
                R.string.forecast_day_temperature_range,
                formatTemperature(day.minTemperature, temperatureUnitSymbol),
                formatTemperature(day.maxTemperature, temperatureUnitSymbol),
            ),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(presentation.labelResource),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DailyMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.forecast_daily_minimum),
                    value = formatTemperature(day.minTemperature, temperatureUnitSymbol),
                )
                DailyMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.forecast_daily_maximum),
                    value = formatTemperature(day.maxTemperature, temperatureUnitSymbol),
                )
                DailyMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.forecast_daily_precipitation),
                    value = stringResource(
                        R.string.forecast_precipitation_value,
                        day.precipitationProbability,
                    ),
                )
            }
        }
        if (day.sunriseLabel != null || day.sunsetLabel != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DailyMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.forecast_sunrise),
                        value = day.sunriseLabel ?: stringResource(R.string.forecast_value_unavailable),
                    )
                    DailyMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.forecast_sunset),
                        value = day.sunsetLabel ?: stringResource(R.string.forecast_value_unavailable),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.forecast_day_real_data_notice),
            style = MaterialTheme.typography.bodySmall,
            color = ForecastSecondaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ForecastWeekContent(
    content: ForecastContentUiModel,
    temperatureUnitSymbol: String,
    notice: ForecastContentNotice,
    onRetry: () -> Unit,
    onDaySelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FORECAST_DAILY_LIST_TEST_TAG),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ForecastNotice(notice = notice, onRetry = onRetry)
        }
        if (content.weekDays.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.forecast_daily_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(
                items = content.weekDays,
                key = DailyForecastUiModel::sourceDate,
            ) { day ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    DailyForecastCard(
                        day = day,
                        temperatureUnitSymbol = temperatureUnitSymbol,
                        modifier = Modifier
                            .fillMaxWidth(FORECAST_WEEK_CARD_WIDTH_FRACTION)
                            .widthIn(max = 520.dp)
                            .testTag("$FORECAST_WEEK_CARD_TEST_TAG_PREFIX${day.sourceDate}"),
                        compact = true,
                        onClick = { onDaySelected(day.sourceDate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastMonthContent(
    content: ForecastContentUiModel,
    temperatureUnitSymbol: String,
    notice: ForecastContentNotice,
    onRetry: () -> Unit,
    onDaySelected: (String) -> Unit,
) {
    val calendar = remember(content.monthDays) {
        content.monthDays.toMonthCalendarUiModel()
    }
    val weekdayLabels = stringArrayResource(R.array.forecast_weekday_short_labels)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val calendarWidth = maxWidth.coerceAtLeast(MONTH_CALENDAR_MIN_WIDTH)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .width(calendarWidth)
                    .fillMaxHeight()
                    .testTag(FORECAST_MONTH_LIST_TEST_TAG),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
        item(
            key = MONTH_AVAILABILITY_KEY,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = stringResource(R.string.forecast_month_availability),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item(
            key = MONTH_WARNING_KEY,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.forecast_month_accuracy_warning),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (notice != ForecastContentNotice.NONE) {
            item(
                key = MONTH_NOTICE_KEY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                ForecastNotice(notice = notice, onRetry = onRetry)
            }
        }

        if (calendar == null) {
            item(
                key = MONTH_EMPTY_KEY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = stringResource(R.string.forecast_month_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item(
                key = MONTH_TITLE_KEY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = calendar.monthLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            weekdayLabels.forEachIndexed { index, label ->
                item(key = "weekday_$index") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = ForecastSecondaryText,
                        )
                    }
                }
            }
            repeat(calendar.leadingEmptyDays) { index ->
                item(key = "month_empty_leading_$index") {
                    Spacer(modifier = Modifier.height(MONTH_CALENDAR_DAY_HEIGHT))
                }
            }
            gridItems(
                items = calendar.days,
                key = ForecastCalendarDayUiModel::sourceDate,
            ) { calendarDay ->
                ForecastCalendarDay(
                    day = calendarDay,
                    temperatureUnitSymbol = temperatureUnitSymbol,
                    onClick = onDaySelected,
                )
            }
            }
        }
    }
}
}

@Composable
private fun ForecastCalendarDay(
    day: ForecastCalendarDayUiModel,
    temperatureUnitSymbol: String,
    onClick: (String) -> Unit,
) {
    val forecast = day.forecast
    val availabilityDescription = if (forecast == null) {
        stringResource(R.string.forecast_calendar_day_unavailable, day.dayOfMonth)
    } else {
        stringResource(
            R.string.forecast_calendar_day_available,
            day.dayOfMonth,
            formatTemperature(forecast.minTemperature, temperatureUnitSymbol),
            formatTemperature(forecast.maxTemperature, temperatureUnitSymbol),
        )
    }
    Card(
        onClick = { forecast?.let { onClick(it.sourceDate) } },
        enabled = forecast != null,
        modifier = Modifier
            .fillMaxWidth()
            .height(MONTH_CALENDAR_DAY_HEIGHT)
            .testTag("$FORECAST_MONTH_DAY_TEST_TAG_PREFIX${day.sourceDate}")
            .semantics { contentDescription = availabilityDescription },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (forecast == null) {
                ForecastGlassSoft
            } else {
                ForecastGlassStrong
            },
            contentColor = ForecastPrimaryText,
            disabledContainerColor = ForecastGlassSoft,
            disabledContentColor = ForecastSecondaryText,
        ),
        border = BorderStroke(1.dp, ForecastGlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (forecast?.isToday == true) FontWeight.Bold else FontWeight.Medium,
            )
            if (forecast == null) {
                Text(
                    text = stringResource(R.string.forecast_value_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Icon(
                    imageVector = forecast.condition.toWeatherPresentation().icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        R.string.forecast_calendar_temperature_range,
                        forecast.minTemperature,
                        forecast.maxTemperature,
                        temperatureUnitSymbol,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ForecastNotice(
    notice: ForecastContentNotice,
    onRetry: () -> Unit,
) {
    when (notice) {
        ForecastContentNotice.NONE -> Unit
        ForecastContentNotice.REFRESHING -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.forecast_refreshing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ForecastPrimaryText,
                    trackColor = ForecastGlassSelected,
                )
            }
        }

        ForecastContentNotice.CACHED_ERROR -> {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.forecast_cached_error),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(CoreUiR.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentConditionsCard(
    current: CurrentForecastUiModel,
    temperatureUnitSymbol: String,
) {
    val presentation = current.condition.toWeatherPresentation()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        current.localTimeLabel?.takeIf(String::isNotBlank)?.let { localTimeLabel ->
            Text(
                text = localTimeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = ForecastSecondaryText,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                tint = ForecastPrimaryText,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatTemperature(
                    value = current.temperature,
                    unitSymbol = temperatureUnitSymbol,
                ),
                fontSize = 72.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Text(
            text = stringResource(presentation.labelResource),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.forecast_feels_like),
                style = MaterialTheme.typography.bodyLarge,
                color = ForecastSecondaryText,
            )
            Text(
                text = formatTemperature(
                    value = current.apparentTemperature,
                    unitSymbol = temperatureUnitSymbol,
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrentMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.WaterDrop,
                    label = stringResource(R.string.forecast_humidity),
                    value = stringResource(
                        R.string.forecast_humidity_value,
                        current.humidityPercent,
                    ),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(56.dp)
                        .background(ForecastDivider),
                )
                CurrentMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Air,
                    label = stringResource(R.string.forecast_wind),
                    value = stringResource(R.string.forecast_wind_value, current.windSpeed),
                )
            }
            HorizontalDivider(color = ForecastDivider)
            ForecastMetric(
                icon = Icons.Outlined.Speed,
                label = stringResource(R.string.forecast_pressure),
                value = stringResource(R.string.forecast_pressure_value, current.pressure),
            )
        }
    }
}

@Composable
private fun CurrentMetricTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = ForecastSecondaryText,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = ForecastSecondaryText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ForecastMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = ForecastSecondaryText,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = ForecastSecondaryText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HourlyForecastCard(
    hour: HourlyForecastUiModel,
    temperatureUnitSymbol: String,
) {
    val presentation = hour.condition.toWeatherPresentation()
    Column(
        modifier = Modifier
            .widthIn(min = 92.dp)
            .heightIn(min = 126.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = hour.timeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = ForecastSecondaryText,
            textAlign = TextAlign.Center,
        )
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = ForecastPrimaryText,
        )
        Text(
            text = formatTemperature(
                value = hour.temperature,
                unitSymbol = temperatureUnitSymbol,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(presentation.labelResource),
            style = MaterialTheme.typography.bodySmall,
            color = ForecastSecondaryText,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun DailyForecastCard(
    day: DailyForecastUiModel,
    temperatureUnitSymbol: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val presentation = day.condition.toWeatherPresentation()
    val contentPadding = if (compact) 12.dp else 16.dp
    val contentSpacing = if (compact) 8.dp else 12.dp
    val rowSpacing = if (compact) 8.dp else 12.dp
    val iconSize = if (compact) 28.dp else 32.dp
    GlassCard(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (day.isToday) {
                            stringResource(R.string.forecast_today)
                        } else {
                            day.dayOfWeekLabel
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = day.dateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = ForecastPrimaryText,
                )
                Text(
                    text = stringResource(presentation.labelResource),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider(color = ForecastDivider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                DailyMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.forecast_daily_minimum),
                    value = formatTemperature(
                        value = day.minTemperature,
                        unitSymbol = temperatureUnitSymbol,
                    ),
                )
                DailyMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.forecast_daily_maximum),
                    value = formatTemperature(
                        value = day.maxTemperature,
                        unitSymbol = temperatureUnitSymbol,
                    ),
                )
                DailyMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.forecast_daily_precipitation),
                    value = stringResource(
                        R.string.forecast_precipitation_value,
                        day.precipitationProbability,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DailyMetric(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ForecastSecondaryText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ForecastGradientBackground(
    condition: WeatherCondition? = null,
    content: @Composable () -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gradient = forecastGradientColors(
        condition = condition,
        isDarkTheme = isDarkTheme,
    )
    val visualColors = forecastVisualColors(condition, isDarkTheme)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient)),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides visualColors.primaryText,
            LocalForecastVisualColors provides visualColors,
            content = content,
        )
    }
}

internal fun ForecastUiState.backgroundWeatherCondition(): WeatherCondition? = when (this) {
    is ForecastUiState.Content -> content.current.condition
    is ForecastUiState.RefreshingWithContent -> content.current.condition
    is ForecastUiState.ErrorWithCache -> content.current.condition
    is ForecastUiState.InitialLoading,
    is ForecastUiState.ErrorWithoutCache,
    is ForecastUiState.CityNotFound,
    -> null
}

private fun ForecastUiState.findDay(sourceDate: String?): DailyForecastUiModel? {
    if (sourceDate == null) return null
    val content = when (this) {
        is ForecastUiState.Content -> content
        is ForecastUiState.RefreshingWithContent -> content
        is ForecastUiState.ErrorWithCache -> content
        is ForecastUiState.InitialLoading,
        is ForecastUiState.ErrorWithoutCache,
        is ForecastUiState.CityNotFound,
        -> null
    }
    return content?.weekDays?.firstOrNull { day -> day.sourceDate == sourceDate }
        ?: content?.monthDays?.firstOrNull { day -> day.sourceDate == sourceDate }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = ForecastCardShape,
        colors = CardDefaults.cardColors(
            containerColor = ForecastGlassStrong,
            contentColor = ForecastPrimaryText,
        ),
        border = BorderStroke(1.dp, ForecastGlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

private enum class ForecastContentNotice {
    NONE,
    REFRESHING,
    CACHED_ERROR,
}

private val LocalForecastVisualColors = staticCompositionLocalOf {
    forecastVisualColors(condition = null, isDarkTheme = false)
}

private val ForecastPrimaryText: Color
    @Composable get() = LocalForecastVisualColors.current.primaryText
private val ForecastSecondaryText: Color
    @Composable get() = LocalForecastVisualColors.current.secondaryText
private val ForecastGlassSoft: Color
    @Composable get() = LocalForecastVisualColors.current.glassSoft
private val ForecastGlassStrong: Color
    @Composable get() = LocalForecastVisualColors.current.glassStrong
private val ForecastGlassSelected: Color
    @Composable get() = LocalForecastVisualColors.current.glassSelected
private val ForecastGlassBorder: Color
    @Composable get() = LocalForecastVisualColors.current.glassBorder
private val ForecastDivider: Color
    @Composable get() = LocalForecastVisualColors.current.divider
private val ForecastActionContent: Color
    @Composable get() = LocalForecastVisualColors.current.actionContent
private val ForecastCardShape = RoundedCornerShape(22.dp)
private val ForecastTabBarShape = RoundedCornerShape(26.dp)
private val ForecastTabShape = RoundedCornerShape(22.dp)
private val CHART_CONTENT_HEIGHT = 128.dp
private val CHART_POINT_MIN_WIDTH = 46.dp
private val CHART_BAR_MIN_HEIGHT = 18.dp
private val CHART_BAR_MAX_HEIGHT = 76.dp
private val MONTH_CALENDAR_MIN_WIDTH = 360.dp
private val MONTH_CALENDAR_DAY_HEIGHT = 56.dp

private const val FORECAST_CHART_HOURLY_LIMIT = 6
private const val MONTH_AVAILABILITY_KEY = "month_availability"
private const val MONTH_WARNING_KEY = "month_warning"
private const val MONTH_NOTICE_KEY = "month_notice"
private const val MONTH_EMPTY_KEY = "month_empty"
private const val MONTH_TITLE_KEY = "month_title"
