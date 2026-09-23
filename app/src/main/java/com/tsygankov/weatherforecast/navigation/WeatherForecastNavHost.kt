package com.tsygankov.weatherforecast.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tsygankov.weatherforecast.feature.forecast.ForecastRoute
import com.tsygankov.weatherforecast.feature.forecast.TemporaryCityForecastRoute
import com.tsygankov.weatherforecast.feature.forecast.WeatherStartRoute
import com.tsygankov.weatherforecast.feature.home.HomeRoute
import com.tsygankov.weatherforecast.feature.settings.SettingsRoute
import com.tsygankov.weatherforecast.core.model.CitySearchResult

internal const val HOME_ROUTE = "home"
internal const val WEATHER_START_ROUTE = "weather"
internal const val SETTINGS_ROUTE = "settings"
internal const val FORECAST_CITY_ID_ARGUMENT = "cityId"
internal const val FORECAST_ROUTE = "forecast/{$FORECAST_CITY_ID_ARGUMENT}"
internal const val TEMPORARY_FORECAST_ROUTE = "temporary-forecast/{$FORECAST_CITY_ID_ARGUMENT}"
internal const val NAVIGATION_ANIMATION_DURATION_MILLIS = 220

internal fun forecastRoute(cityId: Long): String = "forecast/$cityId"
internal fun temporaryForecastRoute(cityId: Long): String = "temporary-forecast/$cityId"

internal fun NavController.navigateToForecast(cityId: Long) {
    navigate(forecastRoute(cityId)) {
        launchSingleTop = true
    }
}

internal fun NavController.navigateToTemporaryForecast(cityId: Long) {
    navigate(temporaryForecastRoute(cityId)) {
        launchSingleTop = true
    }
}

internal fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE) {
        launchSingleTop = true
    }
}

internal fun NavController.navigateToCities() {
    navigate(HOME_ROUTE) {
        launchSingleTop = true
    }
}

internal fun NavController.navigateToCurrentWeather() {
    if (!popBackStack(WEATHER_START_ROUTE, inclusive = false)) {
        navigate(WEATHER_START_ROUTE) { launchSingleTop = true }
    }
}

@Composable
internal fun WeatherForecastNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    weatherDestination: @Composable (
        onCitiesClick: () -> Unit,
        onSettingsClick: () -> Unit,
    ) -> Unit = { onCitiesClick, onSettingsClick ->
        WeatherStartRoute(
            onCitiesClick = onCitiesClick,
            onSettingsClick = onSettingsClick,
        )
    },
    homeDestination: @Composable (
        onCityClick: (Long) -> Unit,
        onPreviewCity: (CitySearchResult) -> Unit,
        onCurrentLocationClick: () -> Unit,
        onSettingsClick: () -> Unit,
        onBackClick: () -> Unit,
    ) -> Unit = { onCityClick, onPreviewCity, onCurrentLocationClick, onSettingsClick, onBackClick ->
        HomeRoute(
            onCityClick = onCityClick,
            onPreviewCity = onPreviewCity,
            onCurrentLocationClick = onCurrentLocationClick,
            onSettingsClick = onSettingsClick,
            onBackClick = onBackClick,
        )
    },
    forecastDestination: @Composable (
        cityId: Long,
        onBackClick: () -> Unit,
        onCitiesClick: () -> Unit,
    ) -> Unit = { cityId, onBackClick, onCitiesClick ->
        ForecastRoute(
            cityId = cityId,
            onBackClick = onBackClick,
            onCitiesClick = onCitiesClick,
        )
    },
    temporaryForecastDestination: @Composable (
        city: CitySearchResult,
        onBackClick: () -> Unit,
        onCitiesClick: () -> Unit,
    ) -> Unit = { city, onBackClick, onCitiesClick ->
        TemporaryCityForecastRoute(
            city = city,
            onBackClick = onBackClick,
            onCitiesClick = onCitiesClick,
        )
    },
    settingsDestination: @Composable (
        onBackClick: () -> Unit,
    ) -> Unit = { onBackClick ->
        SettingsRoute(onBackClick = onBackClick)
    },
) {
    var temporaryCityId by rememberSaveable { mutableStateOf<Long?>(null) }
    var temporaryCityName by rememberSaveable { mutableStateOf<String?>(null) }
    var temporaryCityCountry by rememberSaveable { mutableStateOf<String?>(null) }
    var temporaryCityAdminArea by rememberSaveable { mutableStateOf<String?>(null) }
    var temporaryCityLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var temporaryCityLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var temporaryCityTimezone by rememberSaveable { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = WEATHER_START_ROUTE,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAVIGATION_ANIMATION_DURATION_MILLIS),
            ) + fadeIn(tween(NAVIGATION_ANIMATION_DURATION_MILLIS))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAVIGATION_ANIMATION_DURATION_MILLIS),
            ) + fadeOut(tween(NAVIGATION_ANIMATION_DURATION_MILLIS))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAVIGATION_ANIMATION_DURATION_MILLIS),
            ) + fadeIn(tween(NAVIGATION_ANIMATION_DURATION_MILLIS))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAVIGATION_ANIMATION_DURATION_MILLIS),
            ) + fadeOut(tween(NAVIGATION_ANIMATION_DURATION_MILLIS))
        },
    ) {
        composable(WEATHER_START_ROUTE) {
            weatherDestination(
                navController::navigateToCities,
                navController::navigateToSettings,
            )
        }
        composable(HOME_ROUTE) {
            homeDestination(
                navController::navigateToForecast,
                { city ->
                    temporaryCityId = city.id
                    temporaryCityName = city.name
                    temporaryCityCountry = city.country
                    temporaryCityAdminArea = city.adminArea
                    temporaryCityLatitude = city.latitude
                    temporaryCityLongitude = city.longitude
                    temporaryCityTimezone = city.timezone
                    navController.navigateToTemporaryForecast(city.id)
                },
                navController::navigateToCurrentWeather,
                navController::navigateToSettings,
                navController::navigateUp,
            )
        }
        composable(
            route = FORECAST_ROUTE,
            arguments = listOf(
                navArgument(FORECAST_CITY_ID_ARGUMENT) {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val arguments = requireNotNull(backStackEntry.arguments)
            check(arguments.containsKey(FORECAST_CITY_ID_ARGUMENT))
            forecastDestination(
                arguments.getLong(FORECAST_CITY_ID_ARGUMENT),
                navController::navigateUp,
                navController::navigateToCities,
            )
        }
        composable(
            route = TEMPORARY_FORECAST_ROUTE,
            arguments = listOf(
                navArgument(FORECAST_CITY_ID_ARGUMENT) {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val routeCityId = requireNotNull(backStackEntry.arguments)
                .getLong(FORECAST_CITY_ID_ARGUMENT)
            val city = if (
                temporaryCityId == routeCityId &&
                temporaryCityName != null &&
                temporaryCityLatitude != null &&
                temporaryCityLongitude != null &&
                temporaryCityTimezone != null
            ) {
                CitySearchResult(
                    id = routeCityId,
                    name = requireNotNull(temporaryCityName),
                    country = temporaryCityCountry,
                    adminArea = temporaryCityAdminArea,
                    latitude = requireNotNull(temporaryCityLatitude),
                    longitude = requireNotNull(temporaryCityLongitude),
                    timezone = requireNotNull(temporaryCityTimezone),
                )
            } else {
                null
            }
            if (city == null) {
                LaunchedEffect(routeCityId) { navController.navigateUp() }
            } else {
                temporaryForecastDestination(
                    city,
                    navController::navigateUp,
                    navController::navigateToCities,
                )
            }
        }
        composable(SETTINGS_ROUTE) {
            settingsDestination(navController::navigateUp)
        }
    }
}
