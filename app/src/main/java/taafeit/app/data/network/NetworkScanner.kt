// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.data.network

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import taafeit.app.R
import taafeit.app.core.PrivilegeManager
import taafeit.app.core.ShellResult
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Network discovery built on a single privileged shell call per phase.
 *
 * The previous implementation spawned 254 `su` processes for one scan; this one
 * runs one parallel ping sweep, then reads the kernel neighbour table.
 */
object NetworkScanner {

    private const val MAX_HOSTS = 254
    private const val TTL_PROBE_LIMIT = 64

    data class NetworkInterfaceInfo(
        val name: String,
        val localIp: String,
        val prefixLength: Int,
        val subnetBase: String,
        val gatewayIp: String?,
    ) {
        val isHotspotLike: Boolean
            get() = name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("wlan1")
    }

    data class Neighbor(
        val ip: String,
        val mac: String,
        val interfaceName: String?,
        val state: String?,
    )

    data class Host(
        val ip: String,
        val mac: String?,
        val vendor: String?,
        val hostname: String?,
        val ttl: Int?,
        val isGateway: Boolean,
        val isSuspicious: Boolean,
        val duplicateCount: Int = 0,
    ) {
        val osGuessRes: Int? get() = when {
            ttl == null || ttl <= 0 -> null
            ttl <= 64 -> R.string.os_linux_android
            ttl <= 128 -> R.string.os_windows
            else -> R.string.os_network_device
        }
    }

    data class ScanResult(
        val network: NetworkInterfaceInfo?,
        val hosts: List<Host> = emptyList(),
        @StringRes val errorRes: Int? = null,
        @StringRes val warningRes: Int? = null,
        val errorDetail: String? = null,
    )

    // --------------------------------------------------------------- discovery

    suspend fun currentInterface(): NetworkInterfaceInfo? = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<Triple<NetworkInterface, Inet4Address, Short>>()
        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                if (!intf.isUp || intf.isLoopback) continue
                for (address in intf.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        candidates += Triple(intf, address, intf.interfaceAddresses
                            .firstOrNull { it.address == address }?.networkPrefixLength ?: 24)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Unable to enumerate interfaces")
            return@withContext null
        }

        val picked = candidates.firstOrNull { it.first.name == "wlan0" && isPrivate(it.second) }
            ?: candidates.firstOrNull { isPrivate(it.second) }
            ?: candidates.firstOrNull()
            ?: return@withContext null

        val (intf, address, prefixLength) = picked
        val base = subnetBase(address, prefixLength.toInt())
        val gateway = detectGateway(intf.name) ?: (base + "1")

        NetworkInterfaceInfo(
            name = intf.name,
            localIp = address.hostAddress ?: "",
            prefixLength = prefixLength.toInt(),
            subnetBase = base,
            gatewayIp = gateway,
        )
    }

    private suspend fun detectGateway(interfaceName: String): String? {
        val result = PrivilegeManager.exec("ip route show default 2>/dev/null")
        val routes = result.output.lines().filter { it.isNotBlank() }
        if (routes.isEmpty()) return null
        val matching = routes.firstOrNull { it.contains(" dev $interfaceName") } ?: routes.first()
        return Regex("""via\s+(\d+\.\d+\.\d+\.\d+)""").find(matching)?.groupValues?.get(1)
    }

    suspend fun neighbors(): List<Neighbor> {
        val result = PrivilegeManager.exec("ip neigh show 2>/dev/null")
        if (!result.ok && result.output.isBlank()) return emptyList()
        return parseNeighbors(result.output)
    }

    // -------------------------------------------------------------------- scan

    suspend fun scan(resolveNames: Boolean = true): ScanResult = withContext(Dispatchers.IO) {
        val network = currentInterface()
            ?: return@withContext ScanResult(null, errorRes = R.string.scan_no_network)

        if (!PrivilegeManager.state.value.canReadNeighbors) {
            return@withContext ScanResult(network, errorRes = R.string.scan_requires_privilege)
        }

        val targets = hostAddresses(network)
        if (targets.isEmpty()) {
            return@withContext ScanResult(network, errorRes = R.string.scan_no_range)
        }

        val sweep = PrivilegeManager.execPrivileged(
            buildPingSweep(network.name, targets),
            timeoutMs = 60_000,
        )
        if (sweep.executor == taafeit.app.core.Executor.NONE) {
            return@withContext ScanResult(
                network,
                errorRes = R.string.scan_requires_privilege,
                errorDetail = sweep.error,
            )
        }

        val found = parseNeighbors(sweep.output)
            .filter { isValidMac(it.mac) }
            .filter { it.ip != network.localIp }

        if (found.isEmpty()) {
            return@withContext ScanResult(
                network,
                hosts = emptyList(),
                warningRes = R.string.scan_no_neighbors,
            )
        }

        val macOwners = found.groupBy { it.mac.lowercase() }.filterValues { it.size > 1 }
        val ttls = probeTtl(network.name, found.map { it.ip }.take(TTL_PROBE_LIMIT))
        val names = if (resolveNames) resolveHostnames(found.map { it.ip }) else emptyMap()

        val hosts = found.map { neighbor ->
            val duplicated = macOwners[neighbor.mac.lowercase()]?.size ?: 0
            Host(
                ip = neighbor.ip,
                mac = neighbor.mac,
                vendor = OuiLookup.vendorFor(neighbor.mac),
                hostname = names[neighbor.ip],
                ttl = ttls[neighbor.ip],
                isGateway = neighbor.ip == network.gatewayIp,
                isSuspicious = duplicated > 1,
                duplicateCount = if (duplicated > 1) duplicated else 0,
            )
        }.sortedWith(compareBy({ !it.isGateway }, { ipToLong(it.ip) }))

        ScanResult(network, hosts)
    }

    private fun buildPingSweep(interfaceName: String, targets: List<String>): String {
        val addresses = targets.joinToString(" ")
        return buildString {
            append("i=0; for ip in ")
            append(addresses)
            append("; do ")
            append("ping -c 1 -W 1 -I ")
            append(PrivilegeManager.quote(interfaceName))
            append(" \"\$ip\" >/dev/null 2>&1 & ")
            append("i=\$((i+1)); if [ \$((i % 64)) -eq 0 ]; then wait; fi; ")
            append("done; wait; ")
            append("ip neigh show 2>/dev/null")
        }
    }

    private suspend fun probeTtl(
        interfaceName: String,
        ips: List<String>,
    ): Map<String, Int> {
        if (ips.isEmpty()) return emptyMap()
        val script = buildString {
            append("for ip in ")
            append(ips.joinToString(" "))
            append("; do t=\$(ping -c 1 -W 1 -I ")
            append(PrivilegeManager.quote(interfaceName))
            append(" \"\$ip\" 2>/dev/null | sed -n 's/.*ttl=\\([0-9]*\\).*/\\1/p' | head -n 1); ")
            append("echo \"\$ip \${t:-0}\"; done")
        }
        val result = PrivilegeManager.execPrivileged(script, timeoutMs = 60_000)
        if (!result.ok) return emptyMap()
        return result.output.lines().mapNotNull { line ->
            val parts = line.trim().split(" ")
            if (parts.size != 2) return@mapNotNull null
            val ttl = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (ttl <= 0) null else parts[0] to ttl
        }.toMap()
    }

    private fun resolveHostnames(ips: List<String>): Map<String, String> {
        if (ips.isEmpty()) return emptyMap()
        val pool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
        return try {
            val tasks = ips.take(32).map { ip ->
                Callable {
                    val name = runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()
                    if (name.isNullOrBlank() || name == ip) null else ip to name
                }
            }
            pool.invokeAll(tasks, 4, TimeUnit.SECONDS)
                .mapNotNull { future -> runCatching { future.get() }.getOrNull() }
                .toMap()
        } catch (e: Exception) {
            Timber.w(e, "Reverse DNS failed")
            emptyMap()
        } finally {
            pool.shutdownNow()
        }
    }

    // --------------------------------------------------------------- isolation

    suspend fun isolate(ip: String): ShellResult =
        PrivilegeManager.execPrivileged(
            "iptables -I FORWARD -s ${PrivilegeManager.quote(ip)} -j DROP 2>&1 && " +
                "iptables -I INPUT -s ${PrivilegeManager.quote(ip)} -j DROP 2>&1",
            timeoutMs = 15_000,
        )

    suspend fun unisolate(ip: String): ShellResult =
        PrivilegeManager.execPrivileged(
            "iptables -D FORWARD -s ${PrivilegeManager.quote(ip)} -j DROP 2>&1; " +
                "iptables -D INPUT -s ${PrivilegeManager.quote(ip)} -j DROP 2>&1",
            timeoutMs = 15_000,
        )

    // ----------------------------------------------------------------- helpers

    fun parseNeighbors(raw: String): List<Neighbor> = raw.lines().mapNotNull { line ->
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.size < 4) return@mapNotNull null
        val ip = tokens[0]
        if (!isIpv4(ip)) return@mapNotNull null
        val devIndex = tokens.indexOf("dev")
        val macIndex = tokens.indexOf("lladdr")
        val mac = if (macIndex >= 0) tokens.getOrNull(macIndex + 1) else null
        if (mac == null || !isValidMac(mac)) return@mapNotNull null
        Neighbor(
            ip = ip,
            mac = mac.lowercase(),
            interfaceName = if (devIndex >= 0) tokens.getOrNull(devIndex + 1) else null,
            state = tokens.lastOrNull()?.takeIf { it in NEIGHBOR_STATES },
        )
    }

    fun isValidMac(mac: String): Boolean {
        if (mac.length != 17) return false
        if (mac == "00:00:00:00:00:00" || mac == "ff:ff:ff:ff:ff:ff") return false
        return MAC_REGEX.matches(mac)
    }

    private fun hostAddresses(network: NetworkInterfaceInfo): List<String> {
        val base = network.subnetBase
        val baseValue = ipToLong(base + "0")
        if (baseValue < 0) return emptyList()
        return (1..MAX_HOSTS).map { offset -> longToIp(baseValue + offset) }
    }

    private fun subnetBase(address: Inet4Address, prefixLength: Int): String {
        // A /16 or wider network would be unbounded; scan the /24 we live in.
        if (prefixLength < 24) {
            val ip = address.hostAddress ?: return "192.168.1."
            return ip.substringBeforeLast('.') + "."
        }
        val ipValue = ipToLong(address.hostAddress ?: return "192.168.1.")
        val mask = (-1 shl (32 - prefixLength)).toLong() and 0xFFFFFFFFL
        val network = ipValue and mask
        return "${(network shr 24) and 0xFF}.${(network shr 16) and 0xFF}.${(network shr 8) and 0xFF}."
    }

    private fun isPrivate(address: Inet4Address): Boolean {
        val bytes = address.address
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private fun isIpv4(value: String): Boolean = IPV4_REGEX.matches(value)

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return -1
        var result = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return -1
            if (octet !in 0..255) return -1
            result = (result shl 8) or octet.toLong()
        }
        return result
    }

    private fun longToIp(value: Long): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"

    private val MAC_REGEX = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")
    private val IPV4_REGEX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
    private val NEIGHBOR_STATES = setOf(
        "REACHABLE", "STALE", "DELAY", "PROBE", "PERMANENT", "NOARP", "FAILED", "INCOMPLETE",
    )
}
