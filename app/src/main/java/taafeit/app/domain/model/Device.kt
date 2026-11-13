// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class Device(
    @PrimaryKey
    val macAddress: String,
    val ipAddress: String,
    val vendor: String?,
    val isSuspicious: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
