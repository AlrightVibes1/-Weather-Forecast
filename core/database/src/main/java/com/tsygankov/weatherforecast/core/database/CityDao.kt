package com.tsygankov.weatherforecast.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Transaction
    @Query("SELECT * FROM favorite_cities ORDER BY addedAt, id")
    fun observeAll(): Flow<List<CityWithCurrentWeather>>

    @Transaction
    @Query("SELECT * FROM favorite_cities WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CityWithCurrentWeather?>

    @Transaction
    @Query("SELECT * FROM favorite_cities WHERE id = :cityId LIMIT 1")
    fun observeForecast(cityId: Long): Flow<StoredForecast?>

    @Query("SELECT * FROM favorite_cities WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CityEntity?

    @Query("SELECT * FROM favorite_cities ORDER BY addedAt, id")
    suspend fun getAll(): List<CityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCity(city: CityEntity): Long

    @Query(
        """
        UPDATE favorite_cities
        SET name = :name,
            country = :country,
            adminArea = :adminArea,
            latitude = :latitude,
            longitude = :longitude,
            timezone = CASE
                WHEN EXISTS(SELECT 1 FROM current_weather WHERE cityId = :id) THEN timezone
                ELSE :timezone
            END
        WHERE id = :id
        """,
    )
    suspend fun updateCityMetadata(
        id: Long,
        name: String,
        country: String,
        adminArea: String?,
        latitude: Double,
        longitude: Double,
        timezone: String,
    ): Int

    @Transaction
    suspend fun upsert(city: CityEntity) {
        if (insertCity(city) == -1L) {
            check(
                updateCityMetadata(
                    id = city.id,
                    name = city.name,
                    country = city.country,
                    adminArea = city.adminArea,
                    latitude = city.latitude,
                    longitude = city.longitude,
                    timezone = city.timezone,
                ) == 1,
            )
        }
    }

    @Query("UPDATE favorite_cities SET timezone = :timezone WHERE id = :cityId")
    suspend fun updateCityTimezone(cityId: Long, timezone: String): Int

    @Query("DELETE FROM current_weather WHERE cityId = :cityId")
    suspend fun deleteCurrentWeather(cityId: Long)

    @Query("DELETE FROM hourly_weather WHERE cityId = :cityId")
    suspend fun deleteHourlyWeather(cityId: Long)

    @Query("DELETE FROM daily_weather WHERE cityId = :cityId")
    suspend fun deleteDailyWeather(cityId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCurrentWeather(currentWeather: CurrentWeatherEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHourlyWeather(hourlyWeather: List<HourlyWeatherEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDailyWeather(dailyWeather: List<DailyWeatherEntity>)

    @Transaction
    suspend fun replaceForecast(cache: ForecastCacheWrite) {
        require(cache.currentWeather.cityId == cache.cityId)
        require(cache.hourlyWeather.isNotEmpty())
        require(cache.dailyWeather.isNotEmpty())
        require(cache.hourlyWeather.all { it.cityId == cache.cityId })
        require(cache.dailyWeather.all { it.cityId == cache.cityId })
        check(updateCityTimezone(cache.cityId, cache.timezone) == 1)
        deleteCurrentWeather(cache.cityId)
        deleteHourlyWeather(cache.cityId)
        deleteDailyWeather(cache.cityId)
        insertCurrentWeather(cache.currentWeather)
        insertHourlyWeather(cache.hourlyWeather)
        insertDailyWeather(cache.dailyWeather)
    }

    @Query("DELETE FROM favorite_cities")
    suspend fun clear()
}
