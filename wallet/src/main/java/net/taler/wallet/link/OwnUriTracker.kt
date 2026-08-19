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

import java.util.Collections

/**
 * Merkt sich taler://-URIs, die diese Wallet-Instanz selbst erzeugt hat
 * (initiatePeerPushDebit -> TransactionPeerPushDebit.talerUri, bereits
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
 * Rein In-Memory (nicht persistiert) - ein Prozessneustart verliert die
 * Zuordnung, was fuer den aktuellen Zweck (Verhindern eines versehentlichen
 * Selbst-Annehmens kurz nach dem eigenen Versenden) ausreicht.
 */
object OwnUriTracker {
  private val ownUris: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())

  fun track(uri: String?, transactionId: String) {
    if (uri.isNullOrBlank()) return
    ownUris[uri] = transactionId
  }

  fun isOwn(uri: String): Boolean = ownUris.containsKey(uri)

  /** Liefert die eigene transactionId zu [uri], oder null, wenn nicht (mehr) bekannt. */
  fun transactionIdFor(uri: String): String? = ownUris[uri]
}
