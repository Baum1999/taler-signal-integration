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
 * SentRefundStore/ConsentStore in diesem Paket. Kein neu exponiertes
 * Geheimnis: die taler://-URI selbst steht ohnehin im Klartext in der
 * Signal-Nachricht.
 */
class OwnUriTracker(private val prefs: SharedPreferences) : OwnUriChecker {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun track(uri: String?, transactionId: String) {
        if (uri.isNullOrBlank()) return
        prefs.edit().putString(uri, transactionId).apply()
    }

    override fun isOwn(uri: String): Boolean = prefs.contains(uri)

    /** Liefert die eigene transactionId zu [uri], oder null, wenn nicht (mehr) bekannt. */
    fun transactionIdFor(uri: String): String? = prefs.getString(uri, null)

    companion object {
        private const val PREFS_NAME = "taler_link_own_uris"
    }
}
