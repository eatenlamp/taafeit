// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.utils

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import timber.log.Timber

class MockLocationManager(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    fun startMocking() {
        try {
            if (locationManager.allProviders.contains(providerName)) {
                locationManager.removeTestProvider(providerName)
            }
            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (_: Exception) {}
    }

    fun stopMocking() {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (_: Exception) {}
    }

    fun setLocation(lat: Double, lng: Double) {
        try {
            val location = Location(providerName).apply {
                latitude = lat
                longitude = lng
                altitude = 0.0
                time = System.currentTimeMillis()
                accuracy = 1.0f
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locationManager.setTestProviderLocation(providerName, location)
        } catch (_: Exception) {}
    }
}
