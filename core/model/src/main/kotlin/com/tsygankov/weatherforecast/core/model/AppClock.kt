package com.tsygankov.weatherforecast.core.model

fun interface AppClock {
    fun currentTimeMillis(): Long
}
