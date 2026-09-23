package com.tsygankov.weatherforecast.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tsygankov.weatherforecast.feature.home.R
import com.tsygankov.weatherforecast.ui.R as CoreUiR
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.ui.weather.WeatherDateTimeFormatter
import com.tsygankov.weatherforecast.ui.weather.formatTemperature
import com.tsygankov.weatherforecast.ui.weather.toWeatherPresentation
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onCityClick: (Long) -> Unit,
    onCurrentLocationClick: () -> Unit = {},
    onPreviewCity: (CitySearchResult) -> Unit = {},
    onSettingsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFailedMessage = stringResource(R.string.home_search_failed)
    val addFailedMessage = stringResource(R.string.home_add_failed)
    val weatherRefreshFailedMessage = stringResource(R.string.home_weather_refresh_failed)
    val staleRefreshFailedMessage = stringResource(R.string.home_stale_refresh_failed)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(
        viewModel,
        snackbarHostState,
        searchFailedMessage,
        addFailedMessage,
        weatherRefreshFailedMessage,
        staleRefreshFailedMessage,
    ) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is HomeEffect.CityAdded -> null
                is HomeEffect.CityAddedWithoutWeather -> when (effect.message) {
                    HomeUserMessage.SEARCH_FAILED -> searchFailedMessage
                    HomeUserMessage.ADD_CITY_FAILED -> addFailedMessage
                    HomeUserMessage.WEATHER_REFRESH_FAILED -> weatherRefreshFailedMessage
                    HomeUserMessage.STALE_REFRESH_FAILED -> staleRefreshFailedMessage
                }

                is HomeEffect.AddCityFailed -> when (effect.message) {
                    HomeUserMessage.SEARCH_FAILED -> searchFailedMessage
                    HomeUserMessage.ADD_CITY_FAILED -> addFailedMessage
                    HomeUserMessage.WEATHER_REFRESH_FAILED -> weatherRefreshFailedMessage
                    HomeUserMessage.STALE_REFRESH_FAILED -> staleRefreshFailedMessage
                }

                is HomeEffect.StaleRefreshFailed -> staleRefreshFailedMessage
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    HomeScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = { viewModel.updateQuery("") },
        onRetrySearch = viewModel::retrySearch,
        onAddCity = viewModel::addCity,
        onPreviewCity = onPreviewCity,
        onCityClick = onCityClick,
        onCurrentLocationClick = onCurrentLocationClick,
        onSettingsClick = onSettingsClick,
        onBackClick = onBackClick,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRetrySearch: () -> Unit,
    onAddCity: (CitySearchResult) -> Unit,
    onPreviewCity: (CitySearchResult) -> Unit = {},
    onCityClick: (Long) -> Unit,
    onCurrentLocationClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
) {
    HomeGradientBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(CoreUiR.string.navigate_back),
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = HomePrimaryText,
                    navigationIconContentColor = HomePrimaryText,
                    actionIconContentColor = HomePrimaryText,
                ),
            )
        },
        snackbarHost = snackbarHost,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag(HOME_CITY_LIST_TEST_TAG),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = SEARCH_FIELD_KEY) {
                SearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                )
            }

            if (state.refreshing) {
                item(key = REFRESH_STATUS_KEY) {
                    RefreshStatus()
                }
            }

            when (val search = state.search) {
                SearchUiState.Idle -> Unit
                is SearchUiState.Loading -> item(key = SEARCH_LOADING_KEY) {
                    SearchLoading()
                }

                is SearchUiState.Results -> {
                    item(key = SEARCH_RESULTS_HEADER_KEY) {
                        SectionTitle(stringResource(R.string.home_search_results_title))
                    }
                    items(
                        items = search.cities,
                        key = { city -> "search_${city.id}" },
                    ) { city ->
                        SearchResultCard(
                            city = city,
                            adding = state.adding,
                            onAddCity = onAddCity,
                            onPreviewCity = onPreviewCity,
                        )
                    }
                }

                is SearchUiState.Empty -> item(key = SEARCH_EMPTY_KEY) {
                    SearchMessage(stringResource(R.string.home_search_empty))
                }

                is SearchUiState.Error -> item(key = SEARCH_ERROR_KEY) {
                    SearchError(
                        message = stringResource(search.message.stringResourceId()),
                        onRetrySearch = onRetrySearch,
                    )
                }
            }

            if (state.showCurrentLocation) {
                item(key = CURRENT_LOCATION_HEADER_KEY) {
                    SectionTitle(stringResource(R.string.home_current_location_title))
                }
                item(key = CURRENT_LOCATION_CARD_KEY) {
                    CurrentLocationCard(
                        cityName = state.currentLocationCityName,
                        onClick = onCurrentLocationClick,
                    )
                }
            }

            if (state.cityItems.isEmpty()) {
                if (state.search is SearchUiState.Idle) {
                    item(key = EMPTY_ITEM_KEY) {
                        EmptyFavorites(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }
            } else {
                item(key = FAVORITES_HEADER_KEY) {
                    SectionTitle(stringResource(R.string.home_favorites_title))
                }
                items(
                    items = state.cityItems,
                    key = { city -> "favorite_${city.id}" },
                ) { city ->
                    FavoriteCityCard(
                        city = city,
                        onClick = { onCityClick(city.id) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CurrentLocationCard(
    cityName: String?,
    onClick: () -> Unit,
) {
    HomeGlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(CURRENT_LOCATION_TEST_TAG),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = cityName ?: stringResource(
                        R.string.home_current_location_unknown,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_current_location_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeSecondaryText,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        shape = HomeCardShape,
        label = { Text(stringResource(R.string.home_search_label)) },
        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.home_search_clear),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = HomePrimaryText,
            unfocusedTextColor = HomePrimaryText,
            focusedContainerColor = HomeGlassSoft,
            unfocusedContainerColor = HomeGlassSoft,
            focusedBorderColor = HomePrimaryText,
            unfocusedBorderColor = HomeGlassBorder,
            focusedLabelColor = HomePrimaryText,
            unfocusedLabelColor = HomeSecondaryText,
            focusedPlaceholderColor = HomeSecondaryText,
            unfocusedPlaceholderColor = HomeSecondaryText,
            focusedLeadingIconColor = HomePrimaryText,
            unfocusedLeadingIconColor = HomeSecondaryText,
            focusedTrailingIconColor = HomePrimaryText,
            unfocusedTrailingIconColor = HomePrimaryText,
            cursorColor = HomePrimaryText,
        ),
    )
}

@Composable
private fun SearchLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
            color = HomePrimaryText,
        )
        Text(
            text = stringResource(R.string.home_search_loading),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun RefreshStatus() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = HomePrimaryText,
        )
        Text(
            text = stringResource(R.string.home_refreshing_weather),
            style = MaterialTheme.typography.bodyMedium,
            color = HomeSecondaryText,
        )
    }
}

@Composable
private fun SearchMessage(message: String) {
    HomeGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = HomeSecondaryText,
        )
    }
}

@Composable
private fun SearchError(
    message: String,
    onRetrySearch: () -> Unit,
) {
    HomeGlassCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = HomePrimaryText,
        )
        Button(
            onClick = onRetrySearch,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HomePrimaryText,
                contentColor = HomeActionContent,
            ),
        ) {
            Text(stringResource(CoreUiR.string.retry))
        }
    }
    }
}

@Composable
private fun SearchResultCard(
    city: CitySearchResult,
    adding: AddCityUiState,
    onAddCity: (CitySearchResult) -> Unit,
    onPreviewCity: (CitySearchResult) -> Unit,
) {
    val addingThisCity = adding == AddCityUiState.Adding(city.id)
    val addEnabled = adding is AddCityUiState.Idle
    HomeGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = city.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            city.searchLocation()?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeSecondaryText,
                )
            }
            Button(
                onClick = { onPreviewCity(city) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("$SEARCH_PREVIEW_TEST_TAG_PREFIX${city.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomePrimaryText,
                    contentColor = HomeActionContent,
                ),
            ) {
                Text(stringResource(R.string.home_city_open_forecast))
            }
            Button(
                onClick = { onAddCity(city) },
                enabled = addEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("$SEARCH_ADD_TEST_TAG_PREFIX${city.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomeGlassSoft,
                    contentColor = HomePrimaryText,
                    disabledContainerColor = HomeGlassSoft,
                    disabledContentColor = HomeSecondaryText,
                ),
            ) {
                if (addingThisCity) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = HomePrimaryText,
                    )
                    Text(stringResource(R.string.home_city_adding))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(stringResource(R.string.home_city_add))
                }
            }
        }
    }
}

private fun CitySearchResult.searchLocation(): String? = listOfNotNull(
    adminArea?.takeIf(String::isNotBlank),
    country?.takeIf(String::isNotBlank),
)
    .distinct()
    .takeIf(List<String>::isNotEmpty)
    ?.joinToString()

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = HomePrimaryText,
    )
}

@Composable
private fun EmptyFavorites(modifier: Modifier = Modifier) {
    HomeGlassCard(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = HomePrimaryText,
        )
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = HomeSecondaryText,
            textAlign = TextAlign.Center,
        )
    }
    }
}

@Composable
private fun FavoriteCityCard(
    city: HomeCityUiModel,
    onClick: () -> Unit,
) {
    HomeGlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = city.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            city.location?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeSecondaryText,
                )
            }
            if (city.temperature == null || city.weatherCondition == null) {
                Text(
                    text = stringResource(R.string.home_weather_not_loaded),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                WeatherSummary(city)
            }
            city.updatedAtMillis?.let { updatedAtMillis ->
                val formattedTime = remember(updatedAtMillis, city.timezone) {
                    WeatherDateTimeFormatter
                        .forTimeZone(primary = city.timezone)
                        .formatLastUpdated(updatedAtMillis)
                }
                Text(
                    text = stringResource(R.string.home_last_updated, formattedTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeSecondaryText,
                )
            }
            if (city.isStale) {
                Text(
                    text = stringResource(R.string.home_stale_data),
                    style = MaterialTheme.typography.labelLarge,
                    color = HomeWarningText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun WeatherSummary(city: HomeCityUiModel) {
    val condition = requireNotNull(city.weatherCondition)
    val presentation = condition.toWeatherPresentation()
    Text(
        text = formatTemperature(
            value = requireNotNull(city.temperature),
            unitSymbol = city.temperatureUnitSymbol,
        ),
        style = MaterialTheme.typography.headlineMedium,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = HomePrimaryText,
        )
        Text(
            text = stringResource(presentation.labelResource),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun HomeGradientBackground(content: @Composable () -> Unit) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gradient = homeGradientFor(isDarkTheme)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient)),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides HomePrimaryText,
            content = content,
        )
    }
}

@Composable
private fun HomeGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = HomeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = HomeGlassStrong,
            contentColor = HomePrimaryText,
        ),
        border = BorderStroke(1.dp, HomeGlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
private fun HomeGlassCard(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = HomeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = HomeGlassStrong,
            contentColor = HomePrimaryText,
        ),
        border = BorderStroke(1.dp, HomeGlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@StringRes
private fun HomeUserMessage.stringResourceId(): Int = when (this) {
    HomeUserMessage.SEARCH_FAILED -> R.string.home_search_failed
    HomeUserMessage.ADD_CITY_FAILED -> R.string.home_add_failed
    HomeUserMessage.WEATHER_REFRESH_FAILED -> R.string.home_weather_refresh_failed
    HomeUserMessage.STALE_REFRESH_FAILED -> R.string.home_stale_refresh_failed
}

private const val SEARCH_FIELD_KEY = "search_field"
private const val REFRESH_STATUS_KEY = "refresh_status"
private const val SEARCH_LOADING_KEY = "search_loading"
private const val SEARCH_RESULTS_HEADER_KEY = "search_results_header"
private const val SEARCH_EMPTY_KEY = "search_empty"
private const val SEARCH_ERROR_KEY = "search_error"
private const val FAVORITES_HEADER_KEY = "favorites_header"
private const val CURRENT_LOCATION_HEADER_KEY = "current_location_header"
private const val CURRENT_LOCATION_CARD_KEY = "current_location_card"
private const val EMPTY_ITEM_KEY = "empty_favorites"
internal const val HOME_CITY_LIST_TEST_TAG = "home_city_list"
internal const val CURRENT_LOCATION_TEST_TAG = "current_location"
internal const val SEARCH_PREVIEW_TEST_TAG_PREFIX = "search_preview_"
internal const val SEARCH_ADD_TEST_TAG_PREFIX = "search_add_"

internal fun homeGradientFor(isDarkTheme: Boolean): List<Color> =
    if (isDarkTheme) DarkHomeGradient else LightHomeGradient

private val LightHomeGradient = listOf(
    Color(0xFF4F61DC),
    Color(0xFF6679D1),
    Color(0xFF4F7395),
)
private val DarkHomeGradient = listOf(
    Color(0xFF141C48),
    Color(0xFF26386E),
    Color(0xFF183B52),
)
private val HomePrimaryText = Color.White
private val HomeSecondaryText = Color.White.copy(alpha = 0.78f)
private val HomeWarningText = Color(0xFFFFE1B0)
private val HomeGlassSoft = Color.White.copy(alpha = 0.10f)
private val HomeGlassStrong = Color.White.copy(alpha = 0.14f)
private val HomeGlassBorder = Color.White.copy(alpha = 0.18f)
private val HomeActionContent = Color(0xFF4054C3)
private val HomeCardShape = RoundedCornerShape(22.dp)
