// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun VaultScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(activity.prefs.getStringSet("profiles", emptySet())?.toList() ?: emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile Vault", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary) }
        }
        
        Text("Securely manage and deploy identity benchmarks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        if (profiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("No profiles stored", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(profiles) { name ->
                    ProfileCard(name, activity) {
                        profiles = activity.prefs.getStringSet("profiles", emptySet())?.toList() ?: emptyList()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Capture Current Identity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will save your current device properties as a benchmark.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it }, label = { Text("Profile Alias") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newProfileName.isNotEmpty()) {
                        val set = activity.prefs.getStringSet("profiles", emptySet())?.toMutableSet() ?: mutableSetOf()
                        set.add(newProfileName)
                        activity.prefs.edit().putStringSet("profiles", set)
                            .putString("profile_${newProfileName}_model", activity.getProp("ro.product.model"))
                            .putString("profile_${newProfileName}_manufacturer", activity.getProp("ro.product.manufacturer"))
                            .apply()
                        profiles = set.toList()
                        newProfileName = ""
                        showAddDialog = false
                    }
                }) { Text("Vault It") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ProfileCard(name: String, activity: MainActivity, onUpdate: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = activity.prefs.getString("profile_${name}_model", "Unknown") ?: "Unknown"
    val man = activity.prefs.getString("profile_${name}_manufacturer", "Unknown") ?: "Unknown"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$model ($man)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    activity.runRootCommands(listOf(
                        "resetprop ro.product.model \"$model\"",
                        "resetprop ro.product.manufacturer \"$man\""
                    ))
                    launch(Dispatchers.Main) { activity.showToast("Deployed: $name") }
                }
            }) { Icon(Icons.Default.CloudUpload, contentDescription = "Deploy", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = {
                val set = activity.prefs.getStringSet("profiles", emptySet())?.toMutableSet() ?: mutableSetOf()
                set.remove(name)
                activity.prefs.edit().putStringSet("profiles", set).apply()
                onUpdate()
            }) { Icon(Icons.Default.DeleteSweep, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
