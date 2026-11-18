// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import taafeit.app.BuildConfig
import taafeit.app.MainActivity
import taafeit.app.R
import taafeit.app.core.AppSettings
import taafeit.app.core.LocaleHelper
import taafeit.app.presentation.ui.components.InfoRow
import taafeit.app.presentation.ui.components.SectionCard

private const val SOURCE_URL = "https://github.com/eatenlamp/taafeit"
private const val ISSUES_URL = "https://github.com/eatenlamp/taafeit/issues"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(activity: MainActivity) {
    val themeMode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by AppSettings.dynamicColor.collectAsStateWithLifecycle()
    val language = LocaleHelper.current(activity)

    fun open(url: String) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.language), icon = Icons.Default.Language) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LocaleHelper.SYSTEM to R.string.language_system,
                    "en" to R.string.english,
                    "ru" to R.string.russian,
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = language == code,
                        onClick = { LocaleHelper.apply(activity, code) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.theme), icon = Icons.Default.Palette) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppSettings.THEME_SYSTEM to R.string.theme_system,
                    AppSettings.THEME_LIGHT to R.string.theme_light,
                    AppSettings.THEME_DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { AppSettings.setThemeMode(activity, mode) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dynamic_color),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.dynamic_color_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = { AppSettings.setDynamicColor(activity, it) },
                )
            }

            OutlinedButton(
                onClick = {
                    AppSettings.resetToDefaults(activity)
                    activity.showToast(activity.getString(R.string.settings_reset_done))
                    LocaleHelper.apply(activity, LocaleHelper.SYSTEM)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reset_settings)) }
        }

        SectionCard(title = stringResource(R.string.about), icon = Icons.Default.Info) {
            InfoRow(
                stringResource(R.string.version),
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            InfoRow(stringResource(R.string.license), "AGPL-3.0-or-later")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { open(SOURCE_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.source_code)) }
                OutlinedButton(
                    onClick = { open(ISSUES_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.report_issue)) }
            }
        }

        Text(
            "Taafeit ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
    }
}
