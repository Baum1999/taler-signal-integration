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

package net.taler.wallet.peer

import android.content.Context
import android.content.SharedPreferences

data class GroupShareInfo(val groupId: String, val index: Int, val count: Int)

/**
 * Merkt sich dauerhaft, zu welcher Gruppen-Zahlung (siehe
 * PROMPT_parallel_group_split.md) eine einzelne peer-push-debit-Transaktion
 * gehoert - reine lokale UI-Zusatzinfo (welcher Anteil von wie vielen),
 * bewusst NICHT Teil des Contract-Term-`summary` (Nutzer-Vorgabe
 * 2026-09-02: das vom Nutzer eingetippte Verwendungszweck-Feld bleibt
 * unbeschnitten, siehe docs/API.md). [GroupShareInfo.groupId] ist die
 * bereits vorhandene correlationId des Sende-Vorgangs
 * (PrepareSendRequest.correlationId), nicht die Signal-Gruppen-ID selbst.
 *
 * SharedPreferences nach demselben Muster wie ConsentStore/SentRefundStore/
 * OwnUriTracker (link-Paket) - kein Proto-DataStore-Schemaeingriff fuer
 * diesen engen Zweck, ueberlebt Prozessneustart. Ueberlebt KEINE
 * Deinstallation/App-Daten-loeschen - im Chat abgeklaert: dieser Fork hat
 * kein Multi-Geraete-Sync (kein Backup/Recovery-Feature im Code gefunden),
 * die Transaktionsliste selbst ist schon rein lokal in wallet-core
 * gespeichert, ein lokaler Store fuer die Gruppen-Zuordnung ist also keine
 * staerkere Einschraenkung als der Rest der App bereits hat.
 *
 * Zwei Praefixe in derselben Datei, da SharedPreferences nicht nach Value
 * suchen kann: `share_<transactionId>` fuer die Vorwaerts-Abfrage
 * (Transaktion -> Gruppeninfo, Detailansicht) und `members_<groupId>` fuer
 * die Rueckwaerts-Abfrage (Gruppe -> alle transactionIds, Sammelzeile in
 * der Transaktionsliste).
 */
class GroupShareStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun save(transactionId: String, info: GroupShareInfo) {
        prefs.edit()
            .putString(shareKey(transactionId), "${info.groupId}|${info.index}|${info.count}")
            .putString(
                membersKey(info.groupId),
                (transactionIdsFor(info.groupId) + transactionId).distinct().joinToString(","),
            )
            .apply()
    }

    fun get(transactionId: String): GroupShareInfo? {
        val stored = prefs.getString(shareKey(transactionId), null) ?: return null
        val parts = stored.split("|", limit = 3)
        if (parts.size != 3) return null
        val index = parts[1].toIntOrNull() ?: return null
        val count = parts[2].toIntOrNull() ?: return null
        return GroupShareInfo(groupId = parts[0], index = index, count = count)
    }

    fun transactionIdsFor(groupId: String): List<String> {
        val stored = prefs.getString(membersKey(groupId), null) ?: return emptyList()
        return stored.split(",").filter { it.isNotBlank() }
    }

    private fun shareKey(transactionId: String) = "share_$transactionId"
    private fun membersKey(groupId: String) = "members_$groupId"

    companion object {
        private const val PREFS_NAME = "taler_peer_group_shares"
    }
}
