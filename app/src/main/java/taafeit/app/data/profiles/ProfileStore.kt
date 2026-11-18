// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.data.profiles

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class IdentityProfile(
    val name: String,
    val model: String,
    val manufacturer: String,
    val brand: String = "",
    val device: String = "",
    val product: String = "",
    val fingerprint: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** System properties that describe this identity. */
    fun toProps(): Map<String, String> = buildMap {
        put("ro.product.model", model)
        put("ro.product.manufacturer", manufacturer)
        if (brand.isNotBlank()) put("ro.product.brand", brand)
        if (device.isNotBlank()) put("ro.product.device", device)
        if (product.isNotBlank()) put("ro.product.name", product)
        if (fingerprint.isNotBlank()) put("ro.build.fingerprint", fingerprint)
    }

    fun summary(): String = listOf(model, manufacturer, brand)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
}

/**
 * Persists identity profiles as JSON instead of a `StringSet` plus loose
 * `profile_<name>_model` keys, which used to leak data after deletion.
 */
object ProfileStore {

    private const val PREFS = "taafeit_prefs"
    private const val KEY_JSON = "profiles_json"
    private const val KEY_LEGACY_NAMES = "profiles"
    private const val LEGACY_MODEL = "profile_%s_model"
    private const val LEGACY_MANUFACTURER = "profile_%s_manufacturer"
    private const val FORMAT_VERSION = 1

    fun load(context: Context): List<IdentityProfile> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null)
        if (raw != null) {
            return runCatching { parse(raw) }.getOrElse { emptyList() }
        }
        return migrateLegacy(context)
    }

    fun save(context: Context, profiles: List<IdentityProfile>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, export(profiles))
            .apply()
    }

    fun upsert(context: Context, profile: IdentityProfile): List<IdentityProfile> {
        val current = load(context).filterNot { it.name.equals(profile.name, ignoreCase = true) }
        val next = current + profile
        save(context, next)
        return next
    }

    fun remove(context: Context, name: String): List<IdentityProfile> {
        val next = load(context).filterNot { it.name.equals(name, ignoreCase = true) }
        save(context, next)
        clearLegacyEntry(context, name)
        return next
    }

    fun capture(name: String, props: Map<String, String>): IdentityProfile = IdentityProfile(
        name = name.trim(),
        model = props["ro.product.model"].orEmpty().ifBlank { "unknown" },
        manufacturer = props["ro.product.manufacturer"].orEmpty().ifBlank { "unknown" },
        brand = props["ro.product.brand"].orEmpty(),
        device = props["ro.product.device"].orEmpty(),
        product = props["ro.product.name"].orEmpty(),
        fingerprint = props["ro.build.fingerprint"].orEmpty(),
    )

    fun export(profiles: List<IdentityProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("name", profile.name)
                    put("model", profile.model)
                    put("manufacturer", profile.manufacturer)
                    put("brand", profile.brand)
                    put("device", profile.device)
                    put("product", profile.product)
                    put("fingerprint", profile.fingerprint)
                    put("createdAt", profile.createdAt)
                }
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("profiles", array)
            .toString(2)
    }

    /** Accepts both `{"profiles":[...]}` and a bare JSON array. */
    fun import(raw: String): List<IdentityProfile> = parse(raw)

    private fun parse(raw: String): List<IdentityProfile> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Empty file")
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("profiles")
                ?: throw IllegalArgumentException("Missing \"profiles\" array")
        }

        val result = ArrayList<IdentityProfile>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val model = item.optString("model").trim()
            if (name.isEmpty() || model.isEmpty()) continue
            result += IdentityProfile(
                name = name,
                model = model,
                manufacturer = item.optString("manufacturer").trim().ifBlank { "unknown" },
                brand = item.optString("brand").trim(),
                device = item.optString("device").trim(),
                product = item.optString("product").trim(),
                fingerprint = item.optString("fingerprint").trim(),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        if (result.isEmpty()) throw IllegalArgumentException("No valid profiles found")
        return result
    }

    private fun migrateLegacy(context: Context): List<IdentityProfile> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = prefs.getStringSet(KEY_LEGACY_NAMES, emptySet()).orEmpty()
        if (names.isEmpty()) return emptyList()

        val profiles = names.mapNotNull { name ->
            val model = prefs.getString(LEGACY_MODEL.format(name), null) ?: return@mapNotNull null
            IdentityProfile(
                name = name,
                model = model,
                manufacturer = prefs.getString(LEGACY_MANUFACTURER.format(name), null)
                    ?: "unknown",
            )
        }
        if (profiles.isNotEmpty()) {
            save(context, profiles)
            names.forEach { clearLegacyEntry(context, it) }
            prefs.edit().remove(KEY_LEGACY_NAMES).apply()
        }
        return profiles
    }

    private fun clearLegacyEntry(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_MODEL.format(name))
            .remove(LEGACY_MANUFACTURER.format(name))
            .apply()
    }
}
