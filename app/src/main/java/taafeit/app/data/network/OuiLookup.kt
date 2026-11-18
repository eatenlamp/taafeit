// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.data.network

/**
 * Offline OUI lookup for the most common consumer and network vendors.
 *
 * Deliberately local: the scanner must work on a LAN with no internet access,
 * and querying an external API would leak the neighbour list.
 */
object OuiLookup {

    private val vendors: Map<String, String> = mapOf(
        // Apple
        "001451" to "Apple", "0017F2" to "Apple", "001B63" to "Apple",
        "0021E9" to "Apple", "002312" to "Apple", "002500" to "Apple",
        "0026B0" to "Apple", "003065" to "Apple", "0050E4" to "Apple",
        "040CCE" to "Apple", "086D41" to "Apple", "0C1539" to "Apple",
        "1093E9" to "Apple", "18AF61" to "Apple", "1C1AC0" to "Apple",
        "28CFE9" to "Apple", "3CAB8E" to "Apple", "40A6D9" to "Apple",
        "44D884" to "Apple", "4C57CA" to "Apple", "5855CA" to "Apple",
        "60FACD" to "Apple", "64B9E8" to "Apple", "6C709F" to "Apple",
        "70DEE2" to "Apple", "78CA39" to "Apple", "7CD1C3" to "Apple",
        "8866A5" to "Apple", "8C8590" to "Apple", "9027E4" to "Apple",
        "94E96A" to "Apple", "9803D8" to "Apple", "A4B197" to "Apple",
        "A8667F" to "Apple", "AC293A" to "Apple", "B065BD" to "Apple",
        "B8E856" to "Apple", "BC52B7" to "Apple", "C06394" to "Apple",
        "C42C03" to "Apple", "C82A14" to "Apple", "D02598" to "Apple",
        "D49A20" to "Apple", "D83062" to "Apple", "DC2B2A" to "Apple",
        "E0B9BA" to "Apple", "E425E7" to "Apple", "E8802E" to "Apple",
        "F0B479" to "Apple", "F45C89" to "Apple", "F81EDF" to "Apple",

        // Samsung
        "0000F0" to "Samsung", "0012FB" to "Samsung", "0015B9" to "Samsung",
        "001DF6" to "Samsung", "002454" to "Samsung", "002637" to "Samsung",
        "04FE31" to "Samsung", "08D42B" to "Samsung", "0C715D" to "Samsung",
        "103047" to "Samsung", "14568E" to "Samsung", "1C5A3E" to "Samsung",
        "20D390" to "Samsung", "24DBAC" to "Samsung", "28CC01" to "Samsung",
        "34AA8B" to "Samsung", "38AA3C" to "Samsung", "3C5A37" to "Samsung",
        "40D3AE" to "Samsung", "44F459" to "Samsung", "4C3C16" to "Samsung",
        "5001BB" to "Samsung", "5492BE" to "Samsung", "5C0A5B" to "Samsung",
        "60A10A" to "Samsung", "68EBAE" to "Samsung", "6C2F2C" to "Samsung",
        "70F927" to "Samsung", "78ABBB" to "Samsung", "7C0BC6" to "Samsung",
        "8018A7" to "Samsung", "8425DB" to "Samsung", "8C71F8" to "Samsung",
        "9401C2" to "Samsung", "A00798" to "Samsung", "A8F274" to "Samsung",
        "B047BF" to "Samsung", "B8D9CE" to "Samsung", "BC1485" to "Samsung",
        "C01173" to "Samsung", "C4731E" to "Samsung", "CC07AB" to "Samsung",
        "D0176A" to "Samsung", "D487D8" to "Samsung", "D831CF" to "Samsung",
        "E4E0C5" to "Samsung", "E8508B" to "Samsung", "EC1F72" to "Samsung",
        "F49F54" to "Samsung", "F8042E" to "Samsung", "FCC734" to "Samsung",

        // Xiaomi / Redmi / POCO
        "00EC0A" to "Xiaomi", "04CF8C" to "Xiaomi", "0C1DAF" to "Xiaomi",
        "102AB3" to "Xiaomi", "185936" to "Xiaomi", "20A783" to "Xiaomi",
        "28E31F" to "Xiaomi", "34CE00" to "Xiaomi", "3C47BB" to "Xiaomi",
        "44680A" to "Xiaomi", "4C49E3" to "Xiaomi", "50EC50" to "Xiaomi",
        "584498" to "Xiaomi", "64B473" to "Xiaomi", "68DFDD" to "Xiaomi",
        "742344" to "Xiaomi", "7802F8" to "Xiaomi", "7C1DD9" to "Xiaomi",
        "8CBEBE" to "Xiaomi", "98FAE3" to "Xiaomi", "A086C6" to "Xiaomi",
        "AC C1 EE" to "Xiaomi", "B0E235" to "Xiaomi", "C46AB7" to "Xiaomi",
        "D463C6" to "Xiaomi", "E4AA5D" to "Xiaomi", "F0B429" to "Xiaomi",
        "F8A45F" to "Xiaomi",

        // Huawei / Honor
        "00259E" to "Huawei", "002EC7" to "Huawei", "04BD70" to "Huawei",
        "080B3E" to "Huawei", "0C37DC" to "Huawei", "10476B" to "Huawei",
        "18C58A" to "Huawei", "201BC0" to "Huawei", "24DBAC" to "Huawei",
        "283152" to "Huawei", "30894A" to "Huawei", "3CDFBD" to "Huawei",
        "40CBA8" to "Huawei", "4843 5B" to "Huawei", "4C5499" to "Huawei",
        "5C4CA9" to "Huawei", "6416F0" to "Huawei", "70723C" to "Huawei",
        "781DBA" to "Huawei", "80B686" to "Huawei", "88E3AB" to "Huawei",
        "9C28EF" to "Huawei", "A0F479" to "Huawei", "B0A2E7" to "Huawei",
        "C81FBE" to "Huawei", "D02DB3" to "Huawei", "E0247F" to "Huawei",
        "F4C714" to "Huawei", "F83DFF" to "Huawei",

        // Google / Nest
        "3C5AB4" to "Google", "54 60 09" to "Google", "6C AD F7" to "Google",
        "94EB2C" to "Google", "A47733" to "Google", "D8 6C 63" to "Google",
        "F4F5D8" to "Google", "F4 F5 E8" to "Google",

        // OnePlus / Oppo / Realme / Vivo
        "04D9F5" to "OnePlus", "1C9DC2" to "OnePlus", "2C598A" to "OnePlus",
        "64A2F9" to "OnePlus", "704F57" to "OnePlus", "8C B0 3D" to "OnePlus",
        "94652D" to "OnePlus", "9C B6 D0" to "OnePlus", "C0EEFB" to "OnePlus",
        "AC C1 EE" to "Oppo", "1C1B68" to "Oppo", "28FA7A" to "Oppo",
        "30B4B7" to "Oppo", "3C1A0F" to "Oppo", "502 E 91" to "Oppo",
        "70A6CC" to "Oppo", "84 3E 8D" to "Oppo", "B0E17E" to "Oppo",
        "C0F9B0" to "Oppo", "E0F847" to "Oppo",
        "00C3F4" to "Realme", "2CC9F9" to "Realme", "4CFB45" to "Realme",
        "68B9D3" to "Realme", "9C2EA1" to "Realme",
        "001A11" to "Vivo", "0C1DA0" to "Vivo", "3C A9 82" to "Vivo",
        "78 A1 06" to "Vivo", "90 4E 2B" to "Vivo", "B0 48 7A" to "Vivo",

        // Network equipment
        "0018E7" to "TP-Link", "14CC20" to "TP-Link", "1C61B4" to "TP-Link",
        "2C3A2E" to "TP-Link", "3C84 6A" to "TP-Link", "50C7BF" to "TP-Link",
        "60A4B7" to "TP-Link", "74DA88" to "TP-Link", "98DAC4" to "TP-Link",
        "B0BE76" to "TP-Link", "C46E1F" to "TP-Link", "E8DE27" to "TP-Link",
        "001195" to "D-Link", "1CBDB9" to "D-Link", "28107B" to "D-Link",
        "340804" to "D-Link", "5CD998" to "D-Link", "B8A386" to "D-Link",
        "001B2F" to "Netgear", "204E7F" to "Netgear", "2C3033" to "Netgear",
        "44944A" to "Netgear", "A00460" to "Netgear", "C40415" to "Netgear",
        "001731" to "Asus", "08606E" to "Asus", "1C872C" to "Asus",
        "2C56DC" to "Asus", "382C4A" to "Asus", "50465D" to "Asus",
        "704D7B" to "Asus", "AC9E17" to "Asus", "D017C2" to "Asus",
        "000C29" to "VMware", "00155D" to "Microsoft Hyper-V", "080027" to "VirtualBox",
        "000142" to "Cisco", "001A2F" to "Cisco", "002155" to "Cisco",
        "2C542D" to "Cisco", "3C5EC3" to "Cisco", "58BC27" to "Cisco",
        "64F69D" to "Cisco", "A0ECF9" to "Cisco", "F09E63" to "Cisco",
        "000C42" to "Routerboard", "2CC81B" to "Routerboard", "48 8F 5A" to "Routerboard",
        "4C5E0C" to "MikroTik", "6C3B6B" to "MikroTik", "CC2DE0" to "MikroTik",
        "24A43C" to "Ubiquiti", "44D9E7" to "Ubiquiti", "68D79A" to "Ubiquiti",
        "74ACB9" to "Ubiquiti", "F09FC2" to "Ubiquiti",
        "001F3F" to "AVM (Fritz!Box)", "3810D5" to "AVM (Fritz!Box)",
        "9C C7 A6" to "AVM (Fritz!Box)", "C80E14" to "AVM (Fritz!Box)",
        "001C10" to "Cisco-Linksys", "001D7E" to "Cisco-Linksys",
        "48F8B3" to "Cisco-Linksys", "C0 56 27" to "Cisco-Linksys",

        // IoT / embedded
        "001A22" to "Espressif", "24 0A C4" to "Espressif", "2C3AE8" to "Espressif",
        "30AEA4" to "Espressif", "3C6105" to "Espressif", "4C11AE" to "Espressif",
        "5CCF7F" to "Espressif", "600194" to "Espressif", "7C9EBD" to "Espressif",
        "84F3EB" to "Espressif", "8CAAB5" to "Espressif", "A020A6" to "Espressif",
        "B4E62D" to "Espressif", "C44F33" to "Espressif", "D8F15B" to "Espressif",
        "ECFA5C" to "Espressif", "F008D1" to "Espressif",
        "B827EB" to "Raspberry Pi", "DCA632" to "Raspberry Pi",
        "E45F01" to "Raspberry Pi", "2CCF67" to "Raspberry Pi",
        "001132" to "Synology", "0011D8" to "Synology", "24 5E BE" to "Synology",
        "F4 4D 30" to "Elgato", "18B430" to "Nest", "641666" to "Nest",
        "44650D" to "Amazon", "4C1744" to "Amazon", "68 37 E9" to "Amazon",
        "74C246" to "Amazon", "84D6D0" to "Amazon", "A002DC" to "Amazon",
        "F0272D" to "Amazon", "FC65DE" to "Amazon",
        "B0A737" to "Roku", "CC6DA0" to "Roku", "D83134" to "Roku",
        "000E58" to "Sonos", "347E5C" to "Sonos", "5CAAFD" to "Sonos",
        "B8E937" to "Sonos",
        "0017 88" to "Philips Hue", "001788" to "Philips Hue",
        "ECB5FA" to "Philips Hue", "D8 D2 7E" to "Tuya",
        "10D561" to "Tuya", "18DE50" to "Tuya", "50 02 91" to "Tuya",
        "84E342" to "Tuya", "D4A651" to "Tuya",
        "349454" to "Shelly", "C45BBE" to "Shelly", "E8DB84" to "Shelly",
        "3C71BF" to "Espressif", "807D3A" to "Espressif",

        // Computers and chipsets
        "001B21" to "Intel", "001E67" to "Intel", "0021 6A" to "Intel",
        "3C970E" to "Intel", "4851B7" to "Intel", "5CE0C5" to "Intel",
        "7C7A91" to "Intel", "94 65 9C" to "Intel", "A0A8CD" to "Intel",
        "E4A471" to "Intel", "F859 09" to "Intel",
        "000E0C" to "Realtek", "001A6B" to "Realtek", "52540A" to "Realtek",
        "00E04C" to "Realtek", "5C F3 70" to "Realtek",
        "001018" to "Broadcom", "001BE9" to "Broadcom", "B827EB" to "Broadcom",
        "001D0F" to "Texas Instruments", "34B1F7" to "Texas Instruments",
        "A4 34 D9" to "Texas Instruments",
        "40 4E 36" to "AzureWave", "74C63B" to "AzureWave",
        "00 03 7F" to "Atheros", "00 0E 8E" to "Atheros",
        "001CB3" to "Apple", "002436" to "Apple",
        "001E65" to "Sony", "0024BE" to "Sony", "30F9ED" to "Sony",
        "544249" to "Sony", "78 84 3C" to "Sony", "FC0FE6" to "Sony",
        "001F 3B" to "LG", "10F96F" to "LG", "2C54CF" to "LG",
        "58A2B5" to "LG", "700514" to "LG", "A816B2" to "LG",
        "88 5A 92" to "Motorola", "C0 56 E3" to "Motorola",
        "00 1A 8A" to "Nokia", "54 88 0E" to "Nokia",
        "00 26 5E" to "Hon Hai", "28 37 2B" to "Hon Hai",
        "1C 4B D6" to "AzureWave", "2C 33 7A" to "Hon Hai",
        "00 1B 9E" to "Lenovo", "68 F7 28" to "Lenovo",
        "00 1E C9" to "Dell", "18 03 73" to "Dell", "B8 2A 72" to "Dell",
        "00 1F 29" to "HP", "3C D9 2B" to "HP", "94 57 A5" to "HP",
        "00 50 F2" to "Microsoft", "28 18 78" to "Microsoft",
        "7C ED 8D" to "Microsoft", "C8 3F 26" to "Microsoft",
    )

    private val cleaned: Map<String, String> by lazy {
        vendors.entries
            .mapNotNull { (prefix, vendor) ->
                val key = prefix.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
                if (key.length == 6) key to vendor else null
            }
            .toMap()
    }

    fun vendorFor(mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        val key = mac.replace(Regex("[^0-9A-Fa-f]"), "").uppercase().take(6)
        if (key.length != 6) return null
        return cleaned[key]
    }

    /** Number of known prefixes, used by the diagnostics screen. */
    val size: Int get() = cleaned.size
}
