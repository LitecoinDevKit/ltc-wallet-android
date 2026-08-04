package com.indigonakamoto.ltcwalletsmoke

import java.io.File
import org.litecoindevkit.Amount
import org.litecoindevkit.Descriptor
import org.litecoindevkit.DescriptorSecretKey
import org.litecoindevkit.ElectrumClient
import org.litecoindevkit.KeychainKind
import org.litecoindevkit.MasterKeys
import org.litecoindevkit.Mnemonic
import org.litecoindevkit.MwebException
import org.litecoindevkit.MwebStore
import org.litecoindevkit.MwebSyncManager
import org.litecoindevkit.Network
import org.litecoindevkit.NetworkKind
import org.litecoindevkit.Persister
import org.litecoindevkit.SyncState
import org.litecoindevkit.Wallet
import org.litecoindevkit.mwebBroadcast
import org.litecoindevkit.mwebDiscoverPeers
import org.litecoindevkit.mwebIsMwebAddress

enum class SmokeStatus { PASS, FAIL, NOTE }

data class SmokeLine(val status: SmokeStatus, val message: String)

object SmokeConstants {
    /** BIP39 English test vector (valid checksum). */
    const val MNEMONIC =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    const val ELECTRUM_URL = "ssl://electrum.ltc.xurious.com:51002"
    const val ELECTRUM_FALLBACK_URL = "ssl://electrum-ltc.bysh.me:51002"

    /**
     * Extra LIP-0006 peers to try before DNS discovery (`host:port`), e.g. your
     * own litecoind testnet node. Empty = DNS-discover public MWEB peers.
     */
    val MWEB_PEERS: List<String> = emptyList()

    /** Litoshis for the self-send spend loop in step 7. */
    const val SPEND_AMOUNT = 10_000uL
    const val SPEND_FEE = 5_000uL
}

/**
 * Shared seven-step LitecoinDevKit smoke checklist.
 * Call from a background dispatcher — Electrum and MWEB sync are blocking.
 *
 * `dataDir` holds mweb.sqlite + mweb_sync.json across runs (use
 * `context.filesDir`).
 */
object SmokeRunner {
    fun run(dataDir: File, onLine: (SmokeLine) -> Unit) {
        fun report(status: SmokeStatus, message: String) = onLine(SmokeLine(status, message))

        val mnemonic: Mnemonic
        val wallet: Wallet = try {
            mnemonic = Mnemonic.fromString(SmokeConstants.MNEMONIC)
            val descriptor = Descriptor.newBip84(
                DescriptorSecretKey(NetworkKind.TEST, mnemonic, null),
                KeychainKind.EXTERNAL,
                NetworkKind.TEST,
            )
            val change = Descriptor.newBip84(
                DescriptorSecretKey(NetworkKind.TEST, mnemonic, null),
                KeychainKind.INTERNAL,
                NetworkKind.TEST,
            )
            val w = Wallet(
                descriptor,
                change,
                Network.TESTNET4,
                Persister.newInMemory(),
                25u,
            )
            report(SmokeStatus.PASS, "1. Created BIP84 Wallet on testnet4 (in-memory)")
            w
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "1. Create wallet failed: ${e.message}")
            return
        }

        try {
            val info = wallet.revealNextAddress(KeychainKind.EXTERNAL)
            val text = info.address.toString()
            if (text.startsWith("tltc1")) {
                report(SmokeStatus.PASS, "2. Transparent address: $text")
            } else {
                report(SmokeStatus.FAIL, "2. Expected tltc1… prefix, got $text")
            }
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "2. Transparent address failed: ${e.message}")
        }

        // 3. MWEB keys from the same mnemonic (LitecoinCore scheme) + address
        val keys: MasterKeys = try {
            val k = MasterKeys.fromMnemonic(mnemonic, null, null, Network.TESTNET4)
            val address = k.receiveAddress(0u, Network.TESTNET4)
            val text = address.toString()
            if (text.startsWith("tmweb1") && mwebIsMwebAddress(address)) {
                report(SmokeStatus.PASS, "3. MWEB address (from mnemonic): $text")
            } else {
                report(SmokeStatus.FAIL, "3. Expected tmweb1… MWEB address, got $text")
            }
            k
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "3. MWEB keys failed: ${e.message}")
            return
        }

        var electrumOk = false
        for (url in listOf(SmokeConstants.ELECTRUM_URL, SmokeConstants.ELECTRUM_FALLBACK_URL)) {
            try {
                report(SmokeStatus.NOTE, "4. Connecting Electrum $url (validateDomain=false)…")
                val client = ElectrumClient(
                    url,
                    /* socks5 = */ null,
                    /* timeout = */ 30u.toUByte(),
                    /* retry = */ 2u.toUByte(),
                    /* validateDomain = */ false,
                )
                val request = wallet.startFullScan().build()
                val update = client.fullScan(request, 20uL, 20uL, false)
                wallet.applyUpdate(update)
                report(SmokeStatus.PASS, "4. Electrum fullScan + applyUpdate via $url")
                electrumOk = true
                break
            } catch (e: Exception) {
                report(SmokeStatus.NOTE, "4. Electrum $url failed: ${e.message}")
            }
        }
        if (!electrumOk) {
            report(SmokeStatus.FAIL, "4. Electrum fullScan failed on all testnet4 endpoints")
        }

        // 5. Persistent MWEB store + sync state
        val storePath = File(dataDir, "mweb.sqlite").absolutePath
        val stateFile = File(dataDir, "mweb_sync.json")
        val store: MwebStore
        val state: SyncState
        try {
            store = MwebStore.loadSqlite(storePath)
            state = if (stateFile.isFile) {
                SyncState.fromJson(stateFile.readText())
            } else {
                SyncState()
            }
            report(SmokeStatus.PASS, "5. MWEB store + sync state at ${dataDir.absolutePath}")
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "5. MWEB store load failed: ${e.message}")
            return
        }

        // 6. LIP-0006 differential sync against testnet4 peers
        var peers = SmokeConstants.MWEB_PEERS
        var syncOk = false
        try {
            if (peers.isEmpty()) {
                report(SmokeStatus.NOTE, "6. Discovering MWEB peers via DNS seeds…")
                peers = mwebDiscoverPeers(Network.TESTNET4)
            }
            if (peers.isEmpty()) {
                report(
                    SmokeStatus.FAIL,
                    "6. No MWEB peers found (DNS seeds empty); set SmokeConstants.MWEB_PEERS",
                )
            } else {
                report(SmokeStatus.NOTE, "6. Syncing MWEB against ${peers.size} peer(s): $peers")
                val syncReport = MwebSyncManager().syncAtTip(wallet, store, keys, state, peers)
                store.persistSqlite(storePath)
                stateFile.writeText(state.toJson())
                report(
                    SmokeStatus.PASS,
                    "6. MWEB synced at height ${syncReport.tipHeight} — " +
                        "downloaded=${syncReport.downloaded}, found=${syncReport.found}, " +
                        "spent=${syncReport.spent}",
                )
                syncOk = true
            }
        } catch (e: MwebException.Throttled) {
            report(SmokeStatus.NOTE, "6. MWEB peer throttled (back off and retry later): ${e.message}")
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "6. MWEB sync failed: ${e.message}")
        }

        // 7. Balances + spend loop (self-send when funds are available)
        try {
            val combined = wallet.balanceCombined(store)
            val tip = wallet.latestCheckpoint().height
            val spendable = store.spendableBalance(tip)
            report(
                SmokeStatus.PASS,
                "7. Balance — transparent=${combined.transparent.total.toSat()}, " +
                    "mwebConfirmed=${combined.mwebConfirmed.toSat()}, " +
                    "mwebPending=${combined.mwebUntrustedPending.toSat()}, spendable=$spendable",
            )
            val needed = SmokeConstants.SPEND_AMOUNT + SmokeConstants.SPEND_FEE
            if (!syncOk) {
                report(SmokeStatus.NOTE, "7b. Spend loop skipped (sync did not complete)")
            } else if (spendable < needed) {
                val receive = keys.receiveAddress(0u, Network.TESTNET4)
                report(
                    SmokeStatus.NOTE,
                    "7b. Spend loop skipped — need $needed spendable litoshis; " +
                        "send testnet LTC to $receive",
                )
            } else {
                val dest = keys.receiveAddress(1u, Network.TESTNET4)
                val funded = wallet.fundMwebSend(
                    store,
                    keys,
                    dest,
                    Amount.fromSat(SmokeConstants.SPEND_AMOUNT),
                    Amount.fromSat(SmokeConstants.SPEND_FEE),
                    /* changeIndex = */ 0u,
                )
                val spentIds = funded.spentCoinIds()
                val tx = wallet.signAndExtractFundedMweb(funded, keys, store)
                val ack = mwebBroadcast(tx, peers, Network.TESTNET4)
                store.markSpent(spentIds)
                store.persistSqlite(storePath)
                report(
                    SmokeStatus.PASS,
                    "7b. MWEB self-send broadcast ($ack) — spent ${spentIds.size} coin(s), " +
                        "balance now spendable=${store.spendableBalance(tip)}",
                )
            }
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "7. Balance / spend loop failed: ${e.message}")
        }
    }
}
