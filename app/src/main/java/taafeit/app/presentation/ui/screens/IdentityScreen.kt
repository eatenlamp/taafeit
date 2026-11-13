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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import taafeit.app.MainActivity
import androidx.compose.ui.res.stringResource
import taafeit.app.R

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var currentModel by remember { mutableStateOf("Loading...") }
    var currentMan by remember { mutableStateOf("Loading...") }
    var isRooted by remember { mutableStateOf(true) }
    
    val models = listOf("Pixel 9 Pro", "Pixel 9 Pro XL", "Pixel 8 Pro", "Xiaomi 14 Ultra", "Galaxy S24 Ultra", "OnePlus 12", "CUSTOM")
    val manufacturers = listOf("Google", "Xiaomi", "Samsung", "OnePlus", "Sony", "CUSTOM")
    
    var selectedModel by remember { mutableStateOf(models[0]) }
    var selectedMan by remember { mutableStateOf(manufacturers[0]) }
    var customModel by remember { mutableStateOf("") }
    var customMan by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            currentModel = activity.getProp("ro.product.model").ifEmpty { "Default" }
            currentMan = activity.getProp("ro.product.manufacturer").ifEmpty { "System" }
            isRooted = activity.isRooted()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.current_model), style = MaterialTheme.typography.labelMedium)
                    Text(currentModel, style = MaterialTheme.typography.headlineSmall)
                    Text("By $currentMan", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.device_spoofing), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                Text("Target Model", style = MaterialTheme.typography.labelLarge)
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { m ->
                        FilterChip(selected = selectedModel == m, onClick = { selectedModel = m }, label = { Text(m) })
                    }
                }
                if (selectedModel == "CUSTOM") {
                    OutlinedTextField(value = customModel, onValueChange = { customModel = it }, label = { Text("Model Name") }, modifier = Modifier.fillMaxWidth())
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Target Manufacturer", style = MaterialTheme.typography.labelLarge)
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    manufacturers.forEach { m ->
                        FilterChip(selected = selectedMan == m, onClick = { selectedMan = m }, label = { Text(m) })
                    }
                }
                if (selectedMan == "CUSTOM") {
                    OutlinedTextField(value = customMan, onValueChange = { customMan = it }, label = { Text("Manufacturer Name") }, modifier = Modifier.fillMaxWidth())
                }

                Button(
                    onClick = {
                        if (!isRooted) {
                            activity.showToast(activity.getString(R.string.no_root))
                            return@Button
                        }
                        val finalModel = if (selectedModel == "CUSTOM") customModel else selectedModel
                        val finalMan = if (selectedMan == "CUSTOM") customMan else selectedMan
                        scope.launch(Dispatchers.IO) {
                            activity.runRootCommands(listOf(
                                "resetprop ro.product.model \"$finalModel\"",
                                "resetprop ro.product.manufacturer \"$finalMan\""
                            ))
                            refresh()
                            launch(Dispatchers.Main) { activity.showToast("Security profile engaged") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}
