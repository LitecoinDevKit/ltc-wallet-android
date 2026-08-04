package com.indigonakamoto.ltcwalletsmoke

import org.litecoindevkit.Descriptor
import org.litecoindevkit.DescriptorSecretKey
import org.litecoindevkit.ElectrumClient
import org.litecoindevkit.KeychainKind
import org.litecoindevkit.MasterKeys
import org.litecoindevkit.Mnemonic
import org.litecoindevkit.MwebStore
import org.litecoindevkit.MwebSyncer
import org.litecoindevkit.Network
import org.litecoindevkit.NetworkKind
import org.litecoindevkit.Persister
import org.litecoindevkit.Wallet
import org.litecoindevkit.mwebIsMwebAddress

enum class SmokeStatus { PASS, FAIL, NOTE }

data class SmokeLine(val status: SmokeStatus, val message: String)

object SmokeConstants {
    const val MNEMONIC =
        "space echo position soft dizzy revise inflict damaged swallow bid thunder expend"
    const val ELECTRUM_URL = "ssl://electrum.ltc.xurious.com:51002"
    const val ELECTRUM_FALLBACK_URL = "ssl://electrum-ltc.bysh.me:51002"
    val MWEB_SEED: ByteArray = ByteArray(32) { 0x42 }
}

/**
 * Shared five-step LitecoinDevKit smoke checklist.
 * Call from a background dispatcher — Electrum is blocking.
 */
object SmokeRunner {
    fun run(onLine: (SmokeLine) -> Unit) {
        fun report(status: SmokeStatus, message: String) = onLine(SmokeLine(status, message))

        val wallet: Wallet = try {
            val mnemonic = Mnemonic.fromString(SmokeConstants.MNEMONIC)
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

        try {
            val keys = MasterKeys.fromSeed(SmokeConstants.MWEB_SEED, Network.TESTNET4)
            val address = keys.receiveAddress(0u, Network.TESTNET4)
            val text = address.toString()
            if (text.startsWith("tmweb1") && mwebIsMwebAddress(address)) {
                report(SmokeStatus.PASS, "3. MWEB address: $text")
            } else {
                report(SmokeStatus.FAIL, "3. Expected tmweb1… MWEB address, got $text")
            }
            MwebSyncer(null)
            report(
                SmokeStatus.NOTE,
                "3b. MwebSyncer constructed; P2P sync not exposed via UniFFI yet",
            )
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "3. MWEB address failed: ${e.message}")
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

        try {
            val store = MwebStore()
            val combined = wallet.balanceCombined(store)
            val transparent = combined.transparent.total.toSat()
            val mwebConfirmed = combined.mwebConfirmed.toSat()
            val mwebPending = combined.mwebUntrustedPending.toSat()
            report(
                SmokeStatus.PASS,
                "5. Combined balance — transparent=$transparent sats, mwebConfirmed=$mwebConfirmed, mwebPending=$mwebPending",
            )
            if (store.unspentCount() == 0uL) {
                report(
                    SmokeStatus.NOTE,
                    "5b. MwebStore empty (expected without MWEB P2P sync)",
                )
            }
        } catch (e: Exception) {
            report(SmokeStatus.FAIL, "5. Combined balance failed: ${e.message}")
        }
    }
}
