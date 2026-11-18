// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import taafeit.app.MainActivity
import taafeit.app.R
import taafeit.app.core.PrivilegeManager
import taafeit.app.data.network.ShieldManager
import taafeit.app.presentation.ui.components.CapabilityRow
import taafeit.app.presentation.ui.components.InfoRow
import taafeit.app.presentation.ui.components.PrivilegeBanner
import taafeit.app.presentation.ui.components.SectionCard
import java.util.Date

@Composable
fun ShieldScreen(activity: MainActivity) {
    val privileges by PrivilegeManager.state.collectAsStateWithLifecycle()
    val shield by ShieldManager.state.collectAsStateWithLifecycle()
    var showGuide by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PrivilegeBanner(privileges, onRequestShizuku = { activity.requestShizukuPermission() })

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (shield.active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (shield.active) Icons.Default.Shield else Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.active_shield),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        shield.status.ifBlank { stringResource(R.string.shield_state_idle) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = shield.active,
                    enabled = privileges.canReadNeighbors || shield.active,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            ShieldManager.start(activity)
                        } else {
                            ShieldManager.stop()
                        }
                    },
                )
            }
        }

        if (!privileges.canReadNeighbors) {
            Text(
                stringResource(R.string.shield_monitoring_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionCard(title = stringResource(R.string.diagnostics)) {
            InfoRow(stringResource(R.string.gateway), shield.gatewayIp ?: "—")
            InfoRow(stringResource(R.string.gateway_mac), shield.gatewayMac ?: "—")
            InfoRow(stringResource(R.string.interface_label), shield.interfaceName ?: "—")
            InfoRow(stringResource(R.string.alerts_count), shield.alerts.toString())
            InfoRow(
                stringResource(R.string.last_check),
                if (shield.lastCheckAt == 0L) {
                    stringResource(R.string.shield_never_checked)
                } else {
                    timeFormat.format(Date(shield.lastCheckAt))
                },
            )
            CapabilityRow(
                stringResource(R.string.capability_neighbors),
                privileges.canReadNeighbors,
            )
            CapabilityRow(
                stringResource(R.string.capability_netfilter),
                privileges.canNetfilter,
            )
        }

        SectionCard(title = stringResource(R.string.security_guide), icon = Icons.AutoMirrored.Filled.HelpOutline) {
            Text(
                stringResource(R.string.guide_shield),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { showGuide = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.read_guide)) }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text(stringResource(R.string.security_guide)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.guide_dns), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.guide_shield), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.guide_identity), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.guide_vault), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}
