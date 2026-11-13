// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import taafeit.app.MainActivity
import androidx.compose.ui.res.stringResource
import taafeit.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(activity: MainActivity) {
    var language by remember { mutableStateOf(activity.prefs.getString("language", "en") ?: "en") }
    val languages = listOf(
        "en" to stringResource(R.string.english),
        "ru" to stringResource(R.string.russian),
        "es" to stringResource(R.string.spanish),
        "de" to stringResource(R.string.german),
        "fr" to stringResource(R.string.french),
        "zh" to stringResource(R.string.chinese)
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { (code, name) ->
                        FilterChip(
                            selected = language == code,
                            onClick = {
                                language = code
                                activity.prefs.edit().putString("language", code).apply()
                                activity.recreate()
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text("Taafeit v3.0 • Blue Edition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
