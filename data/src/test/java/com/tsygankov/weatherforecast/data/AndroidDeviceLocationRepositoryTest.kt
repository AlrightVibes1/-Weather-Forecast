package com.tsygankov.weatherforecast.data

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.Address
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidDeviceLocationRepositoryTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `reverse geocoding prefers city and safely falls back to region`() {
        val cityAddress = Address(Locale.forLanguageTag("ru")).apply {
            locality = "Омск"
            subAdminArea = "Омский район"
            adminArea = "Омская область"
        }
        val regionAddress = Address(Locale.forLanguageTag("ru")).apply {
            locality = " "
            subAdminArea = null
            adminArea = "Омская область"
        }

        assertEquals("Омск", cityAddress.preferredCityName())
        assertEquals("Омская область", regionAddress.preferredCityName())
    }

    @Test
    fun `missing permission returns failure before reading location`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val repository = AndroidDeviceLocationRepository(application)

        val result = repository.currentCoordinates()

        assertTrue(result.isFailure)
    }

    @Test
    fun `disabled device location returns failure without waiting for timeout`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = application.getSystemService(Context.LOCATION_SERVICE)
            as LocationManager
        shadowOf(locationManager).setLocationEnabled(false)
        val repository = AndroidDeviceLocationRepository(application)

        val result = repository.currentCoordinates()

        assertTrue(result.isFailure)
    }

    @Test
    fun `recent gps fix is used when network provider has no location`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = application.getSystemService(Context.LOCATION_SERVICE)
            as LocationManager
        val shadowLocationManager = shadowOf(locationManager).apply {
            setLocationEnabled(true)
            setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        }
        val gpsLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 55.7558
            longitude = 37.6173
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        shadowLocationManager.setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            gpsLocation,
        )
        val repository = AndroidDeviceLocationRepository(
            context = application,
            locationNameResolver = LocationNameResolver { _, _ -> "Москва" },
        )

        val result = repository.currentCoordinates()

        assertTrue(result.isSuccess)
        assertEquals(55.7558, result.getOrThrow().latitude, 0.0001)
        assertEquals(37.6173, result.getOrThrow().longitude, 0.0001)
        assertEquals("Москва", result.getOrThrow().cityName)
    }
}
