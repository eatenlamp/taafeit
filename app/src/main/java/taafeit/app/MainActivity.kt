// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.io.DataOutputStream
import androidx.activity.compose.setContent
import taafeit.app.presentation.ui.screens.MainScreen
import taafeit.app.presentation.ui.theme.TaafeitTheme
import rikka.shizuku.Shizuku
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import timber.log.Timber

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : androidx.activity.ComponentActivity() {

    val prefs by lazy { getSharedPreferences("taafeit_prefs", MODE_PRIVATE) }
    private var shizukuService: IShizukuService? = null
    
    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            shizukuService = IShizukuService.Stub.asInterface(binder)
            Timber.i("Shizuku Service Connected")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            shizukuService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        updateLocale()
        super.onCreate(savedInstanceState)
        
        bindShizuku()
        checkIntegrity()
        
        setContent {
            TaafeitTheme {
                MainScreen(this)
            }
        }
    }

    private fun bindShizuku() {
        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val args = Shizuku.UserServiceArgs(ComponentName(this, ShizukuService::class.java))
                    .daemon(false)
                    .processNameSuffix("privileged")
                Shizuku.bindUserService(args, shizukuConnection)
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind Shizuku service")
            }
        }
    }

    private fun checkIntegrity() {
        if (!isRooted() && !isShizukuRunning()) {
            showNotification("Security Baseline", "Running in limited mode. Enable Shizuku or Root for protection.")
        }
    }

    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-v"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuRunning(): Boolean = Shizuku.pingBinder()

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestShizukuPermission(requestCode: Int) {
        if (isShizukuRunning()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun runRootCommands(commands: List<String>): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            for (command in commands) {
                outputStream.writeBytes("$command\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    fun getProp(name: String): String {
        var output = executeShellWithOutput("getprop $name")
        if (output.isEmpty() && shizukuService != null) {
            try {
                output = shizukuService!!.runCommand("getprop $name")
            } catch (_: Exception) {}
        }
        if (output.isEmpty()) {
            output = executeWithOutput("getprop $name")
        }
        return output
    }

    fun executeShellWithOutput(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun executeWithOutput(command: String): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    fun applyVpnMasking(enabled: Boolean): Boolean {
        val value = if (enabled) "0" else "1"
        val commands = mutableListOf(
            "resetprop net.vpn.status $value",
            "resetprop net.dns1 8.8.8.8",
            "resetprop net.dns2 8.8.4.4",
            "resetprop ro.build.selinux 1",
            "setprop vpn.active $value"
        )
        return runRootCommands(commands)
    }

    fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Taafeit", text)
        clipboard.setPrimaryClip(clip)
        showToast("Copied to clipboard")
    }

    fun showNotification(title: String, text: String) {
        try {
            val channelId = "taafeit_status"
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(channelId, "Taafeit Status", android.app.NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true).build()
            notificationManager.notify(1, notification)
        } catch (_: Exception) {}
    }

    fun exportSettings(data: String) {
        try {
            val file = java.io.File(getExternalFilesDir(null), "taafeit_backup.txt")
            file.writeText(data)
            showToast("Exported to ${file.absolutePath}")
        } catch (e: Exception) {
            showToast("Export failed: ${e.message}")
        }
    }

    private fun updateLocale() {
        val lang = prefs.getString("language", "en") ?: "en"
        val currentLocale = resources.configuration.locales[0]
        if (currentLocale.language == lang) return
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
