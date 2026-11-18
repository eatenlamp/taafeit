// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import taafeit.app.data.profiles.IdentityProfile
import taafeit.app.data.profiles.ProfileStore
import taafeit.app.presentation.ui.components.PrivilegeBanner

@Composable
fun VaultScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    val privileges by PrivilegeManager.state.collectAsStateWithLifecycle()

    var profiles by remember { mutableStateOf<List<IdentityProfile>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profiles = withContext(Dispatchers.IO) { ProfileStore.load(activity) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = ProfileStore.export(profiles)
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    activity.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open the target file")
                }.isSuccess
            }
            activity.showToast(
                if (success) {
                    activity.getString(R.string.export_ok, profiles.size)
                } else {
                    activity.getString(R.string.export_failed)
                }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = activity.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Unable to read the selected file")
                    ProfileStore.import(text)
                }
            }
            result.onSuccess { imported ->
                val added = imported.filter { candidate ->
                    profiles.none { it.name.equals(candidate.name, ignoreCase = true) }
                }
                val merged = (profiles + added).sortedBy { it.name.lowercase() }
                withContext(Dispatchers.IO) { ProfileStore.save(activity, merged) }
                profiles = merged
                activity.showToast(activity.getString(R.string.import_ok, added.size))
            }.onFailure {
                activity.showToast(
                    activity.getString(R.string.import_failed, it.message ?: "?")
                )
            }
        }
    }

    fun deploy(profile: IdentityProfile) {
        if (!privileges.rootAvailable) {
            activity.showToast(activity.getString(R.string.identity_root_required))
            return
        }
        busy = true
        scope.launch {
            val result = PrivilegeManager.applyProps(profile.toProps())
            busy = false
            if (result.applied) {
                activity.showToast(activity.getString(R.string.deployed, profile.name))
            } else {
                val reason = result.mismatches.entries.joinToString("; ") { (key, values) ->
                    "$key → ${values.second}"
                }.ifBlank { result.command.summary() }
                activity.showToast(activity.getString(R.string.deploy_failed, reason))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.vault_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { showAddDialog = true }, enabled = !busy) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = stringResource(R.string.add_profile),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            stringResource(R.string.vault_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))

        PrivilegeBanner(privileges, onRequestShizuku = { activity.requestShizukuPermission() })
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { exportLauncher.launch("taafeit-profiles.json") },
                enabled = profiles.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.export_profiles)) }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.import_profiles)) }
        }

        Spacer(Modifier.height(12.dp))

        if (profiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        stringResource(R.string.vault_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.name }) { profile ->
                    ProfileCard(
                        profile = profile,
                        busy = busy,
                        onDeploy = { deploy(profile) },
                        onDelete = {
                            scope.launch {
                                profiles = withContext(Dispatchers.IO) {
                                    ProfileStore.remove(activity, profile.name)
                                }
                            }
                        },
                        onCopy = {
                            activity.copyToClipboard(
                                profile.name,
                                ProfileStore.export(listOf(profile)),
                            )
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.capture_current)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.vault_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(stringResource(R.string.profile_alias)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = newProfileName.trim()
                    when {
                        name.isEmpty() ->
                            activity.showToast(activity.getString(R.string.profile_name_empty))
                        profiles.any { it.name.equals(name, ignoreCase = true) } ->
                            activity.showToast(activity.getString(R.string.profile_exists))
                        else -> {
                            busy = true
                            scope.launch {
                                val props = withContext(Dispatchers.IO) {
                                    PrivilegeManager.getProps(force = true)
                                }
                                val profile = ProfileStore.capture(name, props)
                                profiles = withContext(Dispatchers.IO) {
                                    ProfileStore.upsert(activity, profile)
                                }.sortedBy { it.name.lowercase() }
                                busy = false
                                newProfileName = ""
                                showAddDialog = false
                                activity.showToast(activity.getString(R.string.profile_saved))
                            }
                        }
                    }
                }) { Text(stringResource(R.string.vault_it)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: IdentityProfile,
    busy: Boolean,
    onDeploy: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy_json),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = onDeploy, enabled = !busy) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = stringResource(R.string.deploy),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
