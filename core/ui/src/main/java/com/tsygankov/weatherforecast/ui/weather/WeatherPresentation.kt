package com.tsygankov.weatherforecast.ui.weather

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.tsygankov.weatherforecast.ui.R
import com.tsygankov.weatherforecast.core.model.WeatherCondition

data class WeatherPresentation(
    @get:StringRes val labelResource: Int,
    val icon: ImageVector,
)

fun WeatherCondition.toWeatherPresentation(): WeatherPresentation = when (this) {
    WeatherCondition.CLEAR -> WeatherPresentation(
        labelResource = R.string.weather_clear,
        icon = Icons.Outlined.LightMode,
    )

    WeatherCondition.PARTLY_CLOUDY -> WeatherPresentation(
        labelResource = R.string.weather_partly_cloudy,
        icon = Icons.Outlined.Cloud,
    )

    WeatherCondition.CLOUDY -> WeatherPresentation(
        labelResource = R.string.weather_cloudy,
        icon = Icons.Outlined.Cloud,
    )

    WeatherCondition.FOG -> WeatherPresentation(
        labelResource = R.string.weather_fog,
        icon = Icons.Outlined.Grain,
    )

    WeatherCondition.DRIZZLE -> WeatherPresentation(
        labelResource = R.string.weather_drizzle,
        icon = Icons.Outlined.Grain,
    )

    WeatherCondition.RAIN -> WeatherPresentation(
        labelResource = R.string.weather_rain,
        icon = Icons.Outlined.WaterDrop,
    )

    WeatherCondition.SNOW -> WeatherPresentation(
        labelResource = R.string.weather_snow,
        icon = Icons.Outlined.AcUnit,
    )

    WeatherCondition.THUNDERSTORM -> WeatherPresentation(
        labelResource = R.string.weather_thunderstorm,
        icon = Icons.Outlined.Thunderstorm,
    )

    WeatherCondition.UNKNOWN -> WeatherPresentation(
        labelResource = R.string.weather_unknown,
        icon = Icons.AutoMirrored.Outlined.HelpOutline,
    )
}

@Composable
fun formatTemperature(
    value: Int,
    unitSymbol: String,
): String = stringResource(R.string.temperature_value, value, unitSymbol)
