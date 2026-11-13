// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import taafeit.app.MainActivity
import taafeit.app.data.network.ArpHelper
import taafeit.app.domain.model.Device

@Composable
fun AnalyzeScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(listOf<Device>()) }
    var isScanning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Network Audit", style = MaterialTheme.typography.headlineSmall)
                Text("${devices.size} nodes identified", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                scope.launch {
                    isScanning = true
                    val subnet = ArpHelper.getSubnet()
                    if (subnet != null) {
                        val found = ArpHelper.scanSubnet(subnet, activity)
                        // Heuristic: Flag MACs appearing multiple times as suspicious
                        val macCounts = found.groupingBy { it.second }.eachCount()
                        devices = found.map { (ip, mac) ->
                            Device(
                                macAddress = mac,
                                ipAddress = ip,
                                vendor = null,
                                isSuspicious = (macCounts[mac] ?: 0) > 1 && mac != "00:00:00:00:00:00"
                            )
                        }
                    }
                    isScanning = false
                }
            }, enabled = !isScanning) {
                if (isScanning) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Pulse Scan")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.isSuspicious) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device.ipAddress, style = MaterialTheme.typography.titleMedium)
                                if (device.isSuspicious) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Warning, contentDescription = "Suspicious", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(device.macAddress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = {
                            activity.runRootCommands(listOf(
                                "iptables -I FORWARD -s ${device.ipAddress} -j DROP",
                                "ip route add blackhole ${device.ipAddress}"
                            ))
                            activity.showToast("Node isolated")
                        }) {
                            Icon(Icons.Default.Block, contentDescription = "Isolate")
                        }
                    }
                }
            }
        }
    }
}
