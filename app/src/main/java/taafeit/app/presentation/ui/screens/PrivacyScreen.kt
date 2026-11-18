// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import taafeit.app.MainActivity
import taafeit.app.R
import taafeit.app.core.PrivilegeManager
import taafeit.app.presentation.ui.components.CapabilityRow
import taafeit.app.presentation.ui.components.InfoRow
import taafeit.app.presentation.ui.components.PrivilegeBanner
import taafeit.app.presentation.ui.components.SectionCard
import taafeit.app.utils.MockLocationManager
import taafeit.app.utils.MockLocationService

private const val PROVIDER_GOOGLE = "Google (dns.google)"
private const val PROVIDER_CLOUDFLARE = "Cloudflare (1.1.1.1)"
private const val PROVIDER_ADGUARD = "AdGuard (dns.adguard-dns.com)"
private const val PROVIDER_CUSTOM = "Custom"

private val providers = listOf(
    PROVIDER_GOOGLE,
    PROVIDER_CLOUDFLARE,
    PROVIDER_ADGUARD,
    PROVIDER_CUSTOM,
)

private fun hostFor(provider: String, custom: String): String = when (provider) {
    PROVIDER_GOOGLE -> "dns.google"
    PROVIDER_CLOUDFLARE -> "one.one.one.one"
    PROVIDER_ADGUARD -> "dns.adguard-dns.com"
    else -> custom.trim()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    val privileges by PrivilegeManager.state.collectAsStateWithLifecycle()
    val mockRunning by MockLocationService.running.collectAsStateWithLifecycle()
    val mockError by MockLocationService.lastError.collectAsStateWithLifecycle()

    var dohEnabled by remember { mutableStateOf(false) }
    var provider by remember {
        mutableStateOf(
            activity.prefs.getString("doh_provider", PROVIDER_GOOGLE) ?: PROVIDER_GOOGLE
        )
    }
    var customDns by remember { mutableStateOf(activity.prefs.getString("doh_custom", "").orEmpty()) }
    var currentMode by remember { mutableStateOf("—") }
    var dnsBusy by remember { mutableStateOf(false) }

    var latitude by remember { mutableStateOf(activity.prefs.getString("mock_lat", "55.7558") ?: "55.7558") }
    var longitude by remember { mutableStateOf(activity.prefs.getString("mock_lng", "37.6173") ?: "37.6173") }
    var mockAppSelected by remember { mutableStateOf<Boolean?>(null) }

    fun coordinates(): Pair<Double, Double>? {
        val lat = latitude.toDoubleOrNull() ?: return null
        val lng = longitude.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    fun startMocking() {
        val pair = coordinates()
        if (pair == null) {
            activity.showToast(activity.getString(R.string.invalid_coordinates))
            return
        }
        if (mockAppSelected == false) {
            activity.showToast(activity.getString(R.string.mock_location_not_selected))
            return
        }
        activity.prefs.edit()
            .putBoolean("is_mocking", true)
            .putString("mock_lat", latitude)
            .putString("mock_lng", longitude)
            .apply()
        MockLocationService.start(activity, pair.first, pair.second)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            startMocking()
        } else {
            activity.showToast(activity.getString(R.string.location_permission_required))
        }
    }

    LaunchedEffect(mockError) {
        mockError?.let { message ->
            activity.showToast(activity.getString(R.string.mock_location_failed) + ": " + message)
            MockLocationService.clearError()
        }
    }

    fun applyDns(enabled: Boolean, selectedProvider: String, custom: String) {
        if (!privileges.canSecureSettings) {
            activity.showToast(activity.getString(R.string.doh_requires_privilege))
            return
        }
        val host = hostFor(selectedProvider, custom)
        if (enabled && host.isBlank()) {
            activity.showToast(activity.getString(R.string.custom_dns_hint))
            return
        }

        dnsBusy = true
        scope.launch {
            val mode = if (enabled) "hostname" else "off"
            val write = if (enabled) {
                "settings put global private_dns_mode hostname; " +
                    "settings put global private_dns_specifier ${PrivilegeManager.quote(host)}"
            } else {
                "settings put global private_dns_mode off; " +
                    "settings delete global private_dns_specifier"
            }
            val result = PrivilegeManager.execPrivileged(write, 15_000)
            val verify = PrivilegeManager.execPrivileged("settings get global private_dns_mode", 8_000)
            val actual = verify.output.lines().firstOrNull()?.trim().orEmpty().ifBlank { "off" }

            currentMode = actual
            dohEnabled = actual == "hostname"
            dnsBusy = false

            if (!result.ok || actual != mode) {
                activity.showToast(
                    activity.getString(R.string.doh_failed, result.summary())
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val probe = withContext(Dispatchers.IO) {
            val settings = PrivilegeManager.execPrivileged(
                "settings get global private_dns_mode; settings get global private_dns_specifier",
                8_000,
            )
            val lines = settings.output.lines().map { it.trim() }
            val appSelected = MockLocationManager(activity).isMockAppSelected()
            (lines to appSelected)
        }
        val (lines, appSelected) = probe
        currentMode = lines.firstOrNull()?.ifBlank { "off" } ?: "off"
        dohEnabled = currentMode == "hostname"
        mockAppSelected = appSelected
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PrivilegeBanner(privileges, onRequestShizuku = { activity.requestShizukuPermission() })

        SectionCard(title = stringResource(R.string.dns_privacy_doh), icon = Icons.Default.Dns) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.encrypt_dns),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.doh_current, currentMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = dohEnabled,
                    enabled = !dnsBusy,
                    onCheckedChange = { checked ->
                        dohEnabled = checked
                        applyDns(checked, provider, customDns)
                    },
                )
            }

            if (dohEnabled) {
                Text(
                    stringResource(R.string.select_provider),
                    style = MaterialTheme.typography.labelSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { item ->
                        FilterChip(
                            selected = provider == item,
                            onClick = {
                                provider = item
                                activity.prefs.edit().putString("doh_provider", item).apply()
                                applyDns(true, item, customDns)
                            },
                            label = { Text(item.substringBefore(" (")) },
                        )
                    }
                }

                if (provider == PROVIDER_CUSTOM) {
                    OutlinedTextField(
                        value = customDns,
                        onValueChange = {
                            customDns = it
                            activity.prefs.edit().putString("doh_custom", it).apply()
                        },
                        label = { Text(stringResource(R.string.custom_dns_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { applyDns(true, provider, customDns) },
                        enabled = !dnsBusy,
                    ) { Text(stringResource(R.string.apply)) }
                }
            }

            if (!privileges.canSecureSettings) {
                Text(
                    stringResource(R.string.doh_requires_privilege),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (dnsBusy) {
                CircularProgressIndicator(modifier = Modifier.width(20.dp))
            }
        }

        SectionCard(title = stringResource(R.string.mock_location), icon = Icons.Default.LocationOn) {
            Text(
                stringResource(
                    if (mockAppSelected == true) {
                        R.string.mock_location_selected
                    } else {
                        R.string.mock_location_not_selected
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (mockAppSelected == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                stringResource(R.string.mock_location_instruction),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text(stringResource(R.string.latitude)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text(stringResource(R.string.longitude)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (mockRunning) {
                            MockLocationService.stop(activity)
                            activity.prefs.edit().putBoolean("is_mocking", false).apply()
                            return@Button
                        }
                        val fine = activity.checkSelfPermissionCompat(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                        val coarse = activity.checkSelfPermissionCompat(
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (!fine && !coarse) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        } else {
                            startMocking()
                        }
                    },
                    colors = if (mockRunning) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (mockRunning) R.string.stop else R.string.start))
                }

                OutlinedButton(
                    onClick = {
                        val pair = coordinates()
                        if (pair == null) {
                            activity.showToast(activity.getString(R.string.invalid_coordinates))
                            return@OutlinedButton
                        }
                        MockLocationService.update(activity, pair.first, pair.second)
                    },
                    enabled = mockRunning,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.update_position)) }
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.developer_settings)) }
        }

        SectionCard(title = stringResource(R.string.shizuku_bridge), icon = Icons.Default.FlashOn) {
            InfoRow(
                stringResource(R.string.shizuku_status_label),
                stringResource(
                    if (privileges.shizukuRunning) R.string.shizuku_running
                    else R.string.shizuku_not_running_short
                ),
            )
            InfoRow(
                stringResource(R.string.shizuku_authorized),
                stringResource(
                    if (privileges.shizukuGranted) R.string.granted else R.string.not_granted
                ),
            )
            CapabilityRow(stringResource(R.string.capability_secure_settings), privileges.canSecureSettings)
            CapabilityRow(stringResource(R.string.capability_neighbors), privileges.canReadNeighbors)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { activity.requestShizukuPermission() },
                    enabled = privileges.shizukuRunning && !privileges.shizukuGranted,
                ) { Text(stringResource(R.string.authorize)) }

                OutlinedButton(
                    onClick = { PrivilegeManager.refreshAsync(force = true) },
                ) { Text(stringResource(R.string.refresh)) }
            }

            if (!privileges.shizukuRunning) {
                Text(
                    stringResource(R.string.shizuku_not_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
