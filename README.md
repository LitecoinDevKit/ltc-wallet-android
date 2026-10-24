# ltc-wallet-android

Smoke app for the published [`bdk-ltc-android.aar`](https://github.com/LitecoinDevKit/bdk-ffi/releases/tag/3.1.0-litecoin.3) (`org.litecoindevkit`).

**Pin source of truth:** [ADOPTION.md § Blessed consumer pin](https://github.com/LitecoinDevKit/bdk/blob/litecoin/docs/ADOPTION.md#blessed-consumer-pin-this-phase--source-of-truth) (AAR **`3.1.0-litecoin.3`**, same binaries as `ltc-swift` `3.1.0-litecoin.3`). Do not invent a divergent pin here.

Sibling of [`ltc-wallet-mac`](https://github.com/LitecoinDevKit/ltc-wallet-mac) — does **not** use `wallet-core`. Calls UniFFI directly.

Integrator docs: [ADOPTION.md](https://github.com/LitecoinDevKit/bdk/blob/litecoin/docs/ADOPTION.md) · dogfood: [DOGFOOD_CHECKLIST.md](https://github.com/LitecoinDevKit/bdk/blob/litecoin/docs/DOGFOOD_CHECKLIST.md).

## What it proves

1. Mnemonic → BIP84 `Wallet` on **testnet4**
2. Transparent receive address (`tltc1…`)
3. MWEB stealth address (`tmweb1…`) via `MasterKeys.fromMnemonic` (LitecoinCore scheme, same phrase as the descriptors)
4. Electrum full scan + `applyUpdate` (`ssl://electrum.ltc.xurious.com:51002`, TLS validate off)
5. Persistent `MwebStore` (SQLite) + `SyncState` JSON in the app's `filesDir`
6. Live MWEB LIP-0006 sync: `mwebDiscoverPeers(TESTNET4)` → `MwebSyncManager.syncAtTip` (differential, resumable)
7. `balanceCombined` + maturity-aware `spendableBalance`; when the wallet holds spendable MWEB coins, a full self-send loop: `fundMwebSend` → `signAndExtractFundedMweb` → `mwebBroadcast` (P2P) → `markSpent`

To exercise the receive/spend loop, send testnet LTC to the `tmweb1…` address shown by step 3 (peg-ins mature after 6 blocks), then re-run.

Configure `SmokeConstants.MWEB_PEERS` to point at your own litecoind testnet node (`host:19335`) if public MWEB peers are scarce.

## Mirror this in your app

Copy the UniFFI call order from `SmokeRunner.kt` — not the AppCompat UI:

1. `Mnemonic` → BIP84 `Wallet` + `MasterKeys.fromMnemonic` (same phrase)
2. Electrum `fullScan` → `wallet.applyUpdate`
3. `MwebStore` (SQLite path) + persist `SyncState` JSON beside it
4. `MwebSyncer` / sync-at-tip on a background dispatcher (**blocking**)
5. `balanceCombined` for display; use maturity-aware spendable for sends
6. Peg-in when needed: `prepareMwebPegin` → sign transparent → broadcast → wait 6 confs
7. `fundMwebSend` / `fundMwebPegout` → `signAndExtractFundedMweb` → broadcast (P2P or RPC) → `markSpent`

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

The UI is a minimal AppCompat screen (button + log). Steps 4 and 6 need network access (Electrum + MWEB P2P on :19335); `INTERNET` permission is already declared.

Kotlin **2.3.10** is required (matches the Kotlin metadata in the published AAR).

## Results

| Date | Mode | Result |
|------|------|--------|
| 2026-08-04 | `./gradlew :app:assembleDebug` (3.1.0-litecoin.0) | **BUILD SUCCESSFUL** — `app/build/outputs/apk/debug/app-debug.apk` (~42MB) includes `libbdkffi.so` + `org.litecoindevkit`. |

## Layout

```text
scripts/fetch-aar.sh     → download release AAR
app/libs/                → bdk-ltc-android.aar (gitignored)
app/src/main/java/…/
  SmokeRunner.kt         → shared checklist
  MainActivity.kt        → AppCompat UI (smoke on Dispatchers.IO)
```
