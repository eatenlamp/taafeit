// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import taafeit.app.MainActivity
import androidx.compose.ui.res.stringResource
import taafeit.app.R

@Composable
fun StatusScreen(activity: MainActivity) {
    var infoItems by remember { mutableStateOf(listOf<Triple<String, String, ImageVector>>()) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        val items = mutableListOf<Triple<String, String, ImageVector>>()
        val isRooted = withContext(Dispatchers.IO) { activity.isRooted() }
        
        items.add(Triple("Security Status", if(isRooted) "Trusted (Root)" else "Limited (Non-Root)", if(isRooted) Icons.Default.VerifiedUser else Icons.Default.GppMaybe))
        items.add(Triple("Device Model", activity.getProp("ro.product.model"), Icons.Default.PhoneIphone))
        items.add(Triple("Manufacturer", activity.getProp("ro.product.manufacturer"), Icons.Default.Factory))
        items.add(Triple("Hardware", Build.HARDWARE, Icons.Default.Memory))
        items.add(Triple("Android Version", Build.VERSION.RELEASE, Icons.Default.Android))
        items.add(Triple("Security Patch", Build.VERSION.SECURITY_PATCH, Icons.Default.Security))
        items.add(Triple("Fingerprint", activity.getProp("ro.build.fingerprint").ifEmpty { Build.FINGERPRINT }, Icons.Default.Fingerprint))
        items.add(Triple("Kernel", System.getProperty("os.version") ?: "Unknown", Icons.Default.SettingsSuggest))
        items.add(Triple("Build ID", Build.DISPLAY, Icons.Default.Code))
        
        infoItems = items
        isRefreshing = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("System Audit", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { isRefreshing = true }) { Icon(Icons.Default.Refresh, contentDescription = null) }
        }

        infoItems.forEach { (label, value, icon) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
