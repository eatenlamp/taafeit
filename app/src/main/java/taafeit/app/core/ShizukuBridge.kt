// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import taafeit.app.BuildConfig
import taafeit.app.IShizukuService
import taafeit.app.ShizukuService
import timber.log.Timber

/**
 * Owns the Shizuku binder lifecycle and the privileged user service.
 *
 * Shizuku runs our [ShizukuService] as uid 2000 (shell), which is enough for
 * `getprop`, `settings`, `ip neigh`, `ping` and similar ADB-level commands —
 * but not for `iptables` or `resetprop`, which still need root.
 */
object ShizukuBridge {

    const val PERMISSION_REQUEST_CODE = 4021

    private var appContext: Context? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    private var service: IShizukuService? = null

    @Volatile
    private var binding = false

    private var listenersRegistered = false

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound.asStateFlow()

    val isRunning: Boolean
        get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val isGranted: Boolean
        get() = runCatching {
            isRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    val isBound: Boolean
        get() = service != null

    val canRequest: Boolean
        get() = isRunning

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            service = IShizukuService.Stub.asInterface(binder)
            _bound.value = service != null
            Timber.i("Shizuku user service connected")
            PrivilegeManager.onShizukuBound()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = false
            service = null
            _bound.value = false
            Timber.w("Shizuku user service disconnected")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (listenersRegistered) return
        listenersRegistered = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky {
                Timber.i("Shizuku binder received")
                ensureBound()
            }
            Shizuku.addBinderDeadListener {
                Timber.w("Shizuku binder died")
                service = null
                binding = false
                _bound.value = false
            }
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE &&
                    grantResult == PackageManager.PERMISSION_GRANTED
                ) {
                    PrivilegeManager.refreshAsync(force = true)
                    ensureBound()
                }
            }
        }.onFailure { Timber.w(it, "Shizuku listeners are unavailable") }
        if (isRunning && isGranted) ensureBound()
    }

    fun requestPermission(): Boolean {
        if (!isRunning) return false
        if (isGranted) {
            ensureBound()
            return true
        }
        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }.getOrElse {
            Timber.w(it, "Shizuku permission request failed")
            false
        }
    }

    @Synchronized
    fun ensureBound() {
        val context = appContext ?: return
        if (!isRunning || !isGranted) return
        if (service != null || binding) return

        val args = userServiceArgs ?: Shizuku.UserServiceArgs(
            ComponentName(context, ShizukuService::class.java)
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
            .also { userServiceArgs = it }

        binding = true
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                binding = false
                Timber.w(it, "Failed to bind the Shizuku user service")
            }
    }

    /** Executes [command] through the Shizuku user service (shell uid). */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val binder = service ?: return@withContext ShellResult.failure(
            "Shizuku service is not bound",
            Executor.SHIZUKU,
        )
        try {
            val raw = binder.runCommand(command) ?: return@withContext ShellResult.failure(
                "Shizuku returned an empty reply",
                Executor.SHIZUKU,
            )
            val (stdout, stderr, exitCode) = ShellProtocol.decode(raw)
            ShellResult(
                ok = exitCode == 0,
                exitCode = exitCode,
                output = stdout.trim(),
                error = stderr.trim(),
                executor = Executor.SHIZUKU,
            )
        } catch (e: Exception) {
            Timber.w(e, "Shizuku command failed: %s", command)
            ShellResult.failure(e.message ?: "Shizuku call failed", Executor.SHIZUKU)
        }
    }
}
