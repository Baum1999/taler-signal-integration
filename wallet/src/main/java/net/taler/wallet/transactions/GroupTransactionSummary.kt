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

package net.taler.wallet.transactions

import net.taler.common.Amount

/**
 * Aufschluesselung der Anteile einer Gruppen-Sammelzeile in der
 * Transaktionsliste (siehe GroupTransactionRow in TransactionsComposable.kt).
 * [total] zaehlt den Eigenanteil des Erstellers mit, falls [includeSelf] beim
 * Aufruf von [computeGroupProgress] true war - der Ersteller behaelt seinen
 * Anteil ja lediglich ein, es gibt dafuer keinen offenen Transfer, der noch
 * abgewartet werden muesste, deshalb zaehlt er sofort als bezahlt (sonst
 * koennte der Fortschrittsbalken bei einer solchen Gruppe nie 100% erreichen,
 * selbst wenn alle echten Empfaenger laengst abgeschlossen haben).
 */
data class GroupProgress(val paid: Int, val open: Int, val declined: Int, val total: Int)

private val declinedMajorStates = setOf(
    TransactionMajorState.Aborted,
    TransactionMajorState.Failed,
    TransactionMajorState.Expired,
    TransactionMajorState.Deleted,
)

fun computeGroupProgress(shares: List<Transaction>, includeSelf: Boolean?): GroupProgress {
    val declined = shares.count { it.txState.major in declinedMajorStates }
    val paidShares = shares.count { it.txState.major == TransactionMajorState.Done }
    val selfCounts = includeSelf == true
    val paid = paidShares + if (selfCounts) 1 else 0
    val total = shares.size + if (selfCounts) 1 else 0
    return GroupProgress(paid = paid, open = total - paid - declined, declined = declined, total = total)
}

/**
 * Gesamtbetrag der Gruppen-Sammelzeile: Summe aller sichtbaren Anteile, plus
 * ein weiterer Anteil in derselben Hoehe, falls der Ersteller sich beim
 * Splitten mitgezaehlt hat ([includeSelf]) - der Eigenanteil des Erstellers
 * ist selbst keine Transaktion in [shares] (AmountSplit.kt teilt gleichmaessig,
 * daher genuegt der erste sichtbare Anteil als Groessen-Referenz). Liefert
 * null, wenn [shares] leer ist.
 */
fun computeGroupTotal(shares: List<Transaction>, includeSelf: Boolean?): Amount? {
    val first = shares.firstOrNull() ?: return null
    val sum = shares.fold(Amount.zero(first.amountEffective.currency)) { acc, tx -> acc + tx.amountEffective }
    return if (includeSelf == true) sum + first.amountEffective else sum
}

/**
 * Vom Nutzer eingegebener Verwendungszweck einer einzelnen Peer-Transaktion -
 * fuer den Titel der Gruppen-Sammelzeile (siehe GroupTransactionRow), da
 * `info: PeerInfoShort` je Transaktionsklasse einzeln deklariert ist statt
 * ueber ein gemeinsames Interface zugaenglich zu sein.
 */
fun Transaction.groupSummary(): String? = when (this) {
    is TransactionPeerPushDebit -> info.summary
    is TransactionPeerPullDebit -> info.summary
    is TransactionPeerPullCredit -> info.summary
    is TransactionPeerPushCredit -> info.summary
    else -> null
}
