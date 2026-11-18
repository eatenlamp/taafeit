// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app

import taafeit.app.core.ShellProtocol
import java.util.concurrent.TimeUnit

/**
 * Runs inside the Shizuku user service process (uid 2000, shell).
 *
 * The AIDL contract only carries a single string, so stdout, stderr and the
 * exit code are packed with [ShellProtocol]. That keeps the checked-in AIDL
 * stub untouched while still letting callers see real failures.
 */
class ShizukuService : IShizukuService.Stub() {

    override fun runCommand(cmd: String): String {
        var process: Process? = null
        return try {
            val started = ProcessBuilder("/system/bin/sh", "-c", cmd).start()
            process = started

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outReader = drain(started.inputStream, stdout)
            val errReader = drain(started.errorStream, stderr)

            val finished = started.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                started.destroyForcibly()
                outReader.join(500)
                errReader.join(500)
                return ShellProtocol.encode(
                    stdout.toString().trim(),
                    "Command timed out after ${COMMAND_TIMEOUT_SECONDS}s",
                    -1,
                )
            }
            outReader.join(1_000)
            errReader.join(1_000)
            ShellProtocol.encode(
                stdout.toString().trim(),
                stderr.toString().trim(),
                started.exitValue(),
            )
        } catch (e: Exception) {
            ShellProtocol.encode("", e.message ?: e.javaClass.simpleName, -1)
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    override fun destroy() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun drain(stream: java.io.InputStream, sink: StringBuilder): Thread =
        Thread {
            try {
                stream.bufferedReader().use { sink.append(it.readText()) }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 60L
    }
}
