package com.tsygankov.weatherforecast.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class WeatherRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val favoritesRepository: FavoriteCitiesRepository,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = try {
        when (favoritesRepository.refreshAll()) {
            is RefreshResult.Success,
            is RefreshResult.PartialSuccess,
            RefreshResult.EmptyFavorites,
            -> Result.success()

            is RefreshResult.TemporaryFailure -> Result.retry()
            is RefreshResult.PermanentFailure,
            is RefreshResult.MixedFailure,
            -> Result.failure()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        Result.failure()
    }
}

object WeatherRefreshScheduler {
    internal const val UNIQUE_NAME = "favorite-weather-refresh"
    internal const val REPEAT_INTERVAL_HOURS = 3L
    internal const val BACKOFF_DELAY_MINUTES = 15L

    fun schedule(context: Context): Operation = schedule(WorkManager.getInstance(context))

    // WorkManager хранит расписание между перезапусками процесса и ждёт доступную сеть.
    // Политика KEEP сохраняет одну периодическую задачу при повторных запусках приложения.
    internal fun schedule(workManager: WorkManager): Operation =
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            createRequest(),
        )

    internal fun createRequest() =
        PeriodicWorkRequestBuilder<WeatherRefreshWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()
}
