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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class PendingRefundStoreTest {

    private fun request(id: String) = PrepareRefundRequest(
        originalUri = "taler://pay-push/host/original",
        correlationId = id,
        returnUri = "signalfuergnu://taler-return",
    )

    @Test
    fun `put then take returns the stored request and transactionId`() {
        PendingRefundStore.put(request("corr-1"), "tx-1")
        val result = PendingRefundStore.take("corr-1")
        assertEquals("taler://pay-push/host/original", result?.request?.originalUri)
        assertEquals("tx-1", result?.originalTransactionId)
    }

    @Test
    fun `take is single-use - second take returns null`() {
        PendingRefundStore.put(request("corr-2"), "tx-2")
        PendingRefundStore.take("corr-2")
        val second = PendingRefundStore.take("corr-2")
        assertNull(second)
    }

    @Test
    fun `unknown correlationId returns null`() {
        assertNull(PendingRefundStore.take("does-not-exist"))
    }

    @Test
    fun `expired entry (older than 15 minutes) returns null`() {
        PendingRefundStore.put(request("corr-3"), "tx-3")
        val farFuture = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(16)
        assertNull(PendingRefundStore.take("corr-3", now = farFuture))
    }
}
