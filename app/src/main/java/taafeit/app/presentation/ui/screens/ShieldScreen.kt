// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import taafeit.app.MainActivity
import taafeit.app.data.network.ShieldManager
import androidx.compose.ui.res.stringResource
import taafeit.app.R

@Composable
fun ShieldScreen(activity: MainActivity) {
    var shieldActive by remember { mutableStateOf(ShieldManager.isShieldActive) }
    var shieldStatus by remember { mutableStateOf(ShieldManager.lastCheckStatus) }
    var showGuide by remember { mutableStateOf(false) }

    // Periodically update status text
    LaunchedEffect(shieldActive) {
        while (shieldActive) {
            shieldStatus = ShieldManager.lastCheckStatus
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.active_shield), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (shieldActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (shieldActive) Icons.Default.Shield else Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.active_shield), style = MaterialTheme.typography.titleMedium)
                    Text(shieldStatus, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = shieldActive, onCheckedChange = {
                    shieldActive = it
                    if (it) ShieldManager.startShield(activity) else ShieldManager.stopShield()
                })
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.security_guide), style = MaterialTheme.typography.titleMedium)
                }
                Text("Technical recommendations for hardening your local network environment.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showGuide = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.read_guide))
                }
            }
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text(stringResource(R.string.security_guide)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• DNS Privacy: Use DoH (DNS-over-HTTPS) to prevent upstream hijacking and ISP tracking.", style = MaterialTheme.typography.bodySmall)
                    Text("• Network Shield: Enable this during public Wi-Fi sessions to detect ARP-spoofing in real-time.", style = MaterialTheme.typography.bodySmall)
                    Text("• Identity Rotation: Change your device model/manufacturer periodically to minimize tracking by telemetry.", style = MaterialTheme.typography.bodySmall)
                    Text("• Node Isolation: If a device is flagged as suspicious, isolate it to drop its traffic to your node.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) { Text("OK") }
            }
        )
    }
}
