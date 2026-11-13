// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.data.network

import kotlinx.coroutines.*
import taafeit.app.MainActivity
import timber.log.Timber

object ShieldManager {
    private var shieldJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var baselineGatewayMac: String? = null
    
    // Status tracking for UI
    var isShieldActive = false
        private set
    var lastCheckStatus = "Shield Idle"
        private set

    fun startShield(main: MainActivity) {
        if (isShieldActive) return
        stopShield()
        isShieldActive = true
        shieldJob = scope.launch {
            val subnet = ArpHelper.getSubnet() ?: run {
                lastCheckStatus = "Error: No Subnet"
                isShieldActive = false
                return@launch
            }
            val gatewayIp = subnet + "1"
            
            lastCheckStatus = "Establishing Baseline..."
            // Establish baseline using multiple attempts for reliability
            var attempts = 0
            while (baselineGatewayMac == null && attempts < 3) {
                baselineGatewayMac = ArpHelper.getLocalDevices(main).find { it.first == gatewayIp }?.second
                if (baselineGatewayMac == null) delay(2000)
                attempts++
            }

            if (baselineGatewayMac == null) {
                lastCheckStatus = "Warning: Gateway not resolved"
            } else {
                lastCheckStatus = "Protection Active: Monitoring $gatewayIp"
            }
            
            while (isActive) {
                val currentDevices = ArpHelper.getLocalDevices(main)
                val currentGatewayMac = currentDevices.find { it.first == gatewayIp }?.second
                
                if (baselineGatewayMac != null && currentGatewayMac != null && currentGatewayMac != baselineGatewayMac) {
                    Timber.w("ARP Spoofing detected! Gateway MAC changed from $baselineGatewayMac to $currentGatewayMac")
                    main.showNotification("SECURITY ALERT", "ARP Spoofing detected! Gateway identity has changed.")
                    
                    // Active defense: Force correct gateway entry and broadcast legitimate identity
                    main.runRootCommands(listOf(
                        "ip neighbor replace $gatewayIp lladdr $baselineGatewayMac dev wlan0 nud permanent",
                        "arping -c 2 -I wlan0 -S $subnet${(100..200).random()} $gatewayIp"
                    ))
                    lastCheckStatus = "ALERT: INTERCEPTION BLOCKED"
                } else {
                    lastCheckStatus = "Secure: Monitoring $gatewayIp"
                }
                
                // Scan for other anomalies (duplicate MACs)
                val macGroups = currentDevices.groupBy { it.second }
                macGroups.forEach { (mac, ips) ->
                    if (ips.size > 1 && mac != "00:00:00:00:00:00") {
                        main.showNotification("Network Warning", "Suspicious device detected: $mac is claiming multiple IPs.")
                    }
                }
                
                delay(8000) // Responsive check cycle
            }
        }
    }

    fun stopShield() {
        shieldJob?.cancel()
        shieldJob = null
        isShieldActive = false
        lastCheckStatus = "Shield Deactivated"
    }
}
