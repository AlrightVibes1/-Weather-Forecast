package com.tsygankov.weatherforecast.acceptance

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tsygankov.weatherforecast.core.model.AppClock
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.core.model.UserSettings
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.GeoCoordinates
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.SettingsRepository
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import com.tsygankov.weatherforecast.feature.forecast.ForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.TemporaryCityForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.WeatherStartViewModel
import com.tsygankov.weatherforecast.feature.home.HomeViewModel
import com.tsygankov.weatherforecast.feature.settings.SettingsViewModel
import com.tsygankov.weatherforecast.navigation.WeatherForecastNavHost
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WeatherForecastJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var favoritesRepository: JourneyFavoriteCitiesRepository

    @Before
    fun setUp() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        favoritesRepository = JourneyFavoriteCitiesRepository()
        startKoin {
            androidContext(context)
            modules(
                module {
                    single<FavoriteCitiesRepository> { favoritesRepository }
                    single<WeatherSearchRepository> {
                        object : WeatherSearchRepository {
                            override suspend fun searchCities(query: String) =
                                Result.success(listOf(MOSCOW_SEARCH_RESULT))
                        }
                    }
                    single<SettingsRepository> { JourneySettingsRepository() }
                    single<CoordinatesWeatherRepository> {
                        object : CoordinatesWeatherRepository {
                            override suspend fun getForecast(latitude: Double, longitude: Double) =
                                Result.success(journeyForecast())
                        }
                    }
                    single<DeviceLocationRepository> {
                        object : DeviceLocationRepository {
                            override suspend fun currentCoordinates(): Result<GeoCoordinates> =
                                Result.failure(IllegalStateException("Location is disabled"))
                        }
                    }
                    single<AppClock> { AppClock { JOURNEY_NOW_MILLIS } }
                    viewModelOf(::HomeViewModel)
                    viewModelOf(::ForecastViewModel)
                    viewModelOf(::TemporaryCityForecastViewModel)
                    viewModelOf(::WeatherStartViewModel)
                    viewModelOf(::SettingsViewModel)
                },
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `empty weather start search add forecast tabs settings and back form one connected journey`() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost(navController = navController)
            }
        }

        waitForText("Пока нет города для прогноза")
        composeRule.onNodeWithText("Выбрать город").performClick()
        composeRule.onNodeWithText("Избранных городов пока нет").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("Москва")
        waitForText("Результаты поиска")
        composeRule.onNodeWithText("Добавить").performClick()

        waitForText("Избранные города")
        composeRule.onNode(
            hasClickAction() and hasText("18°C", substring = true),
        ).performClick()

        waitForText("Ощущается как")
        composeRule.onNodeWithText("Ближайшие часы").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Месяц").performClick()
        waitForText("Доступно до 16 дней")

        composeRule.onNodeWithContentDescription("Назад").performClick()
        waitForText("Избранные города")
        composeRule.onNodeWithContentDescription("Открыть настройки").performClick()
        waitForText("Единицы температуры")
        composeRule.onNodeWithText("Тёмная").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Назад").performClick()
        waitForText("Избранные города")
        composeRule.runOnIdle { navController.navigateUp() }
        composeRule.onNodeWithContentDescription("Выбрать город").assertIsDisplayed()
        composeRule.onAllNodesWithText("18°C").onFirst().assertIsDisplayed()
    }

    @Test
    fun `search preview opens forecast and returns without adding favorite`() {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                WeatherForecastNavHost()
            }
        }

        waitForText("Пока нет города для прогноза")
        composeRule.onNodeWithText("Выбрать город").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Москва")
        waitForText("Результаты поиска")
        composeRule.onNodeWithText("Открыть прогноз").performClick()

        waitForText("Ощущается как")
        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(0, favoritesRepository.cityCount)
        }

        composeRule.onNodeWithContentDescription("Назад").performClick()
        waitForText("Результаты поиска")
        composeRule.onNodeWithText("Добавить").assertIsDisplayed()
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(0, favoritesRepository.cityCount)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        val MOSCOW_SEARCH_RESULT = CitySearchResult(
            id = 524901L,
            name = "Москва",
            country = "Россия",
            adminArea = "Москва",
            latitude = 55.75,
            longitude = 37.61,
            timezone = "Europe/Moscow",
        )
    }
}

private class JourneySettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(
        UserSettings(automaticLocationWeather = false),
    )

    override fun setTemperatureUnit(unit: TemperatureUnit) {
        settings.value = settings.value.copy(temperatureUnit = unit)
    }

    override fun setThemeMode(mode: ThemeMode) {
        settings.value = settings.value.copy(themeMode = mode)
    }

    override fun setAutomaticLocationWeather(enabled: Boolean) {
        settings.value = settings.value.copy(automaticLocationWeather = enabled)
    }
}

private class JourneyFavoriteCitiesRepository : FavoriteCitiesRepository {
    private val cities = MutableStateFlow<List<City>>(emptyList())
    private val forecasts = MutableStateFlow<Map<Long, Forecast>>(emptyMap())
    val cityCount: Int get() = cities.value.size

    override fun observeCities(): Flow<List<City>> = cities

    override fun observeCity(id: Long): Flow<City?> = cities.map { values ->
        values.firstOrNull { city -> city.id == id }
    }

    override fun observeForecast(id: Long): Flow<Forecast?> = forecasts.map { values -> values[id] }

    override suspend fun getCity(id: Long): City? = cities.value.firstOrNull { city -> city.id == id }

    override suspend fun add(city: CitySearchResult): AddFavoriteResult {
        val storedCity = City(
            id = city.id,
            name = city.name,
            country = city.country.orEmpty(),
            adminArea = city.adminArea,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = city.timezone,
            currentTemperatureCelsius = 18.0,
            currentWeatherCode = 2,
            updatedAt = JOURNEY_NOW_MILLIS,
        )
        cities.value = listOf(storedCity)
        forecasts.value = mapOf(city.id to journeyForecast())
        return AddFavoriteResult(
            cityId = city.id,
            refreshResult = RefreshResult.Success(setOf(city.id)),
        )
    }

    override suspend fun clear() {
        cities.value = emptyList()
        forecasts.value = emptyMap()
    }

    override suspend fun refreshCity(cityId: Long): RefreshResult =
        RefreshResult.Success(setOf(cityId))

    override suspend fun refreshAll(): RefreshResult = when {
        cities.value.isEmpty() -> RefreshResult.EmptyFavorites
        else -> RefreshResult.Success(cities.value.mapTo(mutableSetOf(), City::id))
    }

}

private fun journeyForecast() = Forecast(
    timezone = "Europe/Moscow",
    current = CurrentWeather(
        time = "2026-09-12T12:00",
        temperatureCelsius = 18.0,
        apparentTemperatureCelsius = 17.0,
        relativeHumidity = 60,
        pressure = 1_005.0,
        windSpeed = 7.0,
        weatherCode = 2,
        isDay = true,
    ),
    hourly = listOf(
        HourlyWeather("2026-09-12T12:00", 18.0, 17.0, 60, 10, 2, 7.0),
        HourlyWeather("2026-09-12T13:00", 19.0, 18.0, 58, 20, 61, 8.0),
    ),
    daily = listOf(
        DailyWeather("2026-09-12", 21.0, 12.0, 2, 20, "06:00", "18:45"),
        DailyWeather("2026-09-13", 20.0, 11.0, 61, 60, "06:02", "18:42"),
    ),
)

private const val JOURNEY_NOW_MILLIS = 1_757_668_800_000L
