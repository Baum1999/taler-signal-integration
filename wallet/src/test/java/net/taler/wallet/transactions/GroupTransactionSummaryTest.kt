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
import net.taler.common.Timestamp
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupTransactionSummaryTest {

    private fun share(major: TransactionMajorState, amount: String = "5.00") = TransactionPeerPushDebit(
        transactionId = "tx_$major",
        timestamp = Timestamp.fromMillis(0),
        txState = TransactionState(major),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.demo.taler.net/",
        amountRaw = Amount.fromString("KUDOS", amount),
        amountEffective = Amount.fromString("KUDOS", amount),
        scopes = listOf(Exchange(currency = "KUDOS", url = "exchange.test.taler.net")),
        info = PeerInfoShort(summary = "Pizza"),
    )

    @Test
    fun `counts done as paid, in-flight as open, terminal failures as declined`() {
        val shares = listOf(
            share(TransactionMajorState.Done),
            share(TransactionMajorState.Pending),
            share(TransactionMajorState.Aborted),
            share(TransactionMajorState.Failed),
        )
        val progress = computeGroupProgress(shares, includeSelf = null)
        assertEquals(GroupProgress(paid = 1, open = 1, declined = 2, total = 4), progress)
    }

    @Test
    fun `own share counts as paid immediately when includeSelf is true`() {
        val shares = listOf(share(TransactionMajorState.Done), share(TransactionMajorState.Pending))
        val progress = computeGroupProgress(shares, includeSelf = true)
        assertEquals(GroupProgress(paid = 2, open = 1, declined = 0, total = 3), progress)
    }

    @Test
    fun `progress reaches 100 percent once all real recipients are done`() {
        val shares = listOf(share(TransactionMajorState.Done), share(TransactionMajorState.Done))
        val progress = computeGroupProgress(shares, includeSelf = true)
        assertEquals(progress.total, progress.paid)
    }

    @Test
    fun `total sums all shares without self`() {
        val shares = listOf(share(TransactionMajorState.Done, "5.00"), share(TransactionMajorState.Done, "5.00"))
        assertEquals(Amount.fromString("KUDOS", "10.00"), computeGroupTotal(shares, includeSelf = false))
    }

    @Test
    fun `total adds one more share worth of amount when includeSelf is true`() {
        val shares = listOf(share(TransactionMajorState.Done, "5.00"), share(TransactionMajorState.Done, "5.00"))
        assertEquals(Amount.fromString("KUDOS", "15.00"), computeGroupTotal(shares, includeSelf = true))
    }

    @Test
    fun `total is null for an empty share list`() {
        assertNull(computeGroupTotal(emptyList(), includeSelf = true))
    }

    @Test
    fun `groupSummary reads the user-entered purpose text`() {
        assertEquals("Pizza", share(TransactionMajorState.Done).groupSummary())
    }
}
