package com.tsygankov.weatherforecast.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.Geocoder
import android.location.Address
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.tsygankov.weatherforecast.domain.repository.DeviceLocationRepository
import com.tsygankov.weatherforecast.domain.repository.GeoCoordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class AndroidDeviceLocationRepository internal constructor(
    private val context: Context,
    private val locationNameResolver: LocationNameResolver,
) : DeviceLocationRepository {
    constructor(context: Context) : this(context, AndroidLocationNameResolver(context))

    private val mutableLatestCoordinates = MutableStateFlow<GeoCoordinates?>(null)
    override val latestCoordinates: StateFlow<GeoCoordinates?> = mutableLatestCoordinates

    override suspend fun currentCoordinates(): Result<GeoCoordinates> = try {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Result.failure(LocationUnavailableException())
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
                as? LocationManager
            val providers = locationManager?.availableProviders().orEmpty()
            if (locationManager == null || providers.isEmpty()) {
                Result.failure(LocationUnavailableException())
            } else {
                val lastKnownLocation = locationManager.bestLastKnownLocation(providers)
                val location = lastKnownLocation
                    ?.takeIf(Location::isRecent) ?: withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                    locationManager.awaitCurrentLocation(
                        providers = providers,
                        executor = ContextCompat.getMainExecutor(context),
                    )
                } ?: lastKnownLocation
                if (location == null) {
                    Result.failure(LocationUnavailableException())
                } else {
                    val coordinates = GeoCoordinates(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        cityName = withTimeoutOrNull(GEOCODING_TIMEOUT_MILLIS) {
                            locationNameResolver.resolve(
                                latitude = location.latitude,
                                longitude = location.longitude,
                            )
                        },
                    )
                    mutableLatestCoordinates.value = coordinates
                    Result.success(coordinates)
                }
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        Result.failure(LocationUnavailableException())
    }
}

internal fun interface LocationNameResolver {
    suspend fun resolve(latitude: Double, longitude: Double): String?
}

private class AndroidLocationNameResolver(
    context: Context,
) : LocationNameResolver {
    private val geocoder = Geocoder(context, Locale.forLanguageTag("ru"))

    override suspend fun resolve(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
                }
            }
            addresses.firstOrNull()?.preferredCityName()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }
}

internal fun Address.preferredCityName(): String? = sequenceOf(
    locality,
    subAdminArea,
    adminArea,
    featureName,
).firstOrNull { value -> !value.isNullOrBlank() }

private fun LocationManager.availableProviders(): List<String> {
    if (!LocationManagerCompat.isLocationEnabled(this)) return emptyList()
    val preferredProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )
    val additionalProviders = runCatching { getProviders(true) }
        .getOrDefault(emptyList())
        .filterNot { it == LocationManager.PASSIVE_PROVIDER || it in preferredProviders }
    return (preferredProviders + additionalProviders).filter { provider ->
        runCatching { isProviderEnabled(provider) }.getOrDefault(false)
    }
}

private fun LocationManager.bestLastKnownLocation(providers: List<String>): Location? = providers
    .mapNotNull { provider ->
        runCatching { getLastKnownLocation(provider) }.getOrNull()
    }
    .maxByOrNull(Location::getTime)

private fun Location.isRecent(): Boolean {
    val elapsedNanos = elapsedRealtimeNanos
    if (elapsedNanos <= 0L) return false
    val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedNanos
    return ageNanos in 0L..LAST_KNOWN_LOCATION_MAX_AGE_NANOS
}

private suspend fun LocationManager.awaitCurrentLocation(
    providers: List<String>,
    executor: Executor,
): Location? = suspendCancellableCoroutine { continuation ->
    // Преобразуем callback API геолокации в suspend-вызов. При отмене корутины
    // invokeOnCancellation отменяет запросы к системным провайдерам.
    val cancellations = mutableListOf<CancellationSignal>()
    val lock = Any()
    var pendingProviders = providers.size

    fun finishProvider(location: Location?) {
        synchronized(lock) {
            if (!continuation.isActive) return
            if (location != null) {
                cancellations.forEach(CancellationSignal::cancel)
                continuation.resume(location)
            } else {
                pendingProviders -= 1
                if (pendingProviders == 0) continuation.resume(null)
            }
        }
    }

    continuation.invokeOnCancellation {
        synchronized(lock) {
            cancellations.forEach(CancellationSignal::cancel)
        }
    }
    providers.forEach { provider ->
        if (!continuation.isActive) return@forEach
        val cancellation = CancellationSignal()
        synchronized(lock) { cancellations += cancellation }
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                this,
                provider,
                cancellation,
                executor,
                ::finishProvider,
            )
        }.onFailure {
            finishProvider(null)
        }
    }
}

private class LocationUnavailableException : Exception()

private const val LOCATION_TIMEOUT_MILLIS = 15_000L
private const val GEOCODING_TIMEOUT_MILLIS = 3_000L
private const val LAST_KNOWN_LOCATION_MAX_AGE_MILLIS = 10 * 60 * 1_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val LAST_KNOWN_LOCATION_MAX_AGE_NANOS =
    LAST_KNOWN_LOCATION_MAX_AGE_MILLIS * NANOS_PER_MILLISECOND
