// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-visible preferences that Compose observes directly, so changing the
 * theme or language does not require restarting the whole app.
 */
object AppSettings {

    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_DYNAMIC_COLOR = "dynamic_color"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val PREFS = LocaleHelper.PREFS_NAME

    private val _themeMode = MutableStateFlow(THEME_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun setThemeMode(context: Context, mode: String) {
        val safe = mode.takeIf { it in setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK) } ?: THEME_SYSTEM
        _themeMode.value = safe
        prefs(context).edit().putString(KEY_THEME_MODE, safe).apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun resetToDefaults(context: Context) {
        setThemeMode(context, THEME_SYSTEM)
        setDynamicColor(context, false)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
