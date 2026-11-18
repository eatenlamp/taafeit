// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import taafeit.app.R
import taafeit.app.core.PrivilegeState
import androidx.compose.ui.res.stringResource

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        }
    }
}

/**
 * Always tells the truth about the current privilege level — this card is the
 * reason the app no longer pretends to work without root or Shizuku.
 */
@Composable
fun PrivilegeBanner(
    state: PrivilegeState,
    onRequestShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        state.rootAvailable -> MaterialTheme.colorScheme.primaryContainer
        state.shizukuBound -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val icon = if (state.rootAvailable || state.shizukuBound) Icons.Default.Shield else Icons.Default.Warning

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.rootAvailable -> state.rootManager
                            ?.let { "${stringResource(R.string.privilege_root)} · $it" }
                            ?: stringResource(R.string.privilege_root)
                        state.shizukuBound -> stringResource(R.string.privilege_shizuku)
                        state.shizukuGranted -> stringResource(R.string.privilege_shizuku)
                        else -> stringResource(R.string.privilege_limited)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        state.shizukuRunning && !state.shizukuGranted && !state.rootAvailable ->
                            stringResource(R.string.shizuku_not_authorized)
                        else -> stringResource(R.string.privilege_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!state.rootAvailable && state.shizukuRunning && !state.shizukuGranted) {
                TextButton(onClick = onRequestShizuku) {
                    Text(stringResource(R.string.authorize))
                }
            }
        }
    }
}

@Composable
fun CapabilityRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (granted) "✓" else "✕",
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
