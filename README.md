# ltc-wallet-android

Smoke app for the published [`bdk-ltc-android.aar`](https://github.com/IndigoNakamoto/bdk-ffi/releases/tag/3.1.0-litecoin.0) (`org.litecoindevkit`, `3.1.0-litecoin.0`).

Sibling of [`ltc-wallet-mac`](https://github.com/IndigoNakamoto/ltc-wallet-mac) — does **not** use `wallet-core`. Calls UniFFI directly.

## What it proves

1. Mnemonic → BIP84 `Wallet` on **testnet4**
2. Transparent receive address (`tltc1…`)
3. MWEB stealth address (`tmweb1…`) via `MasterKeys.fromSeed`
4. Electrum full scan + `applyUpdate` (`ssl://electrum.ltc.xurious.com:51002`, TLS validate off)
5. `balanceCombined` with empty `MwebStore`

**Not proven:** live MWEB LIP-0006 sync (`MwebSyncer` is construct-only in UniFFI today).

## Setup

```bash
cd /Users/indigo/Dev/ltc-wallet-android
bash ./scripts/fetch-aar.sh          # → app/libs/bdk-ltc-android.aar (gitignored)
# Ensure Android SDK: local.properties with sdk.dir=… (or ANDROID_HOME)
```

## Run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug          # device or emulator
# then tap "Run Smoke" in the app
```

Or open the folder in Android Studio and run the `app` configuration.

The UI is a minimal AppCompat screen (button + log). Step 4 needs network access to a public testnet4 Electrum server.

Kotlin **2.3.10** is required (matches the Kotlin metadata in the published AAR).

## Results

| Date | Mode | Result |
|------|------|--------|
| 2026-08-04 | `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** — `app/build/outputs/apk/debug/app-debug.apk` (~42MB) includes `libbdkffi.so` + `org.litecoindevkit`. Tap **Run Smoke** on a device/emulator to execute the checklist (needs network for Electrum). |

## Layout

```text
scripts/fetch-aar.sh     → download release AAR
app/libs/                → bdk-ltc-android.aar (gitignored)
app/src/main/java/…/
  SmokeRunner.kt         → shared checklist
  MainActivity.kt        → AppCompat UI (Electrum on Dispatchers.IO)
```
