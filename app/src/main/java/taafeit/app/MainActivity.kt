// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import taafeit.app.core.LocaleHelper
import taafeit.app.core.Notifications
import taafeit.app.core.PrivilegeManager
import taafeit.app.core.PrivilegeState
import taafeit.app.core.ShizukuBridge
import taafeit.app.presentation.ui.screens.MainScreen
import taafeit.app.presentation.ui.theme.TaafeitTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    val prefs by lazy { getSharedPreferences(LocaleHelper.PREFS_NAME, MODE_PRIVATE) }

    val privileges: PrivilegeState
        get() = PrivilegeManager.state.value

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.i("Notification permission granted=%s", granted)
            if (!granted) showToast(getString(R.string.notification_permission_denied))
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        PrivilegeManager.start(applicationContext)

        setContent {
            TaafeitTheme {
                MainScreen(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-detect on every resume so granting Shizuku/root picks up immediately.
        PrivilegeManager.refreshAsync()
    }

    /** Android 13+ drops every notification without this runtime grant. */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifications.canPost(this)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun hasNotificationPermission(): Boolean = Notifications.canPost(this)

    fun requestShizukuPermission() {
        if (!ShizukuBridge.isRunning) {
            showToast(getString(R.string.shizuku_not_running))
            return
        }
        if (ShizukuBridge.isGranted) {
            ShizukuBridge.ensureBound()
            showToast(getString(R.string.shizuku_already_authorized))
            return
        }
        if (!ShizukuBridge.requestPermission()) {
            showToast(getString(R.string.shizuku_permission_failed))
        }
    }

    fun showToast(message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        showToast(getString(R.string.copied_to_clipboard))
    }

    fun showNotification(
        title: String,
        text: String,
        id: Int = NOTIFICATION_ID_ALERT,
    ): Boolean = Notifications.show(this, id, title, text)

    fun checkSelfPermissionCompat(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val NOTIFICATION_ID_ALERT = 1001
        const val NOTIFICATION_ID_STATUS = 1002
    }
}
