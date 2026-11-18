// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class PrivilegeLevel {
    /** uid 0 through su. */
    ROOT,

    /** uid 2000 through the Shizuku user service. */
    SHIZUKU,

    /** App uid only: read-only features. */
    APP,
}

data class PrivilegeState(
    val level: PrivilegeLevel = PrivilegeLevel.APP,
    val rootAvailable: Boolean = false,
    val rootManager: String? = null,
    val resetpropPath: String? = null,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuBound: Boolean = false,
    val checked: Boolean = false,
    val lastCheckedAt: Long = 0L,
) {
    val canSystemProperties: Boolean get() = rootAvailable
    val canSecureSettings: Boolean get() = rootAvailable || shizukuBound
    val canNetfilter: Boolean get() = rootAvailable
    val canReadNeighbors: Boolean get() = rootAvailable || shizukuBound
}

data class PropApplyResult(
    val command: ShellResult,
    val mismatches: Map<String, Pair<String, String>>,
) {
    val applied: Boolean get() = command.ok && mismatches.isEmpty()
}

/**
 * Single source of truth for "what can this device actually do right now".
 *
 * Every privileged operation goes through here, so the UI can stop claiming
 * success when there is no root and no Shizuku.
 */
object PrivilegeManager {

    private const val ROOT_CACHE_MS = 60_000L
    private const val PROBE_TIMEOUT_MS = 12_000L
    private const val PROP_CACHE_MS = 5_000L
    private const val DEFAULT_TIMEOUT_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PrivilegeState())
    val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    private data class RootInfo(
        val available: Boolean,
        val manager: String?,
        val resetpropPath: String?,
    )

    private data class CachedRoot(val info: RootInfo, val at: Long)
    private data class CachedProps(val props: Map<String, String>, val at: Long)

    @Volatile
    private var cachedRoot: CachedRoot? = null

    @Volatile
    private var cachedProps: CachedProps? = null

    /** Serializes detection so two launches cannot show two `su` prompts. */
    private val refreshMutex = Mutex()

    fun start(context: Context) {
        ShizukuBridge.init(context)
        refreshAsync(force = true)
    }

    fun refreshAsync(force: Boolean = false) {
        scope.launch { refresh(force = force) }
    }

    suspend fun refresh(force: Boolean = false): PrivilegeState = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val root = cachedRoot
                ?.takeIf { !force && now - it.at < ROOT_CACHE_MS }
                ?.info
                ?: detectRoot().also { cachedRoot = CachedRoot(it, now) }

            val shizukuRunning = ShizukuBridge.isRunning
            val shizukuGranted = ShizukuBridge.isGranted
            if (shizukuRunning && shizukuGranted) ShizukuBridge.ensureBound()

            val next = PrivilegeState(
                level = when {
                    root.available -> PrivilegeLevel.ROOT
                    ShizukuBridge.isBound -> PrivilegeLevel.SHIZUKU
                    else -> PrivilegeLevel.APP
                },
                rootAvailable = root.available,
                rootManager = root.manager,
                resetpropPath = root.resetpropPath,
                shizukuRunning = shizukuRunning,
                shizukuGranted = shizukuGranted,
                shizukuBound = ShizukuBridge.isBound,
                checked = true,
                lastCheckedAt = System.currentTimeMillis(),
            )
            _state.value = next
            if (force) cachedProps = null
            next
        }
    }

    fun onShizukuBound() {
        _state.update {
            it.copy(
                shizukuBound = true,
                level = if (it.rootAvailable) PrivilegeLevel.ROOT else PrivilegeLevel.SHIZUKU,
            )
        }
    }

    // ---------------------------------------------------------------- commands

    /** Balanced execution: root, then Shizuku, then the app uid. */
    suspend fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult {
        val current = state.value
        return when {
            current.rootAvailable -> execRoot(command, timeoutMs)
            ShizukuBridge.isBound -> ShizukuBridge.exec(command)
            else -> execApp(command, timeoutMs)
        }
    }

    /** For operations that must never silently degrade to the app uid. */
    suspend fun execPrivileged(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult {
        val current = state.value
        return when {
            current.rootAvailable -> execRoot(command, timeoutMs)
            ShizukuBridge.isBound -> ShizukuBridge.exec(command)
            else -> ShellResult.failure(
                "Root or Shizuku is required for this action",
                Executor.NONE,
            )
        }
    }

    suspend fun execRoot(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult = withContext(Dispatchers.IO) {
        if (!state.value.rootAvailable) {
            return@withContext ShellResult.failure("Root is not available", Executor.NONE)
        }
        ShellExecutor.run(listOf("su", "-c", command), timeoutMs, Executor.ROOT)
    }

    suspend fun execShizuku(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult = ShizukuBridge.exec(command)

    suspend fun execApp(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult = withContext(Dispatchers.IO) {
        ShellExecutor.run(listOf("/system/bin/sh", "-c", command), timeoutMs, Executor.APP)
    }

    // ------------------------------------------------------------------ props

    /** Reads all system properties once and caches them briefly. */
    suspend fun getProps(force: Boolean = false): Map<String, String> {
        val now = SystemClock.elapsedRealtime()
        cachedProps?.let { cached ->
            if (!force && now - cached.at < PROP_CACHE_MS) return cached.props
        }

        val appRead = execApp("getprop", 8_000)
        var props = parseGetprop(appRead.output)
        if (props.isEmpty()) {
            props = parseGetprop(exec("getprop", 8_000).output)
        }
        if (props.isNotEmpty()) cachedProps = CachedProps(props, now)
        return props
    }

    suspend fun getProp(name: String): String = getProps()[name].orEmpty()

    /**
     * Applies properties with resetprop and verifies the result, because
     * resetprop can fail silently on some ROMs and properties.
     */
    suspend fun applyProps(props: Map<String, String>): PropApplyResult {
        val current = state.value
        if (!current.rootAvailable) {
            return PropApplyResult(
                command = ShellResult.failure(
                    "Changing system properties requires root",
                    Executor.NONE,
                ),
                mismatches = emptyMap(),
            )
        }
        if (props.isEmpty()) {
            return PropApplyResult(ShellResult(true, 0, "", "", Executor.ROOT), emptyMap())
        }

        val fallback = current.resetpropPath
        val candidates = listOfNotNull(
            fallback,
            "/data/adb/magisk/resetprop",
            "/data/adb/ksu/bin/resetprop",
            "/data/adb/ap/bin/resetprop",
            "/system/bin/resetprop",
        ).distinct()

        val script = buildString {
            append("RESETPROP=\$(command -v resetprop 2>/dev/null)\n")
            append("if [ -z \"\$RESETPROP\" ]; then\n")
            append("  for p in ")
            append(candidates.joinToString(" ") { quote(it) })
            append("; do [ -x \"\$p\" ] && RESETPROP=\"\$p\" && break; done\n")
            append("fi\n")
            append("[ -n \"\$RESETPROP\" ] || { echo 'resetprop not found' >&2; exit 127; }\n")
            props.forEach { (key, value) ->
                append("\$RESETPROP ")
                append(quote(key))
                append(" ")
                append(quote(value))
                append("\n")
            }
        }

        val command = execRoot(script)
        val actual = getProps(force = true)
        val mismatches = props.mapNotNull { (key, expected) ->
            val seen = actual[key]
            if (seen == expected) null else key to (expected to (seen ?: "<unset>"))
        }.toMap()

        return PropApplyResult(command, mismatches)
    }

    // ------------------------------------------------------------------ utils

    /** POSIX single-quote escaping: prevents command injection via user input. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private suspend fun detectRoot(): RootInfo = withContext(Dispatchers.IO) {
        val probe = ShellExecutor.run(
            listOf("su", "-c", ROOT_PROBE),
            PROBE_TIMEOUT_MS,
            Executor.ROOT,
        )
        val lines = probe.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val isRoot = lines.firstOrNull()?.toIntOrNull() == 0
        if (!isRoot) return@withContext RootInfo(false, null, null)

        val resetprop = lines.firstOrNull { it.contains("resetprop") }
        val manager = when {
            lines.any { it == "KSU" } -> "KernelSU"
            lines.any { it == "APATCH" } -> "APatch"
            lines.any { it.contains("magisk") } -> "Magisk"
            else -> "su"
        }
        RootInfo(true, manager, resetprop)
    }

    private fun parseGetprop(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("[")) return@forEach
            val separator = trimmed.indexOf("]: [")
            if (separator <= 0) return@forEach
            val key = trimmed.substring(1, separator)
            val value = trimmed.substring(separator + 4).removeSuffix("]")
            result[key] = value
        }
        return result
    }

    private val ROOT_PROBE = """
        id -u
        command -v resetprop || true
        command -v magisk || true
        [ -d /data/adb/ksu ] && echo KSU || true
        [ -d /data/adb/ap ] && echo APATCH || true
        true
    """.trimIndent()

    fun logState(state: PrivilegeState = _state.value) {
        Timber.i(
            "Privileges: level=%s root=%s(%s) shizuku=%s bound=%s",
            state.level,
            state.rootAvailable,
            state.rootManager,
            state.shizukuGranted,
            state.shizukuBound,
        )
    }
}
