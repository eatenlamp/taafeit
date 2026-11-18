// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import taafeit.app.core.Executor
import taafeit.app.core.PrivilegeManager
import taafeit.app.core.PropApplyResult
import taafeit.app.domain.model.DevicePresets
import taafeit.app.presentation.ui.components.InfoRow
import taafeit.app.presentation.ui.components.PrivilegeBanner
import taafeit.app.presentation.ui.components.SectionCard

private const val CUSTOM = "__custom__"

private data class Report(val title: String, val body: String)

private val ORIGINAL_KEYS = listOf(
    "ro.product.model",
    "ro.product.manufacturer",
    "ro.product.brand",
    "ro.product.device",
    "ro.product.name",
    "ro.build.fingerprint",
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    val privileges by PrivilegeManager.state.collectAsStateWithLifecycle()

    var props by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf(DevicePresets.all.first().label) }
    var customModel by remember { mutableStateOf("") }
    var customManufacturer by remember { mutableStateOf("") }
    var customBrand by remember { mutableStateOf("") }
    var customDevice by remember { mutableStateOf("") }
    var customFingerprint by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<Report?>(null) }

    fun customProps(): Map<String, String> = linkedMapOf(
        "ro.product.model" to customModel.trim(),
        "ro.product.manufacturer" to customManufacturer.trim(),
        "ro.product.brand" to customBrand.trim(),
        "ro.product.device" to customDevice.trim(),
        "ro.product.name" to customDevice.trim(),
        "ro.build.fingerprint" to customFingerprint.trim(),
    ).filterValues { it.isNotBlank() }

    fun restoreOriginal() {
        val stored = ORIGINAL_KEYS.mapNotNull { key ->
            activity.prefs.getString("original_$key", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { key to it }
        }.toMap()

        if (stored.isEmpty()) {
            activity.showToast(activity.getString(R.string.unknown))
            return
        }
        busy = true
        scope.launch {
            val result = PrivilegeManager.applyProps(stored)
            props = withContext(Dispatchers.IO) { PrivilegeManager.getProps(force = true) }
            busy = false
            report = buildReport(activity, result)
        }
    }

    LaunchedEffect(Unit) {
        props = withContext(Dispatchers.IO) { PrivilegeManager.getProps(force = true) }
        captureOriginal(activity, props)
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PrivilegeBanner(privileges, onRequestShizuku = { activity.requestShizukuPermission() })

        SectionCard(
            title = stringResource(R.string.current_model),
            icon = Icons.Default.Person,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                InfoRow(
                    stringResource(R.string.target_model),
                    props["ro.product.model"].orEmpty().ifBlank { "—" },
                )
                InfoRow(
                    stringResource(R.string.target_manufacturer),
                    props["ro.product.manufacturer"].orEmpty().ifBlank { "—" },
                )
                InfoRow(
                    stringResource(R.string.target_brand),
                    props["ro.product.brand"].orEmpty().ifBlank { "—" },
                )
                InfoRow(
                    stringResource(R.string.target_device),
                    props["ro.product.device"].orEmpty().ifBlank { "—" },
                )
                InfoRow(
                    stringResource(R.string.target_fingerprint),
                    props["ro.build.fingerprint"].orEmpty().ifBlank { "—" },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    scope.launch {
                        loading = true
                        props = withContext(Dispatchers.IO) { PrivilegeManager.getProps(force = true) }
                        loading = false
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
                TextButton(onClick = { restoreOriginal() }, enabled = !busy && privileges.rootAvailable) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.restore_original))
                }
            }
        }

        SectionCard(title = stringResource(R.string.device_spoofing)) {
            if (!privileges.rootAvailable) {
                Text(
                    stringResource(R.string.identity_root_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(stringResource(R.string.target_model), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevicePresets.all.forEach { preset ->
                    FilterChip(
                        selected = selectedLabel == preset.label,
                        onClick = { selectedLabel = preset.label },
                        label = { Text(preset.label) },
                    )
                }
                FilterChip(
                    selected = selectedLabel == CUSTOM,
                    onClick = { selectedLabel = CUSTOM },
                    label = { Text(stringResource(R.string.custom)) },
                )
            }

            if (selectedLabel == CUSTOM) {
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    label = { Text(stringResource(R.string.model_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customManufacturer,
                    onValueChange = { customManufacturer = it },
                    label = { Text(stringResource(R.string.manufacturer_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customBrand,
                    onValueChange = { customBrand = it },
                    label = { Text(stringResource(R.string.target_brand)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customDevice,
                    onValueChange = { customDevice = it },
                    label = { Text(stringResource(R.string.target_device)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customFingerprint,
                    onValueChange = { customFingerprint = it },
                    label = { Text(stringResource(R.string.target_fingerprint)) },
                    placeholder = { Text(stringResource(R.string.fingerprint_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = {
                    val preset = DevicePresets.all.firstOrNull { it.label == selectedLabel }
                    val target = preset?.toProps() ?: customProps()
                    if (target["ro.product.model"].isNullOrBlank()) {
                        activity.showToast(activity.getString(R.string.model_name_hint))
                        return@Button
                    }
                    if (!privileges.rootAvailable) {
                        report = Report(
                            activity.getString(R.string.identity_failed),
                            activity.getString(R.string.identity_root_required),
                        )
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        val result = PrivilegeManager.applyProps(target)
                        props = withContext(Dispatchers.IO) { PrivilegeManager.getProps(force = true) }
                        busy = false
                        report = buildReport(activity, result)
                    }
                },
                enabled = !busy && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.apply_identity))
                }
            }
        }

        Text(
            stringResource(R.string.identity_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
    }

    report?.let { current ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(current.title) },
            text = { Text(current.body) },
            confirmButton = {
                TextButton(onClick = { report = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

private fun captureOriginal(activity: MainActivity, props: Map<String, String>) {
    if (props.isEmpty()) return
    val prefs = activity.prefs
    if (prefs.getBoolean("original_captured", false)) return
    val editor = prefs.edit()
    ORIGINAL_KEYS.forEach { key -> editor.putString("original_$key", props[key].orEmpty()) }
    editor.putBoolean("original_captured", true).apply()
}

private fun buildReport(activity: MainActivity, result: PropApplyResult): Report {
    val mismatches = result.mismatches.entries.joinToString("\n") { (key, values) ->
        "$key\n  → ${values.second}"
    }
    return when {
        result.command.executor == Executor.NONE ->
            Report(activity.getString(R.string.identity_failed), result.command.error)
        !result.command.ok ->
            Report(activity.getString(R.string.identity_failed), result.command.summary())
        result.mismatches.isEmpty() ->
            Report(
                activity.getString(R.string.identity_applied),
                result.command.output.ifBlank { "OK" },
            )
        else ->
            Report(activity.getString(R.string.identity_partial), mismatches)
    }
}
