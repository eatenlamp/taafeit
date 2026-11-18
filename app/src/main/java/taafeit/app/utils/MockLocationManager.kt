// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Test-provider based location mocking.
 *
 * Two real bugs from the previous version are gone:
 *  - the API 31-only `ProviderProperties` overload is no longer called on
 *    Android 10/11 (it threw `NoSuchMethodError`, which is an Error, so the
 *    old `catch (Exception)` never caught it);
 *  - the fix is re-published every second, because a test location goes stale
 *    and apps stop receiving it.
 */
class MockLocationManager(private val context: Context) {

    data class Point(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double = 0.0,
    )

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    @Volatile
    private var current: Point? = null

    /** True when Taafeit is selected as the mock location app in Developer options. */
    fun isMockAppSelected(): Boolean = try {
        removeTestProviderQuietly(PROBE_PROVIDER)
        addTestProvider(PROBE_PROVIDER)
        removeTestProviderQuietly(PROBE_PROVIDER)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        false
    }

    fun start(point: Point): Result<Unit> {
        if (!isMockAppSelected()) {
            return Result.failure(
                IllegalStateException(
                    "Select Taafeit as the mock location app in Developer options"
                )
            )
        }
        return try {
            current = point
            removeTestProviderQuietly(LocationManager.GPS_PROVIDER)
            addTestProvider(LocationManager.GPS_PROVIDER)
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            publish(point)

            ticker?.cancel()
            ticker = scope.launch {
                while (isActive) {
                    current?.let { publish(it) }
                    delay(REFRESH_INTERVAL_MS)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "Unable to start location mocking")
            stop()
            Result.failure(e)
        }
    }

    fun update(point: Point) {
        current = point
        if (ticker?.isActive != true) return
        publish(point)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        current = null
        runCatching {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
        }
        removeTestProviderQuietly(LocationManager.GPS_PROVIDER)
    }

    private fun publish(point: Point) {
        try {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = point.latitude
                longitude = point.longitude
                altitude = point.altitude
                accuracy = 3.0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                speed = 0f
                bearing = 0f
                verticalAccuracyMeters = 3f
                speedAccuracyMetersPerSecond = 0.5f
                bearingAccuracyDegrees = 1f
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        } catch (e: Exception) {
            Timber.w(e, "Unable to publish mock location")
        }
    }

    @SuppressLint("InlinedApi") // Same integer values as the deprecated Criteria constants.
    private fun addTestProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.addTestProvider(
                provider,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE,
            )
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                provider,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE,
            )
        }
    }

    private fun removeTestProviderQuietly(provider: String) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val PROBE_PROVIDER = "taafeit_probe"
        const val REFRESH_INTERVAL_MS = 1_000L
    }
}
