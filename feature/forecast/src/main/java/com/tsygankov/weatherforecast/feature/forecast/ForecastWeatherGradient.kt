package com.tsygankov.weatherforecast.feature.forecast

import androidx.compose.ui.graphics.Color
import com.tsygankov.weatherforecast.core.model.WeatherCondition

internal data class ForecastVisualColors(
    val primaryText: Color,
    val secondaryText: Color,
    val glassSoft: Color,
    val glassStrong: Color,
    val glassSelected: Color,
    val glassBorder: Color,
    val divider: Color,
    val actionContent: Color,
)

internal fun forecastGradientColors(
    condition: WeatherCondition?,
    isDarkTheme: Boolean,
): List<Color> = when (condition) {
    WeatherCondition.CLEAR -> if (isDarkTheme) DarkClearGradient else LightClearGradient
    WeatherCondition.PARTLY_CLOUDY ->
        if (isDarkTheme) DarkPartlyCloudyGradient else LightPartlyCloudyGradient

    WeatherCondition.CLOUDY -> if (isDarkTheme) DarkCloudyGradient else LightCloudyGradient
    WeatherCondition.FOG -> if (isDarkTheme) DarkFogGradient else LightFogGradient
    WeatherCondition.DRIZZLE -> if (isDarkTheme) DarkDrizzleGradient else LightDrizzleGradient
    WeatherCondition.RAIN -> if (isDarkTheme) DarkRainGradient else LightRainGradient
    WeatherCondition.SNOW -> if (isDarkTheme) DarkSnowGradient else LightSnowGradient
    WeatherCondition.THUNDERSTORM ->
        if (isDarkTheme) DarkThunderstormGradient else LightThunderstormGradient

    WeatherCondition.UNKNOWN,
    null,
    -> if (isDarkTheme) DarkNeutralGradient else LightNeutralGradient
}

internal fun forecastVisualColors(
    condition: WeatherCondition?,
    isDarkTheme: Boolean,
): ForecastVisualColors {
    val usesDarkInk = !isDarkTheme && condition in LightBackgroundConditions
    val primary = if (usesDarkInk) Color(0xFF18212A) else Color.White
    return ForecastVisualColors(
        primaryText = primary,
        secondaryText = primary.copy(alpha = if (usesDarkInk) 0.76f else 0.78f),
        glassSoft = if (usesDarkInk) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.10f),
        glassStrong = if (usesDarkInk) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f),
        glassSelected = if (usesDarkInk) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.20f),
        glassBorder = if (usesDarkInk) Color.Black.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.18f),
        divider = if (usesDarkInk) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.24f),
        actionContent = if (usesDarkInk) Color.White else Color(0xFF4054C3),
    )
}

private val LightBackgroundConditions = setOf(
    WeatherCondition.CLEAR,
    WeatherCondition.PARTLY_CLOUDY,
    WeatherCondition.CLOUDY,
    WeatherCondition.FOG,
    WeatherCondition.SNOW,
)

private val LightClearGradient = listOf(
    Color(0xFFFFF176),
    Color(0xFFFFD54F),
    Color(0xFFFFB74D),
)
private val DarkClearGradient = listOf(
    Color(0xFF6A4700),
    Color(0xFF8A5900),
    Color(0xFF573700),
)
private val LightPartlyCloudyGradient = listOf(
    Color(0xFFB8E3F4),
    Color(0xFFA8C8D8),
    Color(0xFF8EA9B8),
)
private val DarkPartlyCloudyGradient = listOf(
    Color(0xFF263B4A),
    Color(0xFF354E5E),
    Color(0xFF21313D),
)
private val LightCloudyGradient = listOf(
    Color(0xFFC4C9CF),
    Color(0xFFA9B0B8),
    Color(0xFF8F98A2),
)
private val DarkCloudyGradient = listOf(
    Color(0xFF303740),
    Color(0xFF424A54),
    Color(0xFF252B32),
)
private val LightFogGradient = listOf(
    Color(0xFFD5D9DD),
    Color(0xFFBEC4C9),
    Color(0xFFA7B0B7),
)
private val DarkFogGradient = listOf(
    Color(0xFF3B454C),
    Color(0xFF4C585F),
    Color(0xFF30383E),
)
private val LightDrizzleGradient = listOf(
    Color(0xFF3F7088),
    Color(0xFF315F76),
    Color(0xFF244B62),
)
private val DarkDrizzleGradient = listOf(
    Color(0xFF173444),
    Color(0xFF1F4659),
    Color(0xFF102936),
)
private val LightRainGradient = listOf(
    Color(0xFF245C85),
    Color(0xFF1B496C),
    Color(0xFF10344F),
)
private val DarkRainGradient = listOf(
    Color(0xFF0C2539),
    Color(0xFF12384F),
    Color(0xFF081B2B),
)
private val LightSnowGradient = listOf(
    Color(0xFFD9F3FF),
    Color(0xFFB9E4F3),
    Color(0xFF91C8DF),
)
private val DarkSnowGradient = listOf(
    Color(0xFF244553),
    Color(0xFF315E6E),
    Color(0xFF1A3541),
)
private val LightThunderstormGradient = listOf(
    Color(0xFF35204F),
    Color(0xFF512B6E),
    Color(0xFF20152F),
)
private val DarkThunderstormGradient = listOf(
    Color(0xFF140D22),
    Color(0xFF28133B),
    Color(0xFF0C0916),
)
private val LightNeutralGradient = listOf(
    Color(0xFF3F50AA),
    Color(0xFF5065A8),
    Color(0xFF3A5C73),
)
private val DarkNeutralGradient = listOf(
    Color(0xFF141C48),
    Color(0xFF26386E),
    Color(0xFF183B52),
)
