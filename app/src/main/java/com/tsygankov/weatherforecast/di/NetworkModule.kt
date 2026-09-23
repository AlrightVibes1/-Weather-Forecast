package com.tsygankov.weatherforecast.di

import com.tsygankov.weatherforecast.core.network.GeocodingApi
import com.tsygankov.weatherforecast.core.network.WeatherApi
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val weatherRetrofit = named("weatherRetrofit")
private val geocodingRetrofit = named("geocodingRetrofit")

internal val networkModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    single(weatherRetrofit) {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    single(geocodingRetrofit) {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    single<WeatherApi> {
        get<Retrofit>(weatherRetrofit).create(WeatherApi::class.java)
    }
    single<GeocodingApi> {
        get<Retrofit>(geocodingRetrofit).create(GeocodingApi::class.java)
    }
}
