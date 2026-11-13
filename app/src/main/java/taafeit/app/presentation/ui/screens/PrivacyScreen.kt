// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import android.content.Intent
import android.provider.Settings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import taafeit.app.MainActivity
import taafeit.app.utils.MockLocationService
import androidx.compose.ui.res.stringResource
import taafeit.app.R

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var shizukuStatus by remember { mutableStateOf("Checking...") }
    var shizukuPermission by remember { mutableStateOf(false) }
    var isMocking by remember { mutableStateOf(activity.prefs.getBoolean("is_mocking", false)) }
    var lat by remember { mutableStateOf(activity.prefs.getString("mock_lat", "55.7558") ?: "55.7558") }
    var lng by remember { mutableStateOf(activity.prefs.getString("mock_lng", "37.6173") ?: "37.6173") }
    
    var dohEnabled by remember { mutableStateOf(activity.prefs.getBoolean("doh_enabled", false)) }
    val dohProviders = listOf("Google (dns.google)", "Cloudflare (1.1.1.1)", "AdGuard (dns.adguard.com)", "Custom")
    var selectedProvider by remember { mutableStateOf(activity.prefs.getString("doh_provider", dohProviders[0]) ?: dohProviders[0]) }
    var customDns by remember { mutableStateOf(activity.prefs.getString("doh_custom", "") ?: "") }

    LaunchedEffect(Unit) {
        shizukuStatus = if (activity.isShizukuRunning()) "Running" else "Not Running"
        shizukuPermission = activity.hasShizukuPermission()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.tab_non_root), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dns_privacy_doh), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.encrypt_dns), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = dohEnabled, onCheckedChange = { checked ->
                        dohEnabled = checked
                        activity.prefs.edit().putBoolean("doh_enabled", checked).apply()
                        scope.launch(Dispatchers.IO) {
                            val host = when (selectedProvider) {
                                "Google (dns.google)" -> "dns.google"
                                "Cloudflare (1.1.1.1)" -> "cloudflare-dns.com"
                                "AdGuard (dns.adguard.com)" -> "dns.adguard.com"
                                else -> customDns
                            }
                            activity.runRootCommands(listOf(
                                "settings put global private_dns_mode ${if(checked) "hostname" else "off"}",
                                "settings put global private_dns_specifier ${if(checked) host else ""}"
                            ))
                        }
                    })
                }
                
                if (dohEnabled) {
                    Text(stringResource(R.string.select_provider), style = MaterialTheme.typography.labelSmall)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dohProviders.forEach { provider ->
                            FilterChip(
                                selected = selectedProvider == provider,
                                onClick = {
                                    selectedProvider = provider
                                    activity.prefs.edit().putString("doh_provider", provider).apply()
                                },
                                label = { Text(provider.split(" ")[0]) }
                            )
                        }
                    }
                    if (selectedProvider == "Custom") {
                        OutlinedTextField(
                            value = customDns,
                            onValueChange = { 
                                customDns = it
                                activity.prefs.edit().putString("doh_custom", it).apply()
                            },
                            label = { Text(stringResource(R.string.custom_dns_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.mock_location), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val intent = Intent(activity, MockLocationService::class.java)
                        if (!isMocking) {
                            intent.action = MockLocationService.ACTION_START
                            intent.putExtra(MockLocationService.EXTRA_LAT, lat.toDoubleOrNull() ?: 0.0)
                            intent.putExtra(MockLocationService.EXTRA_LNG, lng.toDoubleOrNull() ?: 0.0)
                            activity.startForegroundService(intent)
                            isMocking = true
                            activity.prefs.edit().putBoolean("is_mocking", true).putString("mock_lat", lat).putString("mock_lng", lng).apply()
                        } else {
                            intent.action = MockLocationService.ACTION_STOP
                            activity.startService(intent)
                            isMocking = false
                            activity.prefs.edit().putBoolean("is_mocking", false).apply()
                        }
                    }, modifier = Modifier.weight(1f), colors = if (isMocking) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) { Text(if (isMocking) "Stop" else "Start") }
                    OutlinedButton(onClick = { try { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } catch (_: Exception) {} }, modifier = Modifier.weight(1f)) { Text("Dev Settings") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.shizuku_bridge), style = MaterialTheme.typography.titleMedium)
                }
                Text("${stringResource(R.string.shizuku_status_label)} $shizukuStatus", style = MaterialTheme.typography.bodySmall)
                if (!shizukuPermission) {
                    TextButton(onClick = { activity.requestShizukuPermission(1001) }) { Text("Authorize") }
                } else {
                    Text(stringResource(R.string.shizuku_authorized), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
