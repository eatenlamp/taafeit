// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.data.network

import kotlinx.coroutines.*
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

object ArpHelper {

    fun getSubnet(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            return ip.substring(0, ip.lastIndexOf(".") + 1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return null
    }

    suspend fun scanSubnet(subnet: String, main: taafeit.app.MainActivity): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val jobs = mutableListOf<Job>()
        (1..254).forEach { i ->
            jobs += launch {
                main.runRootCommands(listOf("ping -c 1 -W 1 $subnet$i > /dev/null 2>&1"))
            }
        }
        jobs.joinAll()
        delay(1000)
        return@withContext getLocalDevices(main)
    }

    fun getLocalDevices(main: taafeit.app.MainActivity): List<Pair<String, String>> {
        val devices = mutableMapOf<String, String>()
        try {
            val output = main.executeWithOutput("ip neighbor show")
            output.split("\n").forEach { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val ip = parts[0]
                    val mac = parts[4]
                    if (isValidMac(mac)) devices[ip] = mac
                }
            }
        } catch (_: Exception) {}
        return devices.toList()
    }

    private fun isValidMac(mac: String): Boolean {
        return mac.isNotEmpty() && mac != "00:00:00:00:00:00" && mac.contains(":")
    }
}
