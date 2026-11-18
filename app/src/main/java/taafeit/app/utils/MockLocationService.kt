// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.utils

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import taafeit.app.R
import taafeit.app.core.Notifications
import timber.log.Timber

/**
 * Foreground service that keeps the mock location alive.
 *
 * The previous version returned `START_STICKY` and dereferenced `intent`
 * after a null check, so a system restart without an action left the service
 * running without `startForeground()`, which crashes on Android 12+.
 */
class MockLocationService : Service() {

    private lateinit var mockManager: MockLocationManager
    private var running = false

    override fun onCreate() {
        super.onCreate()
        mockManager = MockLocationManager(this)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            // System restart without data: never keep a headless service alive.
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> handleStart(intent)
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    mockManager.update(MockLocationManager.Point(lat, lng))
                }
            }
            ACTION_STOP -> stopMocking()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)

        try {
            startForegroundCompat(createNotification(getString(R.string.mock_location_starting)))
        } catch (e: Exception) {
            // Android 14+ throws when location permission is missing; never crash.
            Timber.w(e, "Unable to enter the foreground state")
            fail(e.message ?: getString(R.string.location_permission_required))
            return
        }

        val result = mockManager.start(MockLocationManager.Point(lat, lng))
        if (result.isFailure) {
            fail(result.exceptionOrNull()?.message ?: getString(R.string.mock_location_failed))
            return
        }

        _lastError.value = null
        running = true
        _running.value = true
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(formatPoint(lat, lng)))
    }

    private fun fail(message: String) {
        Timber.w("Mock location failed: %s", message)
        Notifications.show(
            this,
            id = NOTIFICATION_ID_ERROR,
            title = getString(R.string.mock_location_failed),
            text = message,
            channel = Notifications.CHANNEL_LOCATION,
        )
        _lastError.value = message
        running = false
        _running.value = false
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun stopMocking() {
        mockManager.stop()
        running = false
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) mockManager.stop()
        running = false
        _running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun createNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_LOCATION)
            .setContentTitle(getString(R.string.mock_location_active))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
    }

    private fun formatPoint(lat: Double, lng: Double): String =
        "%.5f, %.5f".format(lat, lng)

    companion object {
        const val ACTION_START = "taafeit.app.START_MOCK"
        const val ACTION_STOP = "taafeit.app.STOP_MOCK"
        const val ACTION_UPDATE = "taafeit.app.UPDATE_MOCK"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"

        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_ID_ERROR = 3002

        private val _running = MutableStateFlow(false)

        /** Real service state, so the UI cannot drift out of sync with prefs. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        fun clearError() {
            _lastError.value = null
        }

        fun start(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LAT, latitude)
                putExtra(EXTRA_LNG, longitude)
            }
            runCatching { context.startForegroundService(intent) }
                .onFailure {
                    Timber.w(it, "Unable to start the mock location service")
                    _lastError.value = it.message
                }
        }

        fun update(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_LAT, latitude)
                putExtra(EXTRA_LNG, longitude)
            }
            runCatching { context.startService(intent) }
                .onFailure { Timber.w(it, "Unable to update mock location") }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Timber.w(it, "Unable to stop mock location") }
        }
    }
}
