package com.tsygankov.weatherforecast

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LauncherIconTest {
    @Test
    @Config(sdk = [24])
    fun `manifest icon has a legacy vector for Android 7`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertEquals(R.mipmap.ic_weather_launcher, context.applicationInfo.icon)
        assertTrue(context.getDrawable(R.mipmap.ic_weather_launcher) is VectorDrawable)
    }

    @Test
    @Config(sdk = [35])
    fun `manifest icon has an adaptive version for modern Android`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertEquals(R.mipmap.ic_weather_launcher, context.applicationInfo.icon)
        val icon = context.getDrawable(R.mipmap.ic_weather_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        assertNotNull(icon.monochrome)
    }
}
