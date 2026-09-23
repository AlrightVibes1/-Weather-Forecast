package com.tsygankov.weatherforecast.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WeatherDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun `migration 1 to 2 preserves city and legacy current summary`() {
        createVersionOneDatabase().use { legacyDatabase ->
            legacyDatabase.execSQL(
                """
                INSERT INTO favorite_cities (
                    id, name, country, adminArea, latitude, longitude, timezone,
                    currentTemperature, currentWeatherCode, updatedAt
                ) VALUES (
                    524901, 'Москва', 'Россия', 'Москва', 55.75, 37.61, 'Europe/Moscow',
                    7.5, 3, 123456
                )
                """.trimIndent(),
            )
        }

        val migratedDatabase = Room.databaseBuilder(context, WeatherDatabase::class.java, TEST_DATABASE)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val database = migratedDatabase.openHelper.writableDatabase
            database.query("SELECT * FROM favorite_cities WHERE id = 524901").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Москва", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals("Europe/Moscow", cursor.getString(cursor.getColumnIndexOrThrow("timezone")))
                assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("addedAt")))
            }
            database.query("SELECT * FROM current_weather WHERE cityId = 524901").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(
                    7.5,
                    cursor.getDouble(cursor.getColumnIndexOrThrow("temperatureCelsius")),
                    0.0,
                )
                assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("weatherCode")))
                assertEquals(123456L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
            }
        } finally {
            migratedDatabase.close()
        }
    }

    private fun createVersionOneDatabase(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS favorite_cities (
                                id INTEGER NOT NULL,
                                name TEXT NOT NULL,
                                country TEXT NOT NULL,
                                adminArea TEXT,
                                latitude REAL NOT NULL,
                                longitude REAL NOT NULL,
                                timezone TEXT NOT NULL,
                                currentTemperature REAL,
                                currentWeatherCode INTEGER,
                                updatedAt INTEGER,
                                PRIMARY KEY(id)
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table " +
                                "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
                        )
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                                "VALUES(42, '$VERSION_ONE_IDENTITY_HASH')",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private companion object {
        const val TEST_DATABASE = "weather-migration-test"
        const val VERSION_ONE_IDENTITY_HASH = "4c3764e66d5341c7804d7291a6ab1603"
    }
}
