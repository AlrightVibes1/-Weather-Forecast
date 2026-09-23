package com.tsygankov.weatherforecast.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tsygankov.weatherforecast.core.model.CurrentWeather
import com.tsygankov.weatherforecast.core.model.DailyWeather
import com.tsygankov.weatherforecast.core.model.Forecast
import com.tsygankov.weatherforecast.core.model.HourlyWeather
import com.tsygankov.weatherforecast.data.mapper.toCacheWrite
import com.tsygankov.weatherforecast.data.mapper.toDomainOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WeatherDatabaseIntegrationTest {
    private lateinit var database: WeatherDatabase
    private lateinit var dao: CityDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cityDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `full forecast is read from Room and emitted by Flow`() = runTest {
        val city = city()
        val expected = forecast("Europe/Moscow", 20.0)
        dao.upsert(city)
        dao.replaceForecast(expected.toCacheWrite(city.id, updatedAt = 1_000L))

        val stored = dao.observeForecast(city.id).first()

        assertNotNull(stored)
        assertEquals(expected, stored?.toDomainOrNull())
        assertEquals(city, dao.getById(city.id))
        assertEquals(
            20.0,
            requireNotNull(dao.observeAll().first().single().currentWeather?.temperatureCelsius),
            0.0,
        )
    }

    @Test
    fun `adding the same city is idempotent and keeps forecast cache`() = runTest {
        val original = city(addedAt = 100L)
        val cachedForecast = forecast("Europe/Moscow", 18.0)
        dao.upsert(original)
        dao.replaceForecast(cachedForecast.toCacheWrite(original.id, updatedAt = 500L))

        dao.upsert(
            original.copy(
                name = "Москва обновлённая",
                timezone = "Wrong/Replacement",
                addedAt = 999L,
            ),
        )

        val cities = dao.getAll()
        val stored = dao.observeForecast(original.id).first()
        assertEquals(1, cities.size)
        assertEquals("Москва обновлённая", cities.single().name)
        assertEquals(100L, cities.single().addedAt)
        assertEquals("Europe/Moscow", cities.single().timezone)
        assertEquals(cachedForecast, stored?.toDomainOrNull())
    }

    @Test
    fun `successful replacement removes every old hourly and daily row`() = runTest {
        val city = city()
        dao.upsert(city)
        dao.replaceForecast(forecast("Europe/Moscow", 10.0).toCacheWrite(city.id, 100L))
        val replacement = forecast("Asia/Yekaterinburg", 30.0).copy(
            hourly = listOf(
                HourlyWeather("new-hour", 30.0, 29.0, 40, 5, 1, 3.0),
            ),
            daily = listOf(
                DailyWeather("new-day", 32.0, 22.0, 1, 5, "06:00", "18:00"),
            ),
        )

        dao.replaceForecast(replacement.toCacheWrite(city.id, 200L))

        val stored = checkNotNull(dao.observeForecast(city.id).first())
        assertEquals(replacement, stored.toDomainOrNull())
        assertEquals(listOf("new-hour"), stored.hourlyWeather.map { it.time })
        assertEquals(listOf("new-day"), stored.dailyWeather.map { it.date })
    }

    @Test
    fun `failed replacement rolls transaction back to previous complete cache`() = runTest {
        val city = city()
        val original = forecast("Europe/Moscow", 12.0)
        dao.upsert(city)
        dao.replaceForecast(original.toCacheWrite(city.id, 100L))
        val invalid = forecast("Asia/Tokyo", 40.0).toCacheWrite(city.id, 200L).let { write ->
            write.copy(
                hourlyWeather = listOf(
                    write.hourlyWeather[0],
                    write.hourlyWeather[0].copy(temperatureCelsius = 999.0),
                ),
            )
        }

        val error = runCatching { dao.replaceForecast(invalid) }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(original, dao.observeForecast(city.id).first()?.toDomainOrNull())
    }

    @Test
    fun `clearing favorites cascades to every forecast table`() = runTest {
        val city = city()
        dao.upsert(city)
        dao.replaceForecast(forecast("Europe/Moscow", 15.0).toCacheWrite(city.id, 100L))

        dao.clear()

        assertNull(dao.getById(city.id))
        assertEquals(0, rowCount("favorite_cities"))
        assertEquals(0, rowCount("current_weather"))
        assertEquals(0, rowCount("hourly_weather"))
        assertEquals(0, rowCount("daily_weather"))
    }

    private fun rowCount(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun city(addedAt: Long = 100L) = CityEntity(
        id = 524901L,
        name = "Москва",
        country = "Россия",
        adminArea = "Москва",
        latitude = 55.75,
        longitude = 37.61,
        timezone = "Europe/Moscow",
        addedAt = addedAt,
    )

    private fun forecast(timezone: String, temperature: Double) = Forecast(
        timezone = timezone,
        current = CurrentWeather("now", temperature, temperature - 1.0, 60, 1_005.0, 8.0, 2, true),
        hourly = listOf(
            HourlyWeather("hour-0", temperature, temperature - 1.0, 60, 10, 2, 8.0),
            HourlyWeather("hour-1", temperature + 1.0, temperature, 58, 20, 3, 9.0),
        ),
        daily = listOf(
            DailyWeather("day-0", temperature + 2.0, 10.0, 2, 20, "05:30", "19:20"),
            DailyWeather("day-1", temperature + 1.0, 9.0, 61, 70, "05:32", "19:17"),
        ),
    )
}
