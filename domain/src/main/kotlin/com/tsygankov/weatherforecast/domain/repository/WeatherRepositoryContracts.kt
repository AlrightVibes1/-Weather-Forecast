package com.tsygankov.weatherforecast.domain.repository

import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface WeatherSearchRepository {
    suspend fun searchCities(query: String): Result<List<CitySearchResult>>
}

data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
    val cityName: String? = null,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

interface DeviceLocationRepository {
    val latestCoordinates: Flow<GeoCoordinates?>
        get() = flowOf(null)

    suspend fun currentCoordinates(): Result<GeoCoordinates>
}

interface CoordinatesWeatherRepository {
    suspend fun getForecast(latitude: Double, longitude: Double): Result<Forecast>
}

interface FavoriteCitiesRepository {
    fun observeCities(): Flow<List<City>>
    fun observeCity(id: Long): Flow<City?>
    fun observeForecast(id: Long): Flow<Forecast?>
    suspend fun getCity(id: Long): City?
    suspend fun add(city: CitySearchResult): AddFavoriteResult
    suspend fun clear()
    suspend fun refreshCity(cityId: Long): RefreshResult
    suspend fun refreshAll(): RefreshResult
}

data class AddFavoriteResult(
    val cityId: Long,
    val refreshResult: RefreshResult,
)

sealed interface RefreshFailure {
    val cityId: Long?

    data class Temporary(
        override val cityId: Long,
        val reason: TemporaryRefreshFailureReason,
    ) : RefreshFailure

    data class Permanent(
        override val cityId: Long?,
        val reason: PermanentRefreshFailureReason,
    ) : RefreshFailure
}

enum class TemporaryRefreshFailureReason {
    NETWORK,
    TIMEOUT,
    SERVICE_UNAVAILABLE,
}

enum class PermanentRefreshFailureReason {
    CITY_NOT_FOUND,
    INVALID_RESPONSE,
    REQUEST_REJECTED,
    STORAGE,
    UNEXPECTED,
}

sealed interface RefreshResult {
    val refreshedCityIds: Set<Long>
    val temporaryFailures: List<RefreshFailure.Temporary>
    val permanentFailures: List<RefreshFailure.Permanent>

    data class Success(
        override val refreshedCityIds: Set<Long>,
    ) : RefreshResult {
        init {
            require(refreshedCityIds.isNotEmpty())
        }

        override val temporaryFailures: List<RefreshFailure.Temporary> = emptyList()
        override val permanentFailures: List<RefreshFailure.Permanent> = emptyList()
    }

    data class PartialSuccess(
        override val refreshedCityIds: Set<Long>,
        override val temporaryFailures: List<RefreshFailure.Temporary>,
        override val permanentFailures: List<RefreshFailure.Permanent>,
    ) : RefreshResult {
        init {
            require(refreshedCityIds.isNotEmpty())
            require(temporaryFailures.isNotEmpty() || permanentFailures.isNotEmpty())
        }
    }

    data class TemporaryFailure(
        override val temporaryFailures: List<RefreshFailure.Temporary>,
    ) : RefreshResult {
        init {
            require(temporaryFailures.isNotEmpty())
        }

        override val refreshedCityIds: Set<Long> = emptySet()
        override val permanentFailures: List<RefreshFailure.Permanent> = emptyList()
    }

    data class PermanentFailure(
        override val permanentFailures: List<RefreshFailure.Permanent>,
    ) : RefreshResult {
        init {
            require(permanentFailures.isNotEmpty())
        }

        override val refreshedCityIds: Set<Long> = emptySet()
        override val temporaryFailures: List<RefreshFailure.Temporary> = emptyList()
    }

    data class MixedFailure(
        override val temporaryFailures: List<RefreshFailure.Temporary>,
        override val permanentFailures: List<RefreshFailure.Permanent>,
    ) : RefreshResult {
        init {
            require(temporaryFailures.isNotEmpty())
            require(permanentFailures.isNotEmpty())
        }

        override val refreshedCityIds: Set<Long> = emptySet()
    }

    data object EmptyFavorites : RefreshResult {
        override val refreshedCityIds: Set<Long> = emptySet()
        override val temporaryFailures: List<RefreshFailure.Temporary> = emptyList()
        override val permanentFailures: List<RefreshFailure.Permanent> = emptyList()
    }
}
