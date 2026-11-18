// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language handling.
 *
 * Android 13+ uses the platform [LocaleManager]; older releases rebuild the
 * Activity context through [wrap]. This replaces the deprecated
 * `resources.updateConfiguration` call that silently stopped working on 13+.
 */
object LocaleHelper {

    const val PREFS_NAME = "taafeit_prefs"
    const val KEY_LANGUAGE = "language"

    /** Follow the system locale. */
    const val SYSTEM = "system"
    const val DEFAULT_LANGUAGE = SYSTEM

    val supported: List<String> = listOf("en", "ru")

    fun current(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
        return when {
            stored == SYSTEM -> SYSTEM
            stored != null && stored in supported -> stored
            else -> DEFAULT_LANGUAGE
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = current(base)
        if (language == SYSTEM) return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    fun apply(activity: Activity, language: String) {
        val safe = when {
            language == SYSTEM -> SYSTEM
            language in supported -> language
            else -> DEFAULT_LANGUAGE
        }
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, safe)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = activity.getSystemService(LocaleManager::class.java)
            manager.applicationLocales = if (safe == SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(safe)
            }
        } else {
            activity.recreate()
        }
    }
}
