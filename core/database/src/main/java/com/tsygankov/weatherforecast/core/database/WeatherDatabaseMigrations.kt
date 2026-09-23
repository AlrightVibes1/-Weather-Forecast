package com.tsygankov.weatherforecast.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorite_cities RENAME TO favorite_cities_v1")
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
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO favorite_cities (
                id, name, country, adminArea, latitude, longitude, timezone, addedAt
            )
            SELECT id, name, country, adminArea, latitude, longitude, timezone, 0
            FROM favorite_cities_v1
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS current_weather (
                cityId INTEGER NOT NULL,
                time TEXT,
                temperatureCelsius REAL,
                apparentTemperatureCelsius REAL,
                relativeHumidity INTEGER,
                pressure REAL,
                windSpeed REAL,
                weatherCode INTEGER,
                isDay INTEGER,
                updatedAt INTEGER,
                PRIMARY KEY(cityId),
                FOREIGN KEY(cityId) REFERENCES favorite_cities(id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO current_weather (
                cityId, temperatureCelsius, weatherCode, updatedAt
            )
            SELECT id, currentTemperature, currentWeatherCode, updatedAt
            FROM favorite_cities_v1
            WHERE currentTemperature IS NOT NULL
                OR currentWeatherCode IS NOT NULL
                OR updatedAt IS NOT NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS hourly_weather (
                cityId INTEGER NOT NULL,
                position INTEGER NOT NULL,
                time TEXT NOT NULL,
                temperatureCelsius REAL NOT NULL,
                apparentTemperatureCelsius REAL NOT NULL,
                humidity INTEGER NOT NULL,
                precipitationProbability INTEGER NOT NULL,
                weatherCode INTEGER NOT NULL,
                windSpeed REAL NOT NULL,
                PRIMARY KEY(cityId, position),
                FOREIGN KEY(cityId) REFERENCES favorite_cities(id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_hourly_weather_cityId ON hourly_weather(cityId)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_weather (
                cityId INTEGER NOT NULL,
                position INTEGER NOT NULL,
                date TEXT NOT NULL,
                maxTemperatureCelsius REAL NOT NULL,
                minTemperatureCelsius REAL NOT NULL,
                weatherCode INTEGER NOT NULL,
                precipitationProbability INTEGER NOT NULL,
                sunrise TEXT NOT NULL,
                sunset TEXT NOT NULL,
                PRIMARY KEY(cityId, position),
                FOREIGN KEY(cityId) REFERENCES favorite_cities(id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_daily_weather_cityId ON daily_weather(cityId)",
        )
        db.execSQL("DROP TABLE favorite_cities_v1")
    }
}
