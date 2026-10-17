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
 * Merkt sich uri -> transactionId aus dem ersten erfolgreichen
 * preparePeerPullDebit-Aufruf fuer diese Prozesslaufzeit.
 *
 * Grund (Bug 4, Signal-Integration): anders als preparePeerPushCredit
 * (empirisch als idempotent verifiziert, siehe TalerLinkService.previewForUri)
 * wurde preparePeerPullDebit nie auf Idempotenz nach einer bereits
 * abgeschlossenen Zahlung geprueft. Signals Routine-Polling ruft previewForUri
 * aber auch fuer eine pay-pull-URI weiter auf, nachdem die Anfrage laengst
 * (ausserhalb von Signal, direkt in Taler) bezahlt wurde - ein erneutes
 * preparePeerPullDebit auf einen bereits verbrauchten Purse ist beobachtbar
 * NICHT idempotent (Symptom: die Karte kippt nach dem Bezahlen von "Offen"
 * auf "Ungueltiger Link" mit Betrag 0, statt "Angenommen" mit dem echten
 * Betrag zu zeigen). Fix: sobald einmal eine transactionId fuer eine URI
 * bekannt ist, wird preparePeerPullDebit fuer diese URI nicht mehr erneut
 * aufgerufen - der tatsaechliche (ggf. inzwischen aktualisierte) Zustand kommt
 * dann ausschliesslich noch ueber getTransactionById(transactionId).
 *
 * Wie OwnUriTracker ein reiner Applikations-Singleton (kein Feld auf
 * TalerLinkService selbst) - ueberlebt damit den Bind/Unbind-Zyklus des
 * Service (siehe TalerLinkService.previewForUri-Kommentar: der Service selbst
 * wird pro Poll-Zyklus zerstoert/neu erzeugt, ein Feld auf der Service-Klasse
 * waere bei jedem Poll leer), nicht aber einen vollstaendigen Prozessneustart.
 * Fuer diesen Zweck ausreichend: verliert der Prozess den Zustand, wird die
 * naechste previewPeerPullDebit einfach wieder frisch prepared (kein neuer
 * Fehlerfall, nur derselbe einmalige Re-Prepare wie vor diesem Fix).
 */
object PeerPullDebitCache {
  private val transactionIds: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())

  fun cachedTransactionId(uri: String): String? = transactionIds[uri]

  fun remember(uri: String, transactionId: String) {
    transactionIds[uri] = transactionId
  }
}
