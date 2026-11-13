// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app

import android.os.Process
import timber.log.Timber

class ShizukuService : IShizukuService.Stub() {
    override fun runCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }
}
