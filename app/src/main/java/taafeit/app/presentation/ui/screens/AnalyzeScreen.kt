// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import taafeit.app.MainActivity
import taafeit.app.R
import taafeit.app.core.PrivilegeManager
import taafeit.app.data.network.NetworkScanner
import taafeit.app.presentation.ui.components.PrivilegeBanner

@Composable
fun AnalyzeScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    val privileges by PrivilegeManager.state.collectAsStateWithLifecycle()

    var result by remember { mutableStateOf<NetworkScanner.ScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var resolveNames by remember { mutableStateOf(true) }
    var isolated by remember {
        mutableStateOf(activity.prefs.getStringSet("isolated_ips", emptySet()).orEmpty())
    }
    var busyIp by remember { mutableStateOf<String?>(null) }

    fun toggleBlock(host: NetworkScanner.Host) {
        if (!privileges.canNetfilter) {
            activity.showToast(activity.getString(R.string.requires_root))
            return
        }
        val blocked = host.ip in isolated
        busyIp = host.ip
        scope.launch {
            val outcome = if (blocked) {
                NetworkScanner.unisolate(host.ip)
            } else {
                NetworkScanner.isolate(host.ip)
            }
            busyIp = null

            if (outcome.ok) {
                val next = if (blocked) isolated - host.ip else isolated + host.ip
                isolated = next
                activity.prefs.edit().putStringSet("isolated_ips", next).apply()
                activity.showToast(
                    activity.getString(
                        if (blocked) R.string.unisolated else R.string.isolated,
                        host.ip,
                    )
                )
            } else {
                activity.showToast(
                    activity.getString(R.string.isolate_failed, outcome.summary())
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    stringResource(R.string.analyze_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.nodes_found, result?.hosts?.size ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    scanning = true
                    scope.launch {
                        result = NetworkScanner.scan(resolveNames)
                        scanning = false
                    }
                },
                enabled = !scanning && privileges.canReadNeighbors,
            ) {
                if (scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.pulse_scan))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        PrivilegeBanner(privileges, onRequestShizuku = { activity.requestShizukuPermission() })
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = resolveNames,
                onCheckedChange = { resolveNames = it },
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.resolve_names), style = MaterialTheme.typography.bodySmall)
        }

        if (!privileges.canReadNeighbors) {
            Text(
                stringResource(R.string.scan_requires_privilege),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val errorRes = result?.errorRes
        if (errorRes != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(errorRes))
                    result?.errorDetail
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                }
            }
        }

        result?.warningRes?.let { warning ->
            Text(
                stringResource(warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (result != null && result?.hosts?.isEmpty() == true && result?.errorRes == null) {
            Text(
                stringResource(R.string.no_hosts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(result?.hosts.orEmpty(), key = { it.ip }) { host ->
                HostCard(
                    host = host,
                    blocked = host.ip in isolated,
                    busy = busyIp == host.ip,
                    canBlock = privileges.canNetfilter,
                    onToggleBlock = { toggleBlock(host) },
                )
            }
            item {
                Text(
                    stringResource(R.string.isolate_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HostCard(
    host: NetworkScanner.Host,
    blocked: Boolean,
    busy: Boolean,
    canBlock: Boolean,
    onToggleBlock: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (host.isSuspicious) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(host.ip, style = MaterialTheme.typography.titleMedium)
                if (host.isGateway) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            stringResource(R.string.gateway),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (host.isSuspicious) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(R.string.suspicious),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                host.mac.orEmpty().ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            host.vendor?.let {
                Text("${stringResource(R.string.vendor_label)}: $it", style = MaterialTheme.typography.bodySmall)
            }
            host.hostname?.let {
                Text("${stringResource(R.string.hostname_label)}: $it", style = MaterialTheme.typography.bodySmall)
            }
            host.osGuessRes?.let { res ->
                Text(
                    "${stringResource(R.string.os_label)}: ${stringResource(res)}" +
                        (host.ttl?.let { ttl -> " · ${stringResource(R.string.ttl_label)} $ttl" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (host.duplicateCount > 1) {
                Text(
                    stringResource(R.string.duplicate_mac_addresses, host.duplicateCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onToggleBlock,
                    enabled = canBlock && !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (blocked) Icons.Default.LockOpen else Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (blocked) R.string.unisolate else R.string.isolate
                            )
                        )
                    }
                }
            }
        }
    }
}
