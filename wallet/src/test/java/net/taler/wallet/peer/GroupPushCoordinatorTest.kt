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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deckt nur die reinen Aggregations-Helfer ab (kein Netzwerk/api-Mock -
 * PeerManager.initiatePeerPushDebitGroup selbst haengt an WalletBackendApi,
 * das in diesem Codebase noch keinen Mock-Seam hat, siehe
 * PROMPT_parallel_group_split.md Meilenstein 1 - nur kompiliert, nicht
 * unit-getestet).
 */
class GroupPushCoordinatorTest {

    private val error = TalerErrorInfo.makeCustomError("boom")

    @Test
    fun `successfulUris returns only successes in order`() {
        val results = listOf(
            ShareResult.Success("txn:1", "taler://pay-push/ex/AAA"),
            ShareResult.Failure("txn:2", error),
            ShareResult.Success("txn:3", "taler://pay-push/ex/CCC"),
        )
        assertEquals(
            listOf("taler://pay-push/ex/AAA", "taler://pay-push/ex/CCC"),
            results.successfulUris(),
        )
    }

    @Test
    fun `allSucceeded is true only when every share succeeded`() {
        val allGood = listOf(
            ShareResult.Success("txn:1", "taler://pay-push/ex/AAA"),
            ShareResult.Success("txn:2", "taler://pay-push/ex/BBB"),
        )
        assertTrue(allGood.allSucceeded())

        val oneBad = allGood + ShareResult.Failure("txn:3", error)
        assertFalse(oneBad.allSucceeded())
    }

    @Test
    fun `allSucceeded is false for an empty result list`() {
        assertFalse(emptyList<ShareResult>().allSucceeded())
    }

    @Test
    fun `failures returns only the failed shares`() {
        val results = listOf(
            ShareResult.Success("txn:1", "taler://pay-push/ex/AAA"),
            ShareResult.Failure(null, error),
            ShareResult.Failure("txn:3", error),
        )
        assertEquals(2, results.failures().size)
        assertEquals(null, results.failures()[0].transactionId)
        assertEquals("txn:3", results.failures()[1].transactionId)
    }
}
