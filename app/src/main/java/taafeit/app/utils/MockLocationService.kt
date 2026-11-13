// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MockLocationService : Service() {
    private lateinit var mockManager: MockLocationManager
    private val CHANNEL_ID = "mock_location"

    override fun onCreate() {
        super.onCreate()
        mockManager = MockLocationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        createNotificationChannel()
        
        when (action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val notification = createNotification("Mocking: $lat, $lng")
                startForeground(1, notification)
                mockManager.startMocking()
                mockManager.setLocation(lat, lng)
            }
            ACTION_STOP -> {
                mockManager.stopMocking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Mock Location", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Taafeit Privacy")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "taafeit.app.START_MOCK"
        const val ACTION_STOP = "taafeit.app.STOP_MOCK"
        const val ACTION_UPDATE = "taafeit.app.UPDATE_MOCK"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
    }
}
