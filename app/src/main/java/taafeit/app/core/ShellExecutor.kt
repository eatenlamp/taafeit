// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.core

import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Runs a local process without ever blocking the caller on a full stderr buffer.
 *
 * Both streams are drained on their own daemon thread, so a chatty command can
 * never deadlock the way the old `readLine()` + `waitFor()` code did.
 */
object ShellExecutor {

    fun run(
        command: List<String>,
        timeoutMs: Long,
        executor: Executor,
    ): ShellResult {
        var process: Process? = null
        return try {
            val started = ProcessBuilder(command).start()
            process = started

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outReader = drainThread(started.inputStream, stdout)
            val errReader = drainThread(started.errorStream, stderr)

            val finished = started.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                started.destroyForcibly()
                outReader.join(500)
                errReader.join(500)
                ShellResult(
                    ok = false,
                    exitCode = -1,
                    output = stdout.toString().trim(),
                    error = "Timed out after ${timeoutMs}ms",
                    executor = executor,
                )
            } else {
                outReader.join(1_000)
                errReader.join(1_000)
                val code = started.exitValue()
                ShellResult(
                    ok = code == 0,
                    exitCode = code,
                    output = stdout.toString().trim(),
                    error = stderr.toString().trim(),
                    executor = executor,
                )
            }
        } catch (e: Exception) {
            ShellResult(
                ok = false,
                exitCode = -1,
                output = "",
                error = e.message ?: e.javaClass.simpleName,
                executor = executor,
            )
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    private fun drainThread(stream: InputStream, sink: StringBuilder): Thread =
        Thread {
            try {
                stream.bufferedReader().use { reader -> sink.append(reader.readText()) }
            } catch (_: Exception) {
                // Stream closed because the process was killed; nothing to add.
            }
        }.apply {
            isDaemon = true
            start()
        }
}
