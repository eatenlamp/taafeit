// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.data.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import taafeit.app.R
import taafeit.app.core.Executor
import taafeit.app.core.PrivilegeManager
import taafeit.app.core.ShizukuBridge
import timber.log.Timber

/**
 * ARP watchdog for the current Wi-Fi network.
 *
 * Honest by design: it says it is "monitoring only" when there is no privilege
 * to read the neighbour table, and it resets its gateway baseline whenever the
 * network changes instead of screaming "spoofing" after a reconnect.
 */
object ShieldManager {

    private const val CHECK_INTERVAL_MS = 5_000L
    private const val ALERT_THROTTLE_MS = 60_000L

    data class ShieldState(
        val active: Boolean = false,
        val monitoringOnly: Boolean = false,
        val status: String = "",
        val gatewayIp: String? = null,
        val gatewayMac: String? = null,
        val interfaceName: String? = null,
        val alerts: Int = 0,
        val lastCheckAt: Long = 0L,
        val lastAlertAt: Long = 0L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ShieldState())
    val state: StateFlow<ShieldState> = _state.asStateFlow()

    private var job: Job? = null
    private var baselineMac: String? = null
    private var baselineNetwork: String? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        appContext = context.applicationContext
        val ctx = appContext ?: return

        val canRead = PrivilegeManager.state.value.canReadNeighbors
        _state.value = ShieldState(
            active = true,
            monitoringOnly = !canRead,
            status = ctx.getString(
                if (canRead) R.string.shield_state_starting else R.string.shield_state_unavailable
            ),
        )
        baselineMac = null
        baselineNetwork = null

        job = scope.launch {
            while (isActive) {
                runCatching { tick(ctx) }.onFailure {
                    Timber.w(it, "Shield tick failed")
                    _state.value = _state.value.copy(
                        status = ctx.getString(
                            R.string.shield_state_error,
                            it.message ?: it.javaClass.simpleName,
                        )
                    )
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        baselineMac = null
        baselineNetwork = null
        _state.value = _state.value.copy(
            active = false,
            status = appContext?.getString(R.string.shield_state_stopped).orEmpty(),
            gatewayMac = null,
        )
    }

    private suspend fun tick(context: Context) {
        val current = _state.value
        if (!PrivilegeManager.state.value.canReadNeighbors) {
            _state.value = current.copy(
                monitoringOnly = true,
                status = context.getString(R.string.shield_state_unavailable),
                lastCheckAt = System.currentTimeMillis(),
            )
            return
        }

        val network = NetworkScanner.currentInterface()
        if (network == null) {
            _state.value = current.copy(
                monitoringOnly = false,
                status = context.getString(R.string.shield_state_no_network),
                lastCheckAt = System.currentTimeMillis(),
            )
            return
        }

        val networkKey = "${network.name}|${network.localIp}|${network.gatewayIp}"
        if (baselineNetwork != networkKey) {
            baselineNetwork = networkKey
            baselineMac = null
            Timber.i("Shield baseline reset for %s", networkKey)
        }

        val neighbors = NetworkScanner.neighbors()
        val gatewayMac = neighbors.firstOrNull { it.ip == network.gatewayIp }?.mac

        if (baselineMac == null) {
            if (gatewayMac != null) {
                baselineMac = gatewayMac
                Timber.i("Shield baseline %s -> %s", network.gatewayIp, gatewayMac)
            }
            _state.value = current.copy(
                monitoringOnly = false,
                status = if (gatewayMac == null) {
                    context.getString(R.string.shield_state_waiting, network.gatewayIp.orEmpty())
                } else {
                    context.getString(R.string.shield_state_baseline, network.gatewayIp.orEmpty())
                },
                gatewayIp = network.gatewayIp,
                gatewayMac = gatewayMac,
                interfaceName = network.name,
                lastCheckAt = System.currentTimeMillis(),
            )
            return
        }

        val changed = gatewayMac != null && gatewayMac != baselineMac
        if (changed) {
            defend(context, network, gatewayMac)
            _state.value = current.copy(
                monitoringOnly = false,
                status = context.getString(R.string.shield_state_alert),
                gatewayIp = network.gatewayIp,
                gatewayMac = gatewayMac,
                interfaceName = network.name,
                alerts = current.alerts + 1,
                lastAlertAt = System.currentTimeMillis(),
                lastCheckAt = System.currentTimeMillis(),
            )
            return
        }

        reportDuplicates(context, neighbors)

        _state.value = current.copy(
            monitoringOnly = false,
            status = context.getString(
                R.string.shield_state_secure,
                network.gatewayIp.orEmpty(),
                network.name,
            ),
            gatewayIp = network.gatewayIp,
            gatewayMac = gatewayMac,
            interfaceName = network.name,
            lastCheckAt = System.currentTimeMillis(),
        )
    }

    private suspend fun defend(
        context: Context,
        network: NetworkScanner.NetworkInterfaceInfo,
        observedMac: String,
    ) {
        val trusted = baselineMac ?: return
        Timber.w("ARP gateway change: %s -> %s", trusted, observedMac)

        val state = _state.value
        val throttled = System.currentTimeMillis() - state.lastAlertAt < ALERT_THROTTLE_MS
        if (!throttled) {
            taafeit.app.core.Notifications.show(
                context,
                id = 2001,
                title = context.getString(R.string.shield_alert_spoof_title),
                text = context.getString(
                    R.string.shield_alert_spoof_text,
                    network.gatewayIp.orEmpty(),
                    trusted,
                    observedMac,
                ),
                channel = taafeit.app.core.Notifications.CHANNEL_ALERTS,
            )
        }

        if (PrivilegeManager.state.value.rootAvailable) {
            val gatewayIp = network.gatewayIp ?: return
            val fix = PrivilegeManager.execRoot(
                "ip neigh replace ${PrivilegeManager.quote(gatewayIp)} " +
                    "lladdr $trusted dev ${PrivilegeManager.quote(network.name)} nud permanent",
                timeoutMs = 10_000,
            )
            if (!fix.ok) Timber.w("Failed to restore gateway entry: %s", fix.error)
        }
    }

    private suspend fun reportDuplicates(
        context: Context,
        neighbors: List<NetworkScanner.Neighbor>,
    ) {
        val duplicates = neighbors.groupBy { it.mac }.filterValues { it.size > 1 }
        if (duplicates.isEmpty()) return
        val state = _state.value
        if (System.currentTimeMillis() - state.lastAlertAt < ALERT_THROTTLE_MS) return

        val (mac, owners) = duplicates.entries.first()
        taafeit.app.core.Notifications.show(
            context,
            id = 2002,
            title = context.getString(R.string.shield_alert_duplicate_title),
            text = context.getString(
                R.string.shield_alert_duplicate_text,
                mac,
                owners.size,
                owners.joinToString { it.ip },
            ),
            channel = taafeit.app.core.Notifications.CHANNEL_ALERTS,
        )
    }

    /** Exposed for the status screen: which executor the watchdog would use. */
    fun executorLabel(): String = when {
        PrivilegeManager.state.value.rootAvailable -> "root"
        ShizukuBridge.isBound -> "Shizuku"
        else -> "none"
    }

    fun currentExecutor(): Executor = when {
        PrivilegeManager.state.value.rootAvailable -> Executor.ROOT
        ShizukuBridge.isBound -> Executor.SHIZUKU
        else -> Executor.NONE
    }
}
