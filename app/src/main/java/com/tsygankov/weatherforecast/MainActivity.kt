package com.tsygankov.weatherforecast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tsygankov.weatherforecast.navigation.WeatherForecastNavHost

class MainActivity : ComponentActivity() {
    // Android вызывает onCreate при первом создании Activity и при её пересоздании.
    // Здесь Activity настраивает окно и размещает Compose с темой и графом навигации.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherForecastAppRoute {
                WeatherForecastNavHost()
            }
        }
    }
}
