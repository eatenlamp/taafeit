// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.domain.model

/**
 * Ready-made identities. Fingerprints are syntactically valid but intentionally
 * not real device builds: the goal is to look like a plausible device, not to
 * forge a specific user's device.
 */
data class DevicePreset(
    val label: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val fingerprint: String,
) {
    val product: String get() = device

    fun toProps(): Map<String, String> = linkedMapOf(
        "ro.product.model" to model,
        "ro.product.manufacturer" to manufacturer,
        "ro.product.brand" to brand,
        "ro.product.device" to device,
        "ro.product.name" to product,
        "ro.build.fingerprint" to fingerprint,
    )
}

object DevicePresets {

    val all: List<DevicePreset> = listOf(
        DevicePreset(
            label = "Pixel 9 Pro",
            model = "Pixel 9 Pro",
            manufacturer = "Google",
            brand = "google",
            device = "caiman",
            fingerprint = "google/caiman/caiman:15/AP4A.250105.002/12345678:user/release-keys",
        ),
        DevicePreset(
            label = "Pixel 8 Pro",
            model = "Pixel 8 Pro",
            manufacturer = "Google",
            brand = "google",
            device = "husky",
            fingerprint = "google/husky/husky:15/AP4A.250105.002/12345678:user/release-keys",
        ),
        DevicePreset(
            label = "Galaxy S24 Ultra",
            model = "SM-S928B",
            manufacturer = "samsung",
            brand = "samsung",
            device = "e3q",
            fingerprint = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AWLM:user/release-keys",
        ),
        DevicePreset(
            label = "Xiaomi 14 Ultra",
            model = "2405CPX3DG",
            manufacturer = "Xiaomi",
            brand = "Xiaomi",
            device = "aurora",
            fingerprint = "Xiaomi/aurora/aurora:14/UKQ1.231003.002/OS1.0.4.0.UNACNXM:user/release-keys",
        ),
        DevicePreset(
            label = "OnePlus 12",
            model = "CPH2581",
            manufacturer = "OnePlus",
            brand = "OnePlus",
            device = "waffle",
            fingerprint = "OnePlus/waffle/waffle:14/UKQ1.230924.001/R.1c3a2d-1:user/release-keys",
        ),
        DevicePreset(
            label = "Xperia 1 VI",
            model = "XQ-EC54",
            manufacturer = "Sony",
            brand = "Sony",
            device = "pdx246",
            fingerprint = "Sony/pdx246/pdx246:14/69.1.A.2.78/12345678:user/release-keys",
        ),
    )
}
