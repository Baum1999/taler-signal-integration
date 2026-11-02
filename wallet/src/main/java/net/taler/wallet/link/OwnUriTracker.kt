/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.wallet.link

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Schmale Schnittstelle, extrahiert damit [buildAggregate] (MultiUriSummary.kt)
 * ohne SharedPreferences/Android-Kontext mit einem Test-Double testbar ist
 * (siehe MultiUriSummaryTest.kt) - [OwnUriTracker] ist die einzige echte
 * Implementierung.
 */
interface OwnUriChecker {
    fun isOwn(uri: String): Boolean
}

/**
 * Merkt sich dauerhaft taler://-URIs, die diese Wallet-Instanz selbst erzeugt
 * hat (initiatePeerPushDebit -> TransactionPeerPushDebit.talerUri, bereits
 * dort abgegriffen wo die App sie sowieso zum Anzeigen/Teilen/NFC liest -
 * siehe MainActivity.selectedTransaction-Collector) - inklusive der dabei
 * erzeugten eigenen transactionId.
 *
 * Grund: TransactionPeerPushCredit (die Antwort von preparePeerPushCredit
 * auf eine EINGEHENDE URI) traegt selbst keinen Identifikator (keinen
 * Purse-Pubkey, keinen Contract-Hash), der sich mit einer eigenen
 * ausgehenden Transaktion abgleichen liesse - wallet-core stellt dafuer
 * aktuell keine Schnittstelle bereit. Der Vergleich muss deshalb
 * clientseitig ueber die URI selbst laufen, bevor preparePeerPushCredit
 * ueberhaupt aufgerufen wird - siehe TalerLinkService.previewPeerPushCredit.
 * Aus demselben Grund haelt HandleUriScreen (Annehmen-Button "Abbrechen" fuer
 * eigene ausgehende Zahlungen) die transactionId hier vor: es gibt sonst
 * keinen Weg, aus der reinen URI wieder zur eigenen TransactionPeerPushDebit
 * zurueckzufinden, ohne (falsch) preparePeerPushCredit auf die eigene URI
 * aufzurufen.
 *
 * Fix (UX-Befund: eigene gesendete Zahlung wird spaeter als "fremd" mit
 * Annehmen/Ablehnen-Buttons angezeigt): vormals ein rein In-Memory
 * MutableMap-Singleton ("ein Prozessneustart verliert die Zuordnung, was
 * fuer den aktuellen Zweck ausreicht"). Diese Annahme stimmt nicht: Signal
 * fragt den Status jeder URI wiederholt per TalerUriRefreshJob ab
 * (TalerPollingCoordinator, ca. alle 20s) und ueberschreibt bei jedem Poll
 * isOwnPayment in TalerPaymentTable neu (TalerUriRefreshJob.applyPreview) -
 * wurde die Taler-App-Instanz zwischen zwei Polls von Android unter
 * Speicherdruck beendet und neu gestartet (auf einem Geraet mit vielen
 * offenen Apps durchaus ueblich, nicht nur bei einem Absturz), ist die
 * In-Memory-Map leer, previewPeerPushCredit faellt auf den regulaeren
 * preparePeerPushCredit-Pfad zurueck (isOwnPayment = false, siehe
 * TalerLinkService.kt) und der naechste Poll kippt eine zuvor korrekt
 * erkannte eigene Zahlung stillschweigend auf "fremd" um. SharedPreferences
 * ueberleben einen Prozessneustart - gleiches Muster wie
 * SentRefundStore/ConsentStore in diesem Paket.
 *
 * Schluessel ist SHA-256(URI) als Hex, nie die URI selbst: eine
 * pay-push-URI ist ein Inhaberpapier und gehoert nicht im Klartext in eine
 * App-Datei (Backups, Geraeteabbilder). Gleiches Verfahren wie OwnUriStore
 * in taler-ios (android-nachzieh-liste.md N15). Fruehere Versionen haben die
 * URI als Schluessel gespeichert; [migrateLegacyKeys] stellt diese Eintraege
 * beim ersten Zugriff um.
 */
class OwnUriTracker(private val prefs: SharedPreferences) : OwnUriChecker {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    init {
        migrateLegacyKeys()
    }

    fun track(uri: String?, transactionId: String) {
        if (uri.isNullOrBlank()) return
        prefs.edit().putString(keyFor(uri), transactionId).apply()
    }

    override fun isOwn(uri: String): Boolean = prefs.contains(keyFor(uri))

    /** Liefert die eigene transactionId zu [uri], oder null, wenn nicht (mehr) bekannt. */
    fun transactionIdFor(uri: String): String? = prefs.getString(keyFor(uri), null)

    private fun migrateLegacyKeys() {
        val legacy = prefs.all.filterKeys { !isHashKey(it) }
        if (legacy.isEmpty()) return
        val editor = prefs.edit()
        legacy.forEach { (uri, transactionId) ->
            if (transactionId is String) editor.putString(keyFor(uri), transactionId)
            editor.remove(uri)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "taler_link_own_uris"

        private val HASH_KEY = Regex("[0-9a-f]{64}")

        internal fun keyFor(uri: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(uri.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        internal fun isHashKey(key: String): Boolean = HASH_KEY.matches(key)
    }
}
