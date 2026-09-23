package com.tsygankov.weatherforecast.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WeatherRefreshSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `request has expected interval network constraint and exponential backoff`() {
        val request = WeatherRefreshScheduler.createRequest()
        val workSpec = request.workSpec

        assertEquals("favorite-weather-refresh", WeatherRefreshScheduler.UNIQUE_NAME)
        assertEquals(
            TimeUnit.HOURS.toMillis(WeatherRefreshScheduler.REPEAT_INTERVAL_HOURS),
            workSpec.intervalDuration,
        )
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(
            TimeUnit.MINUTES.toMillis(WeatherRefreshScheduler.BACKOFF_DELAY_MINUTES),
            workSpec.backoffDelayDuration,
        )
    }

    @Test
    fun `scheduling twice keeps exactly one existing periodic work`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        val workManager = WorkManager.getInstance(context)

        WeatherRefreshScheduler.schedule(workManager).result.get()
        val first = workManager
            .getWorkInfosForUniqueWork(WeatherRefreshScheduler.UNIQUE_NAME)
            .get()
            .single()

        WeatherRefreshScheduler.schedule(workManager).result.get()
        val afterSecondSchedule = workManager
            .getWorkInfosForUniqueWork(WeatherRefreshScheduler.UNIQUE_NAME)
            .get()

        assertEquals(1, afterSecondSchedule.size)
        assertEquals(first.id, afterSecondSchedule.single().id)
    }
}
