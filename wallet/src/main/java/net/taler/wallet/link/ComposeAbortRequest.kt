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

import net.taler.wallet.transactions.TransactionMajorState

/**
 * Reine Logik fuer talerlink://compose-abort (docs/cross-platform/link-protokoll.md
 * Abschnitt 5 und 9): welche URIs Taler bearbeitet und welches Ergebnis jede
 * URI im Ruecksprung bekommt. Ohne Android-Klassen, damit sie mit plain JUnit
 * testbar bleibt (dieses Repo hat kein Robolectric).
 *
 * Eine URI, die diese Wallet nicht selbst erzeugt hat, wird NIE angenommen,
 * sondern bekommt `not_own`. Genau das unterscheidet compose-abort vom
 * Aktionslink taler://pay-push/..., bei dem Taler eine fremde URI als
 * "Annehmen" deutet: Signal auf einem zweiten Geraet desselben Kontos haelt
 * eine dort synchronisierte Zahlung fuer die eigene, die Wallet auf diesem
 * Geraet hat sie aber nie erzeugt.
 */
object ComposeAbortRequest {

    /** Hoechstens so viele URIs bearbeitet Taler je Link, der Rest bekommt `failed`. */
    const val MAX_URIS = 32

    const val MAX_RECIPIENT_HINT_LENGTH = 80

    /**
     * Doppelte URIs zusammenfassen (Reihenfolge des ersten Auftretens bleibt),
     * damit jede Eingangs-URI genau ein Ergebnis bekommt.
     */
    fun distinctUris(uris: List<String>): List<String> =
        uris.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Ob die URI an [index] bearbeitet wird: innerhalb der Obergrenze und eine
     * pay-push- oder pay-pull-URI. Alles andere bekommt `failed`.
     */
    fun isProcessable(uris: List<String>, index: Int): Boolean {
        if (index !in uris.indices || index >= MAX_URIS) return false
        val kind = TalerUriParser.classify(uris[index])
        return kind == TalerUriKind.PAY_PUSH || kind == TalerUriKind.PAY_PULL
    }

    fun hasProcessable(uris: List<String>): Boolean =
        uris.indices.any { isProcessable(uris, it) }

    /**
     * Anzeigehinweis aus Signal: Steuerzeichen raus, gekuerzt. Rein
     * kosmetisch, angreiferkontrolliert, nie Entscheidungsgrundlage.
     */
    fun sanitizeHint(hint: String?): String? =
        hint?.filter { it.isDefined() && !it.isISOControl() }
            ?.trim()
            ?.take(MAX_RECIPIENT_HINT_LENGTH)
            ?.takeIf { it.isNotEmpty() }

    /**
     * Ergebnis einer URI aus dem Zustand der eigenen Transaktion vor dem
     * Abbruchversuch und dem Ausgang des Versuchs (gleiches Mapping wie
     * taler-ios AbortOutcome):
     * - Abbruch erfolgreich -> `aborted`.
     * - Keine eigene Transaktion ([majorState] null, nicht versucht) -> `not_own`.
     * - aborting/aborted -> `aborted`, done -> `already_claimed`, expired -> `expired`.
     * - Sonst: Versuch gescheitert -> `failed`, nicht versucht -> `declined`.
     */
    fun outcome(majorState: TransactionMajorState?, abortSucceeded: Boolean?): AbortOutcome {
        if (abortSucceeded == true) return AbortOutcome.ABORTED
        if (majorState == null) {
            return if (abortSucceeded == false) AbortOutcome.FAILED else AbortOutcome.NOT_OWN
        }
        return when (majorState) {
            TransactionMajorState.Aborting,
            TransactionMajorState.Aborted,
            TransactionMajorState.SuspendedAborting -> AbortOutcome.ABORTED
            TransactionMajorState.Done -> AbortOutcome.ALREADY_CLAIMED
            TransactionMajorState.Expired -> AbortOutcome.EXPIRED
            else -> if (abortSucceeded == false) AbortOutcome.FAILED else AbortOutcome.DECLINED
        }
    }

    /** Zustaende, in denen nicht (erneut) abgebrochen, sondern nur gemappt wird. */
    fun isSettled(majorState: TransactionMajorState?): Boolean = when (majorState) {
        TransactionMajorState.Done,
        TransactionMajorState.Expired,
        TransactionMajorState.Aborting,
        TransactionMajorState.Aborted,
        TransactionMajorState.SuspendedAborting -> true
        else -> false
    }

    /** Wert eines `result`-Parameters im Ruecksprung: `<uri>|<ergebnis>`. */
    fun resultValue(uri: String, outcome: AbortOutcome): String = "$uri|${outcome.wireValue}"
}

enum class AbortOutcome(val wireValue: String) {
    ABORTED("aborted"),
    ALREADY_CLAIMED("already_claimed"),
    EXPIRED("expired"),
    DECLINED("declined"),
    NOT_OWN("not_own"),
    FAILED("failed"),
}
