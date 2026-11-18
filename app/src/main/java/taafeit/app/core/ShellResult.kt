// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

/** Which identity actually executed a shell command. */
enum class Executor {
    /** Executed through `su` as uid 0. */
    ROOT,

    /** Executed through the Shizuku user service as uid 2000 (shell). */
    SHIZUKU,

    /** Executed as the Taafeit app uid. */
    APP,

    /** Nothing was executed. */
    NONE,
}

/**
 * Result of a shell invocation. [ok] is true only when the command finished
 * with exit code 0, so callers can stop pretending that a failed command worked.
 */
data class ShellResult(
    val ok: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String,
    val executor: Executor,
) {
    /** Human readable text: stdout when present, stderr otherwise. */
    val text: String
        get() = output.ifBlank { error }

    /** Short description for toasts and cards. */
    fun summary(): String = when {
        ok -> text.ifBlank { "OK" }
        executor == Executor.NONE -> error.ifBlank { "Not executed" }
        else -> "exit $exitCode: ${error.ifBlank { output }.ifBlank { "no output" }}"
    }

    companion object {
        fun failure(message: String, executor: Executor = Executor.NONE) =
            ShellResult(false, -1, "", message, executor)
    }
}

/** Protocol constants shared between [taafeit.app.ShizukuService] and [ShizukuBridge]. */
object ShellProtocol {
    const val STDERR_SEPARATOR = "\u0002"
    const val EXIT_SEPARATOR = "\u0003"

    fun encode(stdout: String, stderr: String, exitCode: Int): String =
        stdout + STDERR_SEPARATOR + stderr + EXIT_SEPARATOR + exitCode

    fun decode(raw: String): Triple<String, String, Int> {
        val exitIndex = raw.lastIndexOf(EXIT_SEPARATOR)
        if (exitIndex < 0) return Triple(raw, "", 0)
        val exitCode = raw.substring(exitIndex + EXIT_SEPARATOR.length).trim().toIntOrNull() ?: -1
        val body = raw.substring(0, exitIndex)
        val errorIndex = body.indexOf(STDERR_SEPARATOR)
        return if (errorIndex < 0) {
            Triple(body, "", exitCode)
        } else {
            Triple(
                body.substring(0, errorIndex),
                body.substring(errorIndex + STDERR_SEPARATOR.length),
                exitCode,
            )
        }
    }
}
