// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app

import android.app.Application
import taafeit.app.core.AppSettings
import taafeit.app.core.Notifications
import taafeit.app.core.PrivilegeManager
import taafeit.app.data.network.ShieldManager
import timber.log.Timber

class TaafeitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        AppSettings.init(this)
        Notifications.ensureChannels(this)
        ShieldManager.init(this)
        PrivilegeManager.start(this)
    }
}
