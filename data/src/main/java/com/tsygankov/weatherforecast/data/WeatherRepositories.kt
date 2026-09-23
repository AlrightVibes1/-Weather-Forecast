package com.tsygankov.weatherforecast.data

import com.tsygankov.weatherforecast.core.database.CityDao
import com.tsygankov.weatherforecast.core.database.CityEntity
import com.tsygankov.weatherforecast.core.model.City
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.network.GeocodingApi
import com.tsygankov.weatherforecast.core.network.WeatherApi
import com.tsygankov.weatherforecast.data.mapper.toCacheWrite
import com.tsygankov.weatherforecast.data.mapper.toDomain
import com.tsygankov.weatherforecast.data.mapper.toDomainOrNull
import com.tsygankov.weatherforecast.domain.repository.AddFavoriteResult
import com.tsygankov.weatherforecast.domain.repository.CoordinatesWeatherRepository
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.PermanentRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.RefreshFailure
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.TemporaryRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.WeatherSearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OpenMeteoWeatherRepository(
    private val geocodingApi: GeocodingApi,
    private val weatherApi: WeatherApi,
) : WeatherSearchRepository, CoordinatesWeatherRepository {
    override suspend fun searchCities(query: String): Result<List<CitySearchResult>> = dataRequest {
        geocodingApi.search(query.trim()).toDomain()
    }

    override suspend fun getForecast(latitude: Double, longitude: Double): Result<Forecast> = dataRequest {
        weatherApi.forecast(latitude, longitude).toDomain()
    }
}

class OfflineFirstFavoriteCitiesRepository(
    private val dao: CityDao,
    private val weatherRepository: OpenMeteoWeatherRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : FavoriteCitiesRepository {
    private val gatesMutex = Mutex()
    private val refreshGates = mutableMapOf<Long, RefreshGate>()

    override fun observeCities(): Flow<List<City>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCity(id: Long): Flow<City?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeForecast(id: Long): Flow<Forecast?> =
        dao.observeForecast(id).map { it?.toDomainOrNull() }

    override suspend fun getCity(id: Long): City? = dao.getById(id)?.toDomain()

    override suspend fun add(city: CitySearchResult): AddFavoriteResult {
        dao.upsert(
            CityEntity(
                id = city.id,
                name = city.name,
                country = city.country.orEmpty(),
                adminArea = city.adminArea,
                latitude = city.latitude,
                longitude = city.longitude,
                timezone = city.timezone,
                addedAt = currentTimeMillis(),
            ),
        )
        return AddFavoriteResult(
            cityId = city.id,
            refreshResult = refreshCity(city.id),
        )
    }

    override suspend fun clear() = dao.clear()

    override suspend fun refreshCity(cityId: Long): RefreshResult =
        withRefreshGate(cityId) { refreshCityOnce(cityId) }

    override suspend fun refreshAll(): RefreshResult {
        val cities = try {
            dao.getAll()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return RefreshResult.PermanentFailure(
                listOf(
                    RefreshFailure.Permanent(
                        cityId = null,
                        reason = PermanentRefreshFailureReason.STORAGE,
                    ),
                ),
            )
        }
        if (cities.isEmpty()) return RefreshResult.EmptyFavorites

        val results = supervisorScope {
            cities.map { city -> async { refreshCity(city.id) } }.awaitAll()
        }
        return aggregateRefreshResults(results)
    }

    private suspend fun refreshCityOnce(cityId: Long): RefreshResult {
        val city = try {
            dao.getById(cityId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return permanentFailure(cityId, PermanentRefreshFailureReason.STORAGE)
        } ?: return permanentFailure(cityId, PermanentRefreshFailureReason.CITY_NOT_FOUND)

        return weatherRepository.getForecast(city.latitude, city.longitude).fold(
            onSuccess = { forecast ->
                try {
                    dao.replaceForecast(
                        forecast.toCacheWrite(
                            cityId = city.id,
                            updatedAt = currentTimeMillis(),
                        ),
                    )
                    RefreshResult.Success(setOf(city.id))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    permanentFailure(city.id, PermanentRefreshFailureReason.STORAGE)
                }
            },
            onFailure = { throwable -> refreshFailure(city.id, throwable) },
        )
    }

    private suspend fun <T> withRefreshGate(
        cityId: Long,
        block: suspend () -> T,
    ): T {
        val gate = gatesMutex.withLock {
            refreshGates.getOrPut(cityId) { RefreshGate() }.also { it.users += 1 }
        }
        return try {
            gate.mutex.withLock { block() }
        } finally {
            withContext(NonCancellable) {
                gatesMutex.withLock {
                    gate.users -= 1
                    if (gate.users == 0 && refreshGates[cityId] === gate) {
                        refreshGates.remove(cityId)
                    }
                }
            }
        }
    }

    private data class RefreshGate(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )
}

private fun refreshFailure(cityId: Long, throwable: Throwable): RefreshResult = when (throwable) {
    is DataException.Network -> temporaryFailure(cityId, TemporaryRefreshFailureReason.NETWORK)
    is DataException.Timeout -> temporaryFailure(cityId, TemporaryRefreshFailureReason.TIMEOUT)
    is DataException.Http -> when {
        throwable.statusCode == 408 ||
            throwable.statusCode == 425 ||
            throwable.statusCode == 429 ||
            throwable.statusCode in 500..599 ->
            temporaryFailure(cityId, TemporaryRefreshFailureReason.SERVICE_UNAVAILABLE)

        else -> permanentFailure(cityId, PermanentRefreshFailureReason.REQUEST_REJECTED)
    }

    is DataException.Parsing,
    is DataException.EmptyData,
    -> permanentFailure(cityId, PermanentRefreshFailureReason.INVALID_RESPONSE)

    is DataException.Unexpected -> permanentFailure(cityId, PermanentRefreshFailureReason.UNEXPECTED)
    else -> permanentFailure(cityId, PermanentRefreshFailureReason.UNEXPECTED)
}

private fun temporaryFailure(
    cityId: Long,
    reason: TemporaryRefreshFailureReason,
): RefreshResult.TemporaryFailure = RefreshResult.TemporaryFailure(
    listOf(RefreshFailure.Temporary(cityId, reason)),
)

private fun permanentFailure(
    cityId: Long?,
    reason: PermanentRefreshFailureReason,
): RefreshResult.PermanentFailure = RefreshResult.PermanentFailure(
    listOf(RefreshFailure.Permanent(cityId, reason)),
)

private fun aggregateRefreshResults(results: List<RefreshResult>): RefreshResult {
    if (results.isEmpty()) return RefreshResult.EmptyFavorites
    val refreshedCityIds = results.flatMapTo(linkedSetOf()) { it.refreshedCityIds }
    val temporaryFailures = results.flatMap { it.temporaryFailures }
    val permanentFailures = results.flatMap { it.permanentFailures }
    return when {
        refreshedCityIds.isEmpty() && temporaryFailures.isEmpty() && permanentFailures.isEmpty() ->
            RefreshResult.EmptyFavorites

        temporaryFailures.isEmpty() && permanentFailures.isEmpty() ->
            RefreshResult.Success(refreshedCityIds)

        refreshedCityIds.isNotEmpty() ->
            RefreshResult.PartialSuccess(
                refreshedCityIds = refreshedCityIds,
                temporaryFailures = temporaryFailures,
                permanentFailures = permanentFailures,
            )

        permanentFailures.isEmpty() -> RefreshResult.TemporaryFailure(temporaryFailures)
        temporaryFailures.isEmpty() -> RefreshResult.PermanentFailure(permanentFailures)
        else -> RefreshResult.MixedFailure(temporaryFailures, permanentFailures)
    }
}

private suspend fun <T> dataRequest(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable.toDataException())
}
