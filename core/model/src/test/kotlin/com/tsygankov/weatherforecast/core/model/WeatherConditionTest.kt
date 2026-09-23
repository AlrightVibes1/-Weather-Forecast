package com.tsygankov.weatherforecast.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionTest {
    @Test
    fun `all documented Open Meteo WMO codes map to a condition`() {
        val codesByCondition = mapOf(
            WeatherCondition.CLEAR to listOf(0, 1),
            WeatherCondition.PARTLY_CLOUDY to listOf(2),
            WeatherCondition.CLOUDY to listOf(3),
            WeatherCondition.FOG to listOf(45, 48),
            WeatherCondition.DRIZZLE to listOf(51, 53, 55, 56, 57),
            WeatherCondition.RAIN to listOf(61, 63, 65, 66, 67, 80, 81, 82),
            WeatherCondition.SNOW to listOf(71, 73, 75, 77, 85, 86),
            WeatherCondition.THUNDERSTORM to listOf(95, 96, 99),
        )

        codesByCondition.forEach { (expectedCondition, codes) ->
            codes.forEach { code ->
                assertEquals(
                    "Unexpected condition for WMO code $code",
                    expectedCondition,
                    WeatherCondition.fromWmo(code),
                )
            }
        }
    }

    @Test
    fun `missing and undocumented WMO codes are unknown`() {
        listOf(null, -1, 4, 44, 100, Int.MAX_VALUE).forEach { code ->
            assertEquals(
                "WMO code $code must be neutral",
                WeatherCondition.UNKNOWN,
                WeatherCondition.fromWmo(code),
            )
        }
    }
}
