package com.tsygankov.weatherforecast.di

import android.content.Context
import androidx.work.WorkerParameters
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.feature.forecast.ForecastViewModel
import com.tsygankov.weatherforecast.feature.forecast.TemporaryCityForecastViewModel
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class KoinModuleVerificationTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `application graph has every required dependency`() {
        appModule.verify(
            extraTypes = listOf(
                Context::class,
                WorkerParameters::class,
            ),
            injections = injectedParameters(
                definition<ForecastViewModel>(Long::class),
                definition<TemporaryCityForecastViewModel>(CitySearchResult::class),
            ),
        )
    }
}
