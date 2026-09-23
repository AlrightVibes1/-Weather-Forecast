package com.tsygankov.weatherforecast.data

import com.tsygankov.weatherforecast.core.database.CityDao
import com.tsygankov.weatherforecast.core.database.CityEntity
import com.tsygankov.weatherforecast.core.database.CityWithCurrentWeather
import com.tsygankov.weatherforecast.core.database.CurrentWeatherEntity
import com.tsygankov.weatherforecast.core.database.DailyWeatherEntity
import com.tsygankov.weatherforecast.core.database.ForecastCacheWrite
import com.tsygankov.weatherforecast.core.database.HourlyWeatherEntity
import com.tsygankov.weatherforecast.core.database.StoredForecast
import com.tsygankov.weatherforecast.core.model.CitySearchResult
import com.tsygankov.weatherforecast.core.network.CurrentDto
import com.tsygankov.weatherforecast.core.network.DailyDto
import com.tsygankov.weatherforecast.core.network.ForecastResponseDto
import com.tsygankov.weatherforecast.core.network.GeocodingApi
import com.tsygankov.weatherforecast.core.network.GeocodingResponseDto
import com.tsygankov.weatherforecast.core.network.HourlyDto
import com.tsygankov.weatherforecast.core.network.WeatherApi
import com.tsygankov.weatherforecast.domain.repository.FavoriteCitiesRepository
import com.tsygankov.weatherforecast.domain.repository.PermanentRefreshFailureReason
import com.tsygankov.weatherforecast.domain.repository.RefreshFailure
import com.tsygankov.weatherforecast.domain.repository.RefreshResult
import com.tsygankov.weatherforecast.domain.repository.TemporaryRefreshFailureReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

class OfflineFirstFavoriteCitiesRepositoryTest {
    private lateinit var dao: FakeCityDao
    private lateinit var weatherApi: MutableWeatherApi
    private lateinit var repository: FavoriteCitiesRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        dao = FakeCityDao()
        weatherApi = MutableWeatherApi { _, _ -> response(10.0, "first") }
        repository = OfflineFirstFavoriteCitiesRepository(
            dao = dao,
            weatherRepository = OpenMeteoWeatherRepository(EmptyGeocodingApi, weatherApi),
            currentTimeMillis = { now },
        )
    }

    @Test
    fun `successful refresh replaces full cache and temporary failure keeps it unchanged`() = runTest {
        val city = cityEntity()
        dao.upsert(city)

        val firstResult = repository.refreshCity(city.id)
        assertTrue(firstResult is RefreshResult.Success)
        val first = checkNotNull(repository.observeForecast(city.id).first())
        assertEquals(10.0, first.current.temperatureCelsius, 0.0)
        assertEquals(listOf("first-hour-0", "first-hour-1"), first.hourly.map { it.time })
        assertEquals(listOf("first-day-0", "first-day-1"), first.daily.map { it.date })

        now = 2_000L
        weatherApi.response = { _, _ -> response(30.0, "second") }
        val secondResult = repository.refreshCity(city.id)
        assertTrue(secondResult is RefreshResult.Success)
        val second = checkNotNull(repository.observeForecast(city.id).first())
        assertEquals(30.0, second.current.temperatureCelsius, 0.0)
        assertEquals(listOf("second-hour-0", "second-hour-1"), second.hourly.map { it.time })
        assertEquals(2_000L, repository.observeCity(city.id).first()?.updatedAt)

        weatherApi.response = { _, _ -> throw UnknownHostException("offline") }
        val failure = repository.refreshCity(city.id)
        assertTrue(failure is RefreshResult.TemporaryFailure)
        assertEquals(
            TemporaryRefreshFailureReason.NETWORK,
            failure.temporaryFailures.single().reason,
        )
        assertEquals(second, repository.observeForecast(city.id).first())
    }

    @Test
    fun `add saves city before failed first forecast`() = runTest {
        val city = citySearchResult()
        weatherApi.response = { _, _ -> throw UnknownHostException("offline") }

        val addResult = repository.add(city)

        assertEquals(city.id, addResult.cityId)
        assertTrue(addResult.refreshResult is RefreshResult.TemporaryFailure)
        assertNotNull(repository.getCity(city.id))
        assertNull(repository.observeForecast(city.id).first())
    }

    @Test
    fun `adding same result keeps one city and previous cache`() = runTest {
        val city = citySearchResult()
        val firstAdd = repository.add(city)
        assertTrue(firstAdd.refreshResult is RefreshResult.Success)
        val cached = checkNotNull(repository.observeForecast(city.id).first())

        now = 9_999L
        weatherApi.response = { _, _ -> throw UnknownHostException("offline") }
        val duplicateAdd = repository.add(city.copy(name = "Москва обновлённая"))

        val cities = repository.observeCities().first()
        assertTrue(duplicateAdd.refreshResult is RefreshResult.TemporaryFailure)
        assertEquals(1, cities.size)
        assertEquals("Москва обновлённая", cities.single().name)
        assertEquals(cached, repository.observeForecast(city.id).first())
        assertEquals(1_000L, dao.getById(city.id)?.addedAt)
    }

    @Test
    fun `clear removes favorites and their observable forecast through the repository contract`() = runTest {
        val city = cityEntity()
        dao.upsert(city)
        assertTrue(repository.refreshCity(city.id) is RefreshResult.Success)
        assertNotNull(repository.observeForecast(city.id).first())

        repository.clear()

        assertTrue(repository.observeCities().first().isEmpty())
        assertNull(repository.observeForecast(city.id).first())
        assertNull(repository.getCity(city.id))
    }

    @Test
    fun `refresh all distinguishes empty partial temporary permanent and mixed outcomes`() = runTest {
        assertTrue(repository.refreshAll() is RefreshResult.EmptyFavorites)
        val missing = repository.refreshCity(404L)
        assertTrue(missing is RefreshResult.PermanentFailure)
        assertEquals(
            PermanentRefreshFailureReason.CITY_NOT_FOUND,
            missing.permanentFailures.single().reason,
        )
        val first = cityEntity(id = 1L, latitude = 10.0)
        val second = cityEntity(id = 2L, latitude = 20.0)
        dao.upsert(first)
        dao.upsert(second)

        weatherApi.response = { latitude, _ ->
            if (latitude == first.latitude) response(15.0, "partial")
            else throw UnknownHostException("offline")
        }
        val partial = repository.refreshAll()
        assertTrue(partial is RefreshResult.PartialSuccess)
        assertEquals(setOf(first.id), partial.refreshedCityIds)
        assertEquals(
            listOf(RefreshFailure.Temporary(second.id, TemporaryRefreshFailureReason.NETWORK)),
            partial.temporaryFailures,
        )
        assertTrue(partial.permanentFailures.isEmpty())
        val successfulCache = repository.observeForecast(first.id).first()
        assertNotNull(successfulCache)

        weatherApi.response = { _, _ -> throw UnknownHostException("offline") }
        val totalTemporary = repository.refreshAll()
        assertTrue(totalTemporary is RefreshResult.TemporaryFailure)
        assertEquals(setOf(first.id, second.id), totalTemporary.temporaryFailures.map { it.cityId }.toSet())
        assertEquals(successfulCache, repository.observeForecast(first.id).first())

        weatherApi.response = { _, _ -> ForecastResponseDto() }
        val totalPermanent = repository.refreshAll()
        assertTrue(totalPermanent is RefreshResult.PermanentFailure)
        assertEquals(setOf(first.id, second.id), totalPermanent.permanentFailures.map { it.cityId }.toSet())
        assertTrue(
            totalPermanent.permanentFailures.all {
                it.reason == PermanentRefreshFailureReason.INVALID_RESPONSE
            },
        )
        assertEquals(successfulCache, repository.observeForecast(first.id).first())

        weatherApi.response = { latitude, _ ->
            if (latitude == first.latitude) throw UnknownHostException("offline")
            else ForecastResponseDto()
        }
        val mixed = repository.refreshAll()
        assertTrue(mixed is RefreshResult.MixedFailure)
        assertEquals(first.id, mixed.temporaryFailures.single().cityId)
        assertEquals(second.id, mixed.permanentFailures.single().cityId)
    }

    @Test
    fun `concurrent refreshes of same city are serialized`() = runTest {
        val city = cityEntity()
        dao.upsert(city)
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val activeRequests = AtomicInteger()
        val maximumActiveRequests = AtomicInteger()
        val totalRequests = AtomicInteger()
        weatherApi.response = { _, _ ->
            totalRequests.incrementAndGet()
            val active = activeRequests.incrementAndGet()
            maximumActiveRequests.updateAndGet { current -> maxOf(current, active) }
            firstRequestStarted.complete(Unit)
            try {
                releaseFirstRequest.await()
                response(25.0, "serialized")
            } finally {
                activeRequests.decrementAndGet()
            }
        }

        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.refreshCity(city.id) }
        firstRequestStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.refreshCity(city.id) }

        assertEquals(1, totalRequests.get())
        assertEquals(1, maximumActiveRequests.get())
        releaseFirstRequest.complete(Unit)
        assertTrue(first.await() is RefreshResult.Success)
        assertTrue(second.await() is RefreshResult.Success)
        assertEquals(2, totalRequests.get())
        assertEquals(1, maximumActiveRequests.get())
    }

    private fun cityEntity(
        id: Long = 524901L,
        latitude: Double = 55.75,
    ) = CityEntity(
        id = id,
        name = "Город $id",
        country = "Россия",
        adminArea = "Регион",
        latitude = latitude,
        longitude = 37.61,
        timezone = "Europe/Moscow",
        addedAt = 100L + id,
    )

    private fun citySearchResult() = CitySearchResult(
        id = 524901L,
        name = "Москва",
        country = "Россия",
        adminArea = "Москва",
        latitude = 55.75,
        longitude = 37.61,
        timezone = "Europe/Moscow",
    )

    private fun response(temperature: Double, label: String) = ForecastResponseDto(
        timezone = "Europe/Moscow",
        current = CurrentDto(
            time = "$label-now",
            temperature = temperature,
            apparentTemperature = temperature - 1.0,
            relativeHumidity = 60,
            pressure = 1_005.0,
            windSpeed = 8.0,
            weatherCode = 2,
            isDay = 1,
        ),
        hourly = HourlyDto(
            time = listOf("$label-hour-0", "$label-hour-1"),
            temperatures = listOf(temperature, temperature + 1.0),
            apparentTemperatures = listOf(temperature - 1.0, temperature),
            humidities = listOf(60, 58),
            precipitationProbabilities = listOf(10, 20),
            weatherCodes = listOf(2, 3),
            windSpeeds = listOf(8.0, 9.0),
        ),
        daily = DailyDto(
            time = listOf("$label-day-0", "$label-day-1"),
            maxTemperatures = listOf(temperature + 2.0, temperature + 1.0),
            minTemperatures = listOf(5.0, 4.0),
            weatherCodes = listOf(2, 61),
            precipitationProbabilities = listOf(20, 70),
            sunrise = listOf("05:30", "05:32"),
            sunset = listOf("19:20", "19:17"),
        ),
    )

    private class MutableWeatherApi(
        var response: suspend (latitude: Double, longitude: Double) -> ForecastResponseDto,
    ) : WeatherApi {
        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
            temperatureUnit: String,
        ): ForecastResponseDto = response(latitude, longitude)
    }

    private data object EmptyGeocodingApi : GeocodingApi {
        override suspend fun search(
            name: String,
            count: Int,
            language: String,
            format: String,
        ) = GeocodingResponseDto()
    }
}

private class FakeCityDao : CityDao {
    private val mutex = Mutex()
    private val cities = linkedMapOf<Long, CityEntity>()
    private val currentWeather = linkedMapOf<Long, CurrentWeatherEntity>()
    private val hourlyWeather = linkedMapOf<Long, List<HourlyWeatherEntity>>()
    private val dailyWeather = linkedMapOf<Long, List<DailyWeatherEntity>>()
    private val revision = MutableStateFlow(0L)

    override fun observeAll(): Flow<List<CityWithCurrentWeather>> = revision.map {
        cities.values
            .sortedWith(compareBy(CityEntity::addedAt, CityEntity::id))
            .map { city -> CityWithCurrentWeather(city, currentWeather[city.id]) }
    }

    override fun observeById(id: Long): Flow<CityWithCurrentWeather?> = revision.map {
        cities[id]?.let { city -> CityWithCurrentWeather(city, currentWeather[id]) }
    }

    override fun observeForecast(cityId: Long): Flow<StoredForecast?> = revision.map {
        cities[cityId]?.let { city ->
            StoredForecast(
                city = city,
                currentWeather = currentWeather[cityId],
                hourlyWeather = hourlyWeather[cityId].orEmpty(),
                dailyWeather = dailyWeather[cityId].orEmpty(),
            )
        }
    }

    override suspend fun getById(id: Long): CityEntity? = mutex.withLock {
        cities[id]
    }

    override suspend fun getAll(): List<CityEntity> = mutex.withLock {
        cities.values.sortedWith(compareBy(CityEntity::addedAt, CityEntity::id))
    }

    override suspend fun insertCity(city: CityEntity): Long = mutex.withLock {
        if (cities.containsKey(city.id)) {
            -1L
        } else {
            cities[city.id] = city
            publishLocked()
            city.id
        }
    }

    override suspend fun updateCityMetadata(
        id: Long,
        name: String,
        country: String,
        adminArea: String?,
        latitude: Double,
        longitude: Double,
        timezone: String,
    ): Int = mutex.withLock {
        val city = cities[id] ?: return@withLock 0
        cities[id] = city.copy(
            name = name,
            country = country,
            adminArea = adminArea,
            latitude = latitude,
            longitude = longitude,
            timezone = if (currentWeather.containsKey(id)) city.timezone else timezone,
        )
        publishLocked()
        1
    }

    override suspend fun upsert(city: CityEntity) = mutex.withLock {
        val existing = cities[city.id]
        cities[city.id] = if (existing == null) {
            city
        } else {
            city.copy(
                timezone = if (currentWeather.containsKey(city.id)) existing.timezone else city.timezone,
                addedAt = existing.addedAt,
            )
        }
        publishLocked()
    }

    override suspend fun updateCityTimezone(cityId: Long, timezone: String): Int = mutex.withLock {
        val city = cities[cityId] ?: return@withLock 0
        cities[cityId] = city.copy(timezone = timezone)
        publishLocked()
        1
    }

    override suspend fun deleteCurrentWeather(cityId: Long) = mutex.withLock {
        currentWeather.remove(cityId)
        publishLocked()
    }

    override suspend fun deleteHourlyWeather(cityId: Long) = mutex.withLock {
        hourlyWeather.remove(cityId)
        publishLocked()
    }

    override suspend fun deleteDailyWeather(cityId: Long) = mutex.withLock {
        dailyWeather.remove(cityId)
        publishLocked()
    }

    override suspend fun insertCurrentWeather(currentWeather: CurrentWeatherEntity) = mutex.withLock {
        check(cities.containsKey(currentWeather.cityId))
        check(!this.currentWeather.containsKey(currentWeather.cityId))
        this.currentWeather[currentWeather.cityId] = currentWeather
        publishLocked()
    }

    override suspend fun insertHourlyWeather(hourlyWeather: List<HourlyWeatherEntity>) = mutex.withLock {
        hourlyWeather.groupBy(HourlyWeatherEntity::cityId).forEach { (cityId, values) ->
            check(cities.containsKey(cityId))
            check(this.hourlyWeather[cityId].isNullOrEmpty())
            this.hourlyWeather[cityId] = values.sortedBy(HourlyWeatherEntity::position)
        }
        publishLocked()
    }

    override suspend fun insertDailyWeather(dailyWeather: List<DailyWeatherEntity>) = mutex.withLock {
        dailyWeather.groupBy(DailyWeatherEntity::cityId).forEach { (cityId, values) ->
            check(cities.containsKey(cityId))
            check(this.dailyWeather[cityId].isNullOrEmpty())
            this.dailyWeather[cityId] = values.sortedBy(DailyWeatherEntity::position)
        }
        publishLocked()
    }

    override suspend fun replaceForecast(cache: ForecastCacheWrite) = mutex.withLock {
        require(cache.currentWeather.cityId == cache.cityId)
        require(cache.hourlyWeather.isNotEmpty())
        require(cache.dailyWeather.isNotEmpty())
        require(cache.hourlyWeather.all { it.cityId == cache.cityId })
        require(cache.dailyWeather.all { it.cityId == cache.cityId })
        val city = checkNotNull(cities[cache.cityId])
        cities[cache.cityId] = city.copy(timezone = cache.timezone)
        currentWeather[cache.cityId] = cache.currentWeather
        hourlyWeather[cache.cityId] = cache.hourlyWeather.sortedBy(HourlyWeatherEntity::position)
        dailyWeather[cache.cityId] = cache.dailyWeather.sortedBy(DailyWeatherEntity::position)
        publishLocked()
    }

    override suspend fun clear() = mutex.withLock {
        cities.clear()
        currentWeather.clear()
        hourlyWeather.clear()
        dailyWeather.clear()
        publishLocked()
    }

    private fun publishLocked() {
        revision.value += 1
    }
}
