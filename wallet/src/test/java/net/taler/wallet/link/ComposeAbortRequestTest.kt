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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeAbortRequestTest {

    private val push = "taler://pay-push/exchange.example.org/AAAA"
    private val pull = "taler://pay-pull/exchange.example.org/BBBB"

    @Test
    fun duplicatesAreMergedInOrder() {
        assertEquals(listOf(push, pull), ComposeAbortRequest.distinctUris(listOf(push, pull, push, " ", "")))
    }

    @Test
    fun onlyPeerUrisAreProcessable() {
        val uris = listOf(push, "taler://withdraw/exchange.example.org/X", "https://example.org", pull)
        assertTrue(ComposeAbortRequest.isProcessable(uris, 0))
        assertFalse(ComposeAbortRequest.isProcessable(uris, 1))
        assertFalse(ComposeAbortRequest.isProcessable(uris, 2))
        assertTrue(ComposeAbortRequest.isProcessable(uris, 3))
    }

    @Test
    fun uppercaseSchemeIsProcessable() {
        assertTrue(ComposeAbortRequest.isProcessable(listOf("TALER://PAY-PUSH/exchange.example.org/AAAA"), 0))
    }

    @Test
    fun urisAboveTheLimitAreNotProcessable() {
        val uris = (0..ComposeAbortRequest.MAX_URIS).map { "$push$it" }
        assertTrue(ComposeAbortRequest.isProcessable(uris, ComposeAbortRequest.MAX_URIS - 1))
        assertFalse(ComposeAbortRequest.isProcessable(uris, ComposeAbortRequest.MAX_URIS))
    }

    @Test
    fun linkWithoutProcessableUriIsDropped() {
        assertFalse(ComposeAbortRequest.hasProcessable(emptyList()))
        assertFalse(ComposeAbortRequest.hasProcessable(listOf("taler://withdraw/exchange.example.org/X")))
    }

    @Test
    fun unknownTransactionIsNotOwnAndNeverAccepted() {
        assertEquals(AbortOutcome.NOT_OWN, ComposeAbortRequest.outcome(null, abortSucceeded = null))
    }

    @Test
    fun successfulAbortIsAborted() {
        assertEquals(AbortOutcome.ABORTED, ComposeAbortRequest.outcome(TransactionMajorState.Pending, abortSucceeded = true))
        assertEquals(AbortOutcome.ABORTED, ComposeAbortRequest.outcome(null, abortSucceeded = true))
    }

    @Test
    fun settledStatesAreMappedWithoutAbort() {
        assertEquals(AbortOutcome.ALREADY_CLAIMED, ComposeAbortRequest.outcome(TransactionMajorState.Done, null))
        assertEquals(AbortOutcome.EXPIRED, ComposeAbortRequest.outcome(TransactionMajorState.Expired, null))
        assertEquals(AbortOutcome.ABORTED, ComposeAbortRequest.outcome(TransactionMajorState.Aborted, null))
        assertTrue(ComposeAbortRequest.isSettled(TransactionMajorState.Done))
        assertFalse(ComposeAbortRequest.isSettled(TransactionMajorState.Pending))
    }

    @Test
    fun failedAbortOnOpenTransactionIsFailed() {
        assertEquals(AbortOutcome.FAILED, ComposeAbortRequest.outcome(TransactionMajorState.Pending, abortSucceeded = false))
        assertEquals(AbortOutcome.FAILED, ComposeAbortRequest.outcome(null, abortSucceeded = false))
        assertEquals(AbortOutcome.ALREADY_CLAIMED, ComposeAbortRequest.outcome(TransactionMajorState.Done, abortSucceeded = false))
    }

    @Test
    fun resultValueSeparatesUriAndOutcomeWithPipe() {
        assertEquals("$push|not_own", ComposeAbortRequest.resultValue(push, AbortOutcome.NOT_OWN))
    }

    @Test
    fun hintIsCleanedAndShortened() {
        assertEquals("Familie", ComposeAbortRequest.sanitizeHint(" Fami\u0000lie\n"))
        assertEquals(ComposeAbortRequest.MAX_RECIPIENT_HINT_LENGTH, ComposeAbortRequest.sanitizeHint("x".repeat(200))?.length)
        assertNull(ComposeAbortRequest.sanitizeHint("\u0007"))
        assertNull(ComposeAbortRequest.sanitizeHint(null))
    }
}
