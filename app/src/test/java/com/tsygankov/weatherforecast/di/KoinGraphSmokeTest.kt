package com.tsygankov.weatherforecast.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tsygankov.weatherforecast.AppThemeViewModel
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.database.CityDao
import com.tsygankov.weatherforecast.core.database.WeatherDatabase
import com.tsygankov.weatherforecast.core.network.GeocodingApi
import com.tsygankov.weatherforecast.core.network.WeatherApi
import com.tsygankov.weatherforecast.data.OpenMeteoWeatherRepository
import com.tsygankov.weatherforecast.data.SharedPreferencesSettingsRepository
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import com.tsygankov.weatherforecast.feature.forecast.ForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.LocationForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.TemporaryCityForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.WeatherStartViewModel
import com.tsygankov.weatherforecast.feature.home.HomeViewModel
import com.tsygankov.weatherforecast.feature.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KoinGraphSmokeTest {
    @Test
    fun `production graph resolves infrastructure repositories and view models`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val koinApplication = koinApplication {
            androidContext(context)
            modules(appModules)
        }

        try {
            val koin = koinApplication.koin
            assertNotNull(koin.get<OkHttpClient>())
            assertEquals(
                setOf("api.open-meteo.com", "geocoding-api.open-meteo.com"),
                koin.getAll<Retrofit>().map { it.baseUrl().host }.toSet(),
            )
            assertNotNull(koin.get<WeatherApi>())
            assertNotNull(koin.get<GeocodingApi>())
            assertNotNull(koin.get<WeatherDatabase>())
            assertNotNull(koin.get<CityDao>())
            assertNotNull(koin.get<OpenMeteoWeatherRepository>())
            assertNotNull(koin.get<WeatherSearchRepository>())
            assertNotNull(koin.get<CoordinatesWeatherRepository>())
            assertNotNull(koin.get<DeviceLocationRepository>())
            assertNotNull(koin.get<FavoriteCitiesRepository>())
            assertNotNull(koin.get<SettingsRepository>())
            assertNotNull(koin.get<SharedPreferencesSettingsRepository>())
            assertNotNull(koin.get<AppClock>())
            assertNotNull(koin.get<AppThemeViewModel>())
            assertNotNull(koin.get<HomeViewModel>())
            assertNotNull(koin.get<SettingsViewModel>())
            assertNotNull(koin.get<WeatherStartViewModel>())
            assertNotNull(koin.get<LocationForecastViewModel>())
            assertNotNull(
                koin.get<TemporaryCityForecastViewModel> {
                    parametersOf(
                        com.tsygankov.weatherforecast.core.model.CitySearchResult(
                            id = 524901L,
                            name = "Москва",
                            country = "Россия",
                            adminArea = "Москва",
                            latitude = 55.75,
                            longitude = 37.61,
                            timezone = "Europe/Moscow",
                        ),
                    )
                },
            )
            assertNotNull(
                koin.get<ForecastViewModel> {
                    parametersOf(524901L)
                },
            )
            advanceUntilIdle()
        } finally {
            koinApplication.koin.getOrNull<WeatherDatabase>()?.close()
            koinApplication.close()
            Dispatchers.resetMain()
        }
    }
}
