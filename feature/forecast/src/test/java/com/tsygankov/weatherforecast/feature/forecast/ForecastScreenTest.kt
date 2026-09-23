package com.tsygankov.weatherforecast.feature.forecast

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tsygankov.weatherforecast.core.model.TemperatureUnit
import com.tsygankov.weatherforecast.core.model.WeatherCondition
import com.tsygankov.weatherforecast.ui.theme.WeatherForecastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ForecastScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `content shows three-tab scaffold current metrics and the nearest hourly item`() {
        setScreen(
            ForecastUiState.Content(
                cityName = "Токио",
                content = CONTENT,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
        )

        composeRule.onNodeWithText("Токио").assertIsDisplayed()
        composeRule.onNodeWithText("Сейчас").assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("7 дней").assertIsEnabled().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Месяц").assertIsEnabled().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Назад").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("20°C").assertIsDisplayed()
        composeRule.onNodeWithText("ср, 9 сентября, 12:30").assertIsDisplayed()
        composeRule.onNodeWithText("Переменная облачность").assertIsDisplayed()
        composeRule.onNodeWithText("Ощущается как").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("18°C").assertIsDisplayed()
        composeRule.onNodeWithText("64 %").assertIsDisplayed()
        composeRule.onNodeWithText("1012,5 гПа").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("7,5 км/ч").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ближайшие часы").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_HOURLY_LIST_TEST_TAG).performScrollTo()
        composeRule.onNodeWithText("ср, 13:00").assertIsDisplayed()
        composeRule.onNodeWithText("21°C").assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_TEMPERATURE_CHART_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Температура по часам. 13:00: 21°C; 14:00: 22°C",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_PRECIPITATION_CHART_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Вероятность осадков. 13:00: 15 %; 14:00: 70 %",
        ).assertIsDisplayed()
    }

    @Test
    fun `week tab shows each real day with date condition temperatures and precipitation`() {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                var selectedTab by remember { mutableStateOf(ForecastTab.NOW) }
                var selectedDay by remember { mutableStateOf<String?>(null) }
                ForecastScreen(
                    state = ForecastUiState.Content(
                        cityName = "Москва",
                        content = CONTENT,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                    ),
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> selectedTab = tab },
                    onRetry = {},
                    onBackClick = {},
                    selectedDaySourceDate = selectedDay,
                    onDaySelected = { sourceDate -> selectedDay = sourceDate },
                    onDayDetailBack = { selectedDay = null },
                )
            }
        }

        composeRule.onNodeWithText("7 дней").performClick().assertIsSelected()
        val listWidth = composeRule.onNodeWithTag(FORECAST_DAILY_LIST_TEST_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val firstCardWidth = composeRule
            .onNodeWithTag("${FORECAST_WEEK_CARD_TEST_TAG_PREFIX}2026-09-09")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertTrue(
            "Карточка недельного прогноза должна быть уже списка",
            firstCardWidth < listWidth,
        )
        composeRule.onNodeWithText("Сегодня").assertIsDisplayed()
        composeRule.onNodeWithText("среда").assertDoesNotExist()
        composeRule.onNodeWithText("9 сентября").assertIsDisplayed()
        composeRule.onNodeWithText("Переменная облачность").assertIsDisplayed()
        composeRule.onAllNodesWithText("Мин.").assertCountEquals(2)
        composeRule.onNodeWithText("12°C").assertIsDisplayed()
        composeRule.onAllNodesWithText("Макс.").assertCountEquals(2)
        composeRule.onNodeWithText("24°C").assertIsDisplayed()
        composeRule.onAllNodesWithText("Осадки").assertCountEquals(2)
        composeRule.onNodeWithText("10 %").assertIsDisplayed()
        composeRule.onNodeWithText("четверг").assertIsDisplayed()
        composeRule.onNodeWithText("10 сентября").assertIsDisplayed()
        composeRule.onNodeWithText("11 сентября").assertDoesNotExist()
        composeRule.onNodeWithText("20°C").assertDoesNotExist()
    }

    @Test
    fun `week tab explains when no daily rows are available`() {
        setScreen(
            state = ForecastUiState.Content(
                cityName = "Москва",
                content = CONTENT.copy(weekDays = emptyList()),
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
            selectedTab = ForecastTab.WEEK,
        )

        composeRule.onNodeWithText("7 дней").assertIsSelected()
        composeRule.onNodeWithText("Дневные данные пока недоступны").assertIsDisplayed()
    }

    @Test
    fun `week day opens its own forecast and back returns to the week`() {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                var selectedDay by remember { mutableStateOf<String?>(null) }
                ForecastScreen(
                    state = ForecastUiState.Content(
                        cityName = "Москва",
                        content = CONTENT,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                    ),
                    selectedTab = ForecastTab.WEEK,
                    onTabSelected = {},
                    onRetry = {},
                    onBackClick = {},
                    selectedDaySourceDate = selectedDay,
                    onDaySelected = { sourceDate -> selectedDay = sourceDate },
                    onDayDetailBack = { selectedDay = null },
                )
            }
        }

        composeRule.onNodeWithTag("${FORECAST_WEEK_CARD_TEST_TAG_PREFIX}2026-09-09")
            .performClick()
        composeRule.onNodeWithTag(FORECAST_DAY_DETAIL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("12°C … 24°C").assertIsDisplayed()
        composeRule.onNodeWithText("Переменная облачность").assertIsDisplayed()
        composeRule.onNodeWithText("06:15").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Восход").assertIsDisplayed()
        composeRule.onNodeWithText("7 дней").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Назад").performClick()

        composeRule.onNodeWithText("7 дней").assertIsSelected()
        composeRule.onNodeWithTag(FORECAST_DAILY_LIST_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `week tab keeps cached daily rows and retry during refresh failure`() {
        var retryClicks = 0
        setScreen(
            state = ForecastUiState.ErrorWithCache(
                cityName = "Москва",
                content = CONTENT,
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
            selectedTab = ForecastTab.WEEK,
            onRetry = { retryClicks += 1 },
        )

        composeRule.onNodeWithText(
            "Не удалось обновить прогноз. Показаны сохранённые данные.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("9 сентября").assertIsDisplayed()
        composeRule.onNodeWithText("Повторить").assertHeightIsAtLeast(48.dp).performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
        }
    }

    @Test
    fun `month tab shows a complete calendar and enables only real forecast dates`() {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                var selectedTab by remember { mutableStateOf(ForecastTab.NOW) }
                var selectedDay by remember { mutableStateOf<String?>(null) }
                ForecastScreen(
                    state = ForecastUiState.Content(
                        cityName = "Москва",
                        content = CONTENT,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                    ),
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> selectedTab = tab },
                    onRetry = {},
                    onBackClick = {},
                    selectedDaySourceDate = selectedDay,
                    onDaySelected = { sourceDate -> selectedDay = sourceDate },
                    onDayDetailBack = { selectedDay = null },
                )
            }
        }

        composeRule.onNodeWithText("Месяц").performClick().assertIsSelected()
        composeRule.onNodeWithText("Доступно до 16 дней").assertIsDisplayed()
        composeRule.onNodeWithText("Точность дальнего прогноза снижается.").assertIsDisplayed()
        composeRule.onNodeWithText("Сентябрь 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("${FORECAST_MONTH_DAY_TEST_TAG_PREFIX}2026-09-01")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("${FORECAST_MONTH_DAY_TEST_TAG_PREFIX}2026-09-09")
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(FORECAST_MONTH_LIST_TEST_TAG).performScrollToIndex(40)
        composeRule.onNodeWithTag("${FORECAST_MONTH_DAY_TEST_TAG_PREFIX}2026-09-30")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(FORECAST_MONTH_LIST_TEST_TAG).performScrollToIndex(19)
        composeRule.onNodeWithTag("${FORECAST_MONTH_DAY_TEST_TAG_PREFIX}2026-09-09")
            .performClick()
        composeRule.onNodeWithTag(FORECAST_DAY_DETAIL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад").performClick()
        composeRule.onNodeWithText("Месяц").assertIsSelected()
    }

    @Test
    fun `month tab keeps horizon explanation when no real dates are available`() {
        setScreen(
            state = ForecastUiState.Content(
                cityName = "Москва",
                content = CONTENT.copy(monthDays = emptyList()),
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
            selectedTab = ForecastTab.MONTH,
        )

        composeRule.onNodeWithText("Месяц").assertIsSelected()
        composeRule.onNodeWithText("Доступно до 16 дней").assertIsDisplayed()
        composeRule.onNodeWithText("Точность дальнего прогноза снижается.").assertIsDisplayed()
        composeRule.onNodeWithText("Расширенный прогноз пока недоступен").assertIsDisplayed()
    }

    @Test
    fun `initial loading explains progress without fake weather values`() {
        setScreen(ForecastUiState.InitialLoading(cityName = "Москва"))

        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.onNodeWithText("Загружаем прогноз…").assertIsDisplayed()
        composeRule.onNodeWithText("0°C").assertDoesNotExist()
    }

    @Test
    fun `blocking error exposes one retry action`() {
        var retryClicks = 0
        setScreen(
            state = ForecastUiState.ErrorWithoutCache(
                cityName = "Москва",
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
            onRetry = { retryClicks += 1 },
        )

        composeRule.onNodeWithText(
            "Не удалось загрузить прогноз. Проверьте подключение к интернету.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Повторить").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
        }
    }

    @Test
    fun `refresh keeps content visible and shows compact progress`() {
        setScreen(
            ForecastUiState.RefreshingWithContent(
                cityName = "Москва",
                content = CONTENT,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
        )

        composeRule.onNodeWithText("Обновляем прогноз…").assertIsDisplayed()
        composeRule.onNodeWithText("20°C").assertIsDisplayed()
        composeRule.onNodeWithText("Переменная облачность").assertIsDisplayed()
    }

    @Test
    fun `cache error keeps content and offers retry`() {
        var retryClicks = 0
        setScreen(
            state = ForecastUiState.ErrorWithCache(
                cityName = "Москва",
                content = CONTENT,
                message = ForecastUserMessage.LOAD_FAILED,
                temperatureUnit = TemperatureUnit.CELSIUS,
            ),
            onRetry = { retryClicks += 1 },
        )

        composeRule.onNodeWithText(
            "Не удалось обновить прогноз. Показаны сохранённые данные.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("20°C").assertIsDisplayed()
        composeRule.onNodeWithText("Повторить").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
        }
    }

    @Test
    fun `missing city has an explicit explanation`() {
        setScreen(ForecastUiState.CityNotFound(TemperatureUnit.CELSIUS))

        composeRule.onNodeWithText("Город больше не найден").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Откройте список городов и выберите сохранённый город.",
        ).assertIsDisplayed()
    }

    @Test
    fun `start forecast opens city list and settings with accessible actions`() {
        var cityListClicks = 0
        var settingsClicks = 0
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                ForecastScreen(
                    state = ForecastUiState.InitialLoading(cityName = "Москва"),
                    selectedTab = ForecastTab.NOW,
                    onTabSelected = {},
                    onRetry = {},
                    onBackClick = { cityListClicks += 1 },
                    isStartScreen = true,
                    onSettingsClick = { settingsClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Выбрать город")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("Открыть настройки")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("Назад").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, cityListClicks)
            assertEquals(1, settingsClicks)
        }
    }

    @Test
    fun `first launch shows a city choice instead of an invented forecast`() {
        var loading by mutableStateOf(true)
        var cityClicks = 0
        var settingsClicks = 0
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                WeatherStartPlaceholderScreen(
                    loading = loading,
                    onCitiesClick = { cityClicks += 1 },
                    onSettingsClick = { settingsClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Загружаем сохранённые города…").assertIsDisplayed()
        composeRule.onNodeWithText("0°C").assertDoesNotExist()
        composeRule.runOnIdle { loading = false }
        composeRule.onNodeWithText("Пока нет города для прогноза").assertIsDisplayed()
        composeRule.onNodeWithText("Найдите город, чтобы увидеть его погоду.").assertIsDisplayed()
        composeRule.onNodeWithText("Выбрать город")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("Открыть настройки")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, cityClicks)
            assertEquals(1, settingsClicks)
        }
    }

    @Test
    fun `first launch explains location permission and keeps manual city fallback`() {
        var permissionClicks = 0
        var cityClicks = 0
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                WeatherStartPlaceholderScreen(
                    loading = false,
                    permissionRequired = true,
                    onCitiesClick = { cityClicks += 1 },
                    onSettingsClick = {},
                    onPermissionClick = { permissionClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Погода для вашего местоположения").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Разрешите приблизительное местоположение, чтобы увидеть погоду рядом с вами. Город не будет добавлен в избранное.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Разрешить местоположение")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("Выбрать город вручную")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("0°C").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(1, permissionClicks)
            assertEquals(1, cityClicks)
        }
    }

    @Test
    fun `rapid interrupted tab animations settle on the last state without retry or stale content`() {
        composeRule.mainClock.autoAdvance = false
        var selectedTab by mutableStateOf(ForecastTab.NOW)
        var retryClicks = 0
        val selections = mutableListOf<ForecastTab>()
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                ForecastScreen(
                    state = ForecastUiState.Content(
                        cityName = "Москва",
                        content = CONTENT,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                    ),
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        selections += tab
                        selectedTab = tab
                    },
                    onRetry = { retryClicks += 1 },
                    onBackClick = {},
                )
            }
        }

        composeRule.onNodeWithText("7 дней").performClick()
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.onNodeWithText("Месяц").performClick()
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.onNodeWithText("Сейчас").performClick()
        composeRule.mainClock.advanceTimeBy(FORECAST_TAB_ANIMATION_DURATION_MILLIS * 2L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Сейчас").assertIsSelected()
        composeRule.onNodeWithText("20°C").assertIsDisplayed()
        composeRule.onNodeWithText("Доступно до 16 дней").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                listOf(ForecastTab.WEEK, ForecastTab.MONTH, ForecastTab.NOW),
                selections,
            )
            assertEquals(0, retryClicks)
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h240dp-land")
    fun `month remains scrollable in compact landscape with large font`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                WeatherForecastTheme(dynamicColor = false) {
                    ForecastScreen(
                        state = ForecastUiState.Content(
                            cityName = "Москва",
                            content = CONTENT,
                            temperatureUnit = TemperatureUnit.CELSIUS,
                        ),
                        selectedTab = ForecastTab.MONTH,
                        onTabSelected = {},
                        onRetry = {},
                        onBackClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Доступно до 16 дней").assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_MONTH_LIST_TEST_TAG).performScrollToIndex(20)
        composeRule.onNodeWithTag("${FORECAST_MONTH_DAY_TEST_TAG_PREFIX}2026-09-10")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h240dp-land")
    fun `large font compact error keeps retry reachable and touch target accessible`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                WeatherForecastTheme(dynamicColor = false) {
                    ForecastScreen(
                        state = ForecastUiState.ErrorWithoutCache(
                            cityName = "Москва",
                            message = ForecastUserMessage.LOAD_FAILED,
                            temperatureUnit = TemperatureUnit.CELSIUS,
                        ),
                        selectedTab = ForecastTab.NOW,
                        onTabSelected = {},
                        onRetry = {},
                        onBackClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Повторить")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "w640dp-h240dp-land")
    fun `large font keeps both real hourly charts reachable by vertical scrolling`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                WeatherForecastTheme(dynamicColor = false) {
                    ForecastScreen(
                        state = ForecastUiState.Content(
                            cityName = "Москва",
                            content = CONTENT,
                            temperatureUnit = TemperatureUnit.CELSIUS,
                        ),
                        selectedTab = ForecastTab.NOW,
                        onTabSelected = {},
                        onRetry = {},
                        onBackClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FORECAST_TEMPERATURE_CHART_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(FORECAST_PRECIPITATION_CHART_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Вероятность осадков. 13:00: 15 %; 14:00: 70 %",
        ).assertIsDisplayed()
    }

    private fun setScreen(
        state: ForecastUiState,
        selectedTab: ForecastTab = ForecastTab.NOW,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherForecastTheme(dynamicColor = false) {
                ForecastScreen(
                    state = state,
                    selectedTab = selectedTab,
                    onTabSelected = {},
                    onRetry = onRetry,
                    onBackClick = {},
                )
            }
        }
    }

    private companion object {
        val CONTENT = ForecastContentUiModel(
            current = CurrentForecastUiModel(
                temperature = 20,
                apparentTemperature = 18,
                humidityPercent = 64,
                pressure = "1012,5",
                windSpeed = "7,5",
                condition = WeatherCondition.PARTLY_CLOUDY,
                localTimeLabel = "ср, 9 сентября, 12:30",
            ),
            hourly = listOf(
                HourlyForecastUiModel(
                    sourceTime = "2026-09-09T13:00",
                    timeLabel = "ср, 13:00",
                    compactTimeLabel = "13:00",
                    temperature = 21,
                    precipitationProbability = 15,
                    condition = WeatherCondition.CLOUDY,
                ),
                HourlyForecastUiModel(
                    sourceTime = "2026-09-09T14:00",
                    timeLabel = "ср, 14:00",
                    compactTimeLabel = "14:00",
                    temperature = 22,
                    precipitationProbability = 70,
                    condition = WeatherCondition.RAIN,
                ),
            ),
            weekDays = listOf(
                DailyForecastUiModel(
                    sourceDate = "2026-09-09",
                    dayOfWeekLabel = "среда",
                    dateLabel = "9 сентября",
                    minTemperature = 12,
                    maxTemperature = 24,
                    condition = WeatherCondition.PARTLY_CLOUDY,
                    precipitationProbability = 10,
                    isToday = true,
                    sunriseLabel = "06:15",
                    sunsetLabel = "19:40",
                ),
                DailyForecastUiModel(
                    sourceDate = "2026-09-10",
                    dayOfWeekLabel = "четверг",
                    dateLabel = "10 сентября",
                    minTemperature = 13,
                    maxTemperature = 25,
                    condition = WeatherCondition.RAIN,
                    precipitationProbability = 20,
                ),
            ),
            monthDays = listOf(
                DailyForecastUiModel(
                    sourceDate = "2026-09-09",
                    dayOfWeekLabel = "среда",
                    dateLabel = "9 сентября",
                    minTemperature = 12,
                    maxTemperature = 24,
                    condition = WeatherCondition.PARTLY_CLOUDY,
                    precipitationProbability = 10,
                ),
                DailyForecastUiModel(
                    sourceDate = "2026-09-10",
                    dayOfWeekLabel = "четверг",
                    dateLabel = "10 сентября",
                    minTemperature = 13,
                    maxTemperature = 25,
                    condition = WeatherCondition.RAIN,
                    precipitationProbability = 20,
                ),
            ),
        )
    }
}
