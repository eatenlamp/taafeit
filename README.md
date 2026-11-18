<!--
SPDX-FileCopyrightText: 2026 eatenlamp <eatenlamp@proton.me>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Taafeit 🛡️

Android network and identity toolkit for your own devices and networks.

Taafeit is honest about privileges: every screen tells you whether an action
will run as **root**, through **Shizuku**, or not at all. Nothing is reported as
"applied" until the result is read back from the system.

---

## ✨ Features

### 📱 Identity emulation (root)
- Change `ro.product.model`, `manufacturer`, `brand`, `device`, `name` and
  `ro.build.fingerprint` with `resetprop`
- Ready-made presets plus a fully custom identity
- Every value is verified after writing; partial failures are shown as such
- Original values are captured automatically and can be restored with one tap
- Values are temporary by design and return after a reboot

### 🗄️ Profile vault
- Capture the current identity as a named profile
- Deploy a profile with the same verified apply path
- Export and import profiles as JSON through the system file picker
- Legacy profiles from older versions are migrated automatically

### 🛡️ Network shield
- Locks the gateway MAC as a baseline and alerts when it changes
- Detects duplicate MAC addresses claiming several IPs
- Notifications are throttled and only sent when permission is granted
- With root it re-installs the trusted neighbour entry after an alert
- Resets the baseline on network changes instead of false alarms

### 🔍 Network audit
- One parallel ping sweep plus the kernel neighbour table (no 254 `su` calls)
- Vendor lookup from a bundled offline OUI table
- Reverse DNS and TTL-based OS guess
- One-tap block/unblock through `iptables` (root + hotspot only)

### 🔒 Privacy
- Private DNS (DoH) switcher with a capability check and read-back
- Mock location with a real foreground service, real state and a 1-second
  refresh so apps keep receiving the position
- Clear warnings when Taafeit is not selected as the mock location app

### ⚙️ Status and configuration
- Full capability report: system properties, secure settings, neighbour table,
  firewall, mock location, notifications
- Device, build, kernel and network diagnostics
- Light / dark / system theme, Material You colours (off by default)
- English and Russian, per-app language on Android 13+

---

## 🔐 Privileges

Taafeit uses a single privilege manager with three levels:

| Level | How it runs | Can do |
|-------|-------------|--------|
| **Root** | `su` (Magisk, KernelSU, APatch) | Everything below plus `resetprop` and `iptables` |
| **Shizuku** | Shizuku user service, uid 2000 (shell) | `getprop`, `settings`, `ip neigh`, `ping` |
| **App** | Normal app uid | Read-only UI, mock location |

Shizuku is auto-detected and bound when it is running and authorised. Root is
preferred when both are available. The app never silently degrades a privileged
action to the app uid.

---

## 🛠 Tech stack

- Kotlin + coroutines + StateFlow
- Jetpack Compose, Material 3
- Navigation Compose
- Shizuku API (AIDL user service)
- Timber for logging
- No Room, no Hilt, no OkHttp, and no internet permission

---

## 📥 Build

Requirements:

- Android 10+ (API 29+) on the device
- JDK 21
- Android SDK with compileSdk 37

```bash
git clone git@github.com:eatenlamp/taafeit.git
cd taafeit
./gradlew :app:assembleDebug
```

Release builds read the signing material from environment variables:

```bash
KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... ./gradlew :app:assembleRelease
```

`taafeit.jks` is not part of the repository and must never be committed.

---

## ⚖️ Disclaimer

For educational use and authorised testing only.

Only use Taafeit on devices and networks you own or have explicit permission to
test. Unauthorised interception or disruption of networks is illegal. The author
is not responsible for misuse.

---

## 📄 License

AGPL-3.0-or-later. See the license file for details.
