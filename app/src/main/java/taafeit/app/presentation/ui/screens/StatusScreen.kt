// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import taafeit.app.BuildConfig
import taafeit.app.MainActivity
import taafeit.app.R
import taafeit.app.core.PrivilegeManager
import taafeit.app.data.network.NetworkScanner
import taafeit.app.data.network.OuiLookup
import taafeit.app.presentation.ui.components.CapabilityRow
import taafeit.app.presentation.ui.components.InfoRow
import taafeit.app.presentation.ui.components.PrivilegeBanner
import taafeit.app.presentation.ui.components.SectionCard
import taafeit.app.utils.MockLocationManager

@Composable
fun StatusScreen(activity: MainActivity) {
    val privileges by PrivilegeManager.state.collectAsStateWithLifecycle()

    var props by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var network by remember { mutableStateOf<NetworkScanner.NetworkInterfaceInfo?>(null) }
    var mockAppSelected by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var notificationsAllowed by remember { mutableStateOf(activity.hasNotificationPermission()) }

    // Re-check after the user returns from the system permission dialog.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = activity.hasNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        val snapshot = withContext(Dispatchers.IO) {
            val loadedProps = PrivilegeManager.getProps(force = true)
            val loadedNetwork = NetworkScanner.currentInterface()
            val mockSelected = MockLocationManager(activity).isMockAppSelected()
            Triple(loadedProps, loadedNetwork, mockSelected)
        }
        props = snapshot.first
        network = snapshot.second
        mockAppSelected = snapshot.third
        notificationsAllowed = activity.hasNotificationPermission()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.system_audit),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = {
                PrivilegeManager.refreshAsync(force = true)
                refreshKey++
            }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }

        PrivilegeBanner(privileges, onRequestShizuku = { activity.requestShizukuPermission() })

        if (loading) {
            CircularProgressIndicator()
        }

        SectionCard(title = stringResource(R.string.diagnostics)) {
            InfoRow(
                stringResource(R.string.security_status),
                stringResource(
                    if (privileges.rootAvailable) R.string.trusted_root
                    else R.string.limited_mode
                ),
            )
            InfoRow(stringResource(R.string.root_manager), privileges.rootManager ?: "—")
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
            CapabilityRow(
                stringResource(R.string.capability_system_props),
                privileges.canSystemProperties,
            )
            CapabilityRow(
                stringResource(R.string.capability_secure_settings),
                privileges.canSecureSettings,
            )
            CapabilityRow(
                stringResource(R.string.capability_neighbors),
                privileges.canReadNeighbors,
            )
            CapabilityRow(
                stringResource(R.string.capability_netfilter),
                privileges.canNetfilter,
            )
            CapabilityRow(
                stringResource(R.string.capability_mock_location),
                mockAppSelected == true,
            )
            CapabilityRow(
                stringResource(R.string.capability_notifications),
                notificationsAllowed,
            )
            if (!notificationsAllowed) {
                Button(onClick = {
                    activity.requestNotificationPermission()
                    notificationsAllowed = activity.hasNotificationPermission()
                }) { Text(stringResource(R.string.grant)) }
            }
        }

        SectionCard(title = stringResource(R.string.system_audit)) {
            InfoRow(
                stringResource(R.string.device_model),
                props["ro.product.model"].orEmpty().ifBlank { Build.MODEL },
                icon = Icons.Default.PhoneIphone,
            )
            InfoRow(
                stringResource(R.string.manufacturer),
                props["ro.product.manufacturer"].orEmpty().ifBlank { Build.MANUFACTURER },
                icon = Icons.Default.Factory,
            )
            InfoRow(stringResource(R.string.hardware), Build.HARDWARE, icon = Icons.Default.Memory)
            InfoRow(
                stringResource(R.string.android_version),
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                icon = Icons.Default.Android,
            )
            InfoRow(
                stringResource(R.string.security_patch),
                Build.VERSION.SECURITY_PATCH.ifBlank { "—" },
                icon = Icons.Default.Security,
            )
            InfoRow(
                stringResource(R.string.fingerprint),
                props["ro.build.fingerprint"].orEmpty().ifBlank { Build.FINGERPRINT },
                icon = Icons.Default.Fingerprint,
            )
            InfoRow(
                stringResource(R.string.kernel),
                System.getProperty("os.version") ?: "—",
                icon = Icons.Default.SettingsSuggest,
            )
            InfoRow(stringResource(R.string.build_id), Build.DISPLAY, icon = Icons.Default.Code)
        }

        SectionCard(title = stringResource(R.string.gateway)) {
            InfoRow(stringResource(R.string.interface_label), network?.name ?: "—")
            InfoRow(stringResource(R.string.gateway), network?.gatewayIp ?: "—")
            InfoRow(
                stringResource(R.string.oui_database),
                OuiLookup.size.toString(),
                icon = Icons.Default.Badge,
            )
            InfoRow(
                stringResource(R.string.app_version),
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}
