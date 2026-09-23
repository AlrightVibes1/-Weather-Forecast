package com.tsygankov.weatherforecast

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import com.tsygankov.weatherforecast.core.model.ThemeMode
import com.tsygankov.weatherforecast.data.SharedPreferencesSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WeatherForecastAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `route reacts to stored theme and updates compose tree and system bars`() {
        val repository = SharedPreferencesSettingsRepository(preferences)
        val viewModel = AppThemeViewModel(repository)
        var observedBackground = Color.Unspecified
        lateinit var hostActivity: Activity

        composeRule.setContent {
            WeatherForecastAppRoute(
                viewModel = viewModel,
                dynamicColor = false,
            ) {
                val view = LocalView.current
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    observedBackground = background
                    hostActivity = requireNotNull(view.context.findActivity())
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(lightColorScheme().background, observedBackground)
            val controller = WindowCompat.getInsetsController(
                hostActivity.window,
                hostActivity.window.decorView,
            )
            assertTrue(controller.isAppearanceLightStatusBars)
            assertTrue(controller.isAppearanceLightNavigationBars)
        }

        repository.setThemeMode(ThemeMode.DARK)

        composeRule.runOnIdle {
            assertEquals(darkColorScheme().background, observedBackground)
            val controller = WindowCompat.getInsetsController(
                hostActivity.window,
                hostActivity.window.decorView,
            )
            assertFalse(controller.isAppearanceLightStatusBars)
            assertFalse(controller.isAppearanceLightNavigationBars)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private companion object {
        const val PREFERENCES_NAME = "wf_007_compose_test"
    }
}
