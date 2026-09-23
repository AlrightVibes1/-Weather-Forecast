package com.tsygankov.weatherforecast.data

import com.google.gson.JsonSyntaxException
import com.tsygankov.weatherforecast.core.network.ForecastResponseDto
import com.tsygankov.weatherforecast.core.network.GeocodingApi
import com.tsygankov.weatherforecast.core.network.GeocodingResponseDto
import com.tsygankov.weatherforecast.core.network.WeatherApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class WeatherRepositoryTest {
    @Test
    fun `repository converts transport and parsing failures to typed data errors`() = runTest {
        val networkRepository = repository(
            geocodingResult = { throw UnknownHostException("internal host detail") },
        )
        val parsingRepository = repository(
            forecastResult = { throw JsonSyntaxException("internal JSON detail") },
        )

        assertTrue(
            networkRepository.searchCities("Москва").exceptionOrNull() is DataException.Network,
        )
        assertTrue(
            parsingRepository.getForecast(55.75, 37.61).exceptionOrNull() is DataException.Parsing,
        )
    }

    @Test
    fun `repository keeps mapper empty-data failure typed`() = runTest {
        val repository = repository(forecastResult = { ForecastResponseDto() })

        assertTrue(
            repository.getForecast(55.75, 37.61).exceptionOrNull() is DataException.EmptyData,
        )
    }

    private fun repository(
        geocodingResult: suspend () -> GeocodingResponseDto = { GeocodingResponseDto() },
        forecastResult: suspend () -> ForecastResponseDto = { ForecastResponseDto() },
    ): OpenMeteoWeatherRepository = OpenMeteoWeatherRepository(
        geocodingApi = object : GeocodingApi {
            override suspend fun search(
                name: String,
                count: Int,
                language: String,
                format: String,
            ): GeocodingResponseDto = geocodingResult()
        },
        weatherApi = object : WeatherApi {
            override suspend fun forecast(
                latitude: Double,
                longitude: Double,
                current: String,
                hourly: String,
                daily: String,
                forecastDays: Int,
                timezone: String,
                temperatureUnit: String,
            ): ForecastResponseDto = forecastResult()
        },
    )
}
