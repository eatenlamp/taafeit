// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import taafeit.app.MainActivity
import taafeit.app.R

/**
 * Notification plumbing with the runtime permission actually respected.
 * Before this, every alert on Android 13+ was dropped without a trace.
 */
object Notifications {

    const val CHANNEL_STATUS = "taafeit_status"
    const val CHANNEL_ALERTS = "taafeit_alerts"
    const val CHANNEL_LOCATION = "mock_location"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCATION,
                context.getString(R.string.notification_channel_location),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun show(
        context: Context,
        id: Int,
        title: String,
        text: String,
        channel: String = CHANNEL_ALERTS,
        ongoing: Boolean = false,
    ): Boolean {
        if (!canPost(context)) return false
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()

        return runCatching {
            manager.notify(id, notification)
            true
        }.getOrDefault(false)
    }
}
