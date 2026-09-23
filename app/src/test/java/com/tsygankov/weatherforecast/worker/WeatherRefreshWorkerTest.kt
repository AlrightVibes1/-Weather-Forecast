package com.tsygankov.weatherforecast.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.di.workerModule
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.PermanentRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.RefreshFailure
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.TemporaryRefreshFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WeatherRefreshWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetKoinBeforeTest() = stopKoin()

    @After
    fun resetKoinAfterTest() = stopKoin()

    @Test
    fun `empty success and partial success finish without retry`() = runTest {
        val repository = FakeFavoriteCitiesRepository(RefreshResult.EmptyFavorites)
        assertWorkerResult(repository, ListenableWorker.Result.success())

        repository.result = RefreshResult.Success(setOf(1L, 2L))
        assertWorkerResult(repository, ListenableWorker.Result.success())

        repository.result = RefreshResult.PartialSuccess(
            refreshedCityIds = setOf(1L),
            temporaryFailures = listOf(
                RefreshFailure.Temporary(2L, TemporaryRefreshFailureReason.NETWORK),
            ),
            permanentFailures = emptyList(),
        )
        assertWorkerResult(repository, ListenableWorker.Result.success())
        assertEquals(3, repository.refreshCalls)
    }

    @Test
    fun `full temporary failure requests WorkManager retry`() = runTest {
        val repository = FakeFavoriteCitiesRepository(
            RefreshResult.TemporaryFailure(
                listOf(RefreshFailure.Temporary(1L, TemporaryRefreshFailureReason.TIMEOUT)),
            ),
        )

        assertWorkerResult(repository, ListenableWorker.Result.retry())
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `permanent and mixed failures finish without retry loop`() = runTest {
        val repository = FakeFavoriteCitiesRepository(
            RefreshResult.PermanentFailure(
                listOf(
                    RefreshFailure.Permanent(1L, PermanentRefreshFailureReason.INVALID_RESPONSE),
                ),
            ),
        )
        assertWorkerResult(repository, ListenableWorker.Result.failure())

        repository.result = RefreshResult.MixedFailure(
            temporaryFailures = listOf(
                RefreshFailure.Temporary(1L, TemporaryRefreshFailureReason.NETWORK),
            ),
            permanentFailures = listOf(
                RefreshFailure.Permanent(2L, PermanentRefreshFailureReason.REQUEST_REJECTED),
            ),
        )
        assertWorkerResult(repository, ListenableWorker.Result.failure())
    }

    @Test
    fun `unexpected exception fails but cancellation remains cancellation`() = runTest {
        val repository = FakeFavoriteCitiesRepository(RefreshResult.EmptyFavorites)
        repository.throwable = IllegalStateException("internal detail")
        assertWorkerResult(repository, ListenableWorker.Result.failure())

        repository.throwable = CancellationException("cancelled")
        try {
            runWorker(repository)
            throw AssertionError("CancellationException must be propagated")
        } catch (_: CancellationException) {
            // Expected: WorkManager owns cancellation and decides whether to reschedule.
        }
    }

    private suspend fun assertWorkerResult(
        repository: FakeFavoriteCitiesRepository,
        expected: ListenableWorker.Result,
    ) {
        val result = runWorker(repository)
        assertEquals(expected::class, result::class)
    }

    private suspend fun runWorker(repository: FavoriteCitiesRepository): ListenableWorker.Result {
        startKoin {
            androidContext(context)
            modules(
                workerModule,
                module {
                    single<FavoriteCitiesRepository> { repository }
                },
            )
        }
        return try {
            TestListenableWorkerBuilder<WeatherRefreshWorker>(context)
                .setWorkerFactory(KoinWorkerFactory())
                .build()
                .doWork()
        } finally {
            stopKoin()
        }
    }
}

private class FakeFavoriteCitiesRepository(
    var result: RefreshResult,
) : FavoriteCitiesRepository {
    var refreshCalls = 0
    var throwable: Throwable? = null

    override fun observeCities(): Flow<List<City>> = emptyFlow()
    override fun observeCity(id: Long): Flow<City?> = emptyFlow()
    override fun observeForecast(id: Long): Flow<Forecast?> = emptyFlow()
    override suspend fun getCity(id: Long): City? = null
    override suspend fun add(city: CitySearchResult): AddFavoriteResult = error("Not used")
    override suspend fun clear() = Unit
    override suspend fun refreshCity(cityId: Long): RefreshResult = error("Not used")
    override suspend fun refreshAll(): RefreshResult {
        refreshCalls += 1
        throwable?.let { throw it }
        return result
    }
}
