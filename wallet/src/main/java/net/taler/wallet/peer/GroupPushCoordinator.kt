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

import net.taler.wallet.backend.TalerErrorInfo

/**
 * Ergebnis einer einzelnen Anteils-URI bei einem parallelen Gruppen-Split-
 * Versand (PeerManager.initiatePeerPushDebitGroup, siehe
 * PROMPT_parallel_group_split.md Meilenstein 1). Getrennt von OutgoingState
 * (peer/OutgoingState.kt) - das bleibt exklusiv fuer den bestehenden
 * Einzel-Sende-Pfad (_outgoingPushState), der genau EINE Purse gleichzeitig
 * kennt.
 *
 * [Failure.transactionId] ist gesetzt, falls die Purse zwar entstand, aber
 * nie eine talerUri bekam (Retries ausgeschoepft) - fuer eine spaetere
 * Aufraeum-Entscheidung (abortTransaction ja/nein). Bewusst NICHT hier
 * automatisch aufgeraeumt, siehe Regel 11 in
 * PROMPT_parallel_group_split.md: das ist eine Produktentscheidung, keine
 * Implementierungsdetail-Entscheidung.
 */
sealed class ShareResult {
    data class Success(val transactionId: String, val talerUri: String) : ShareResult()
    data class Failure(val transactionId: String?, val error: TalerErrorInfo) : ShareResult()
}

/** Nur die erfolgreich bestaetigten URIs, in derselben Reihenfolge wie [this]. */
fun List<ShareResult>.successfulUris(): List<String> =
    filterIsInstance<ShareResult.Success>().map { it.talerUri }

/** true nur, wenn ALLE Anteile erfolgreich waren (leere Liste zaehlt nicht als Erfolg). */
fun List<ShareResult>.allSucceeded(): Boolean =
    isNotEmpty() && all { it is ShareResult.Success }

/** Anteile, die nach Ausschoepfen der Retries endgueltig gescheitert sind. */
fun List<ShareResult>.failures(): List<ShareResult.Failure> =
    filterIsInstance<ShareResult.Failure>()

/**
 * UI-Zustand fuer PeerManager.initiatePeerPushDebitGroupAsync (Meilenstein
 * 3) - eigenstaendig von OutgoingState (peer/OutgoingState.kt), das genau
 * EINE Purse gleichzeitig kennt.
 */
sealed class GroupPushState {
    data object Idle : GroupPushState()
    data class InProgress(val total: Int) : GroupPushState()
    data class Done(val results: List<ShareResult>) : GroupPushState()
}
