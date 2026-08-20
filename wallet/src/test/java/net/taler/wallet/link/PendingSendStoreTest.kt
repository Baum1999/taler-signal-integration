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

class PendingSendStoreTest {

    private fun request(id: String) = PrepareSendRequest(
        amount = "5.00",
        currency = "KUDOS",
        recipientHint = "Test Contact",
        purpose = "Dinner",
        correlationId = id,
        returnUri = "signalfuergnu://taler-return",
    )

    @Test
    fun `put then take returns the stored request`() {
        PendingSendStore.put(request("corr-1"))
        val result = PendingSendStore.take("corr-1")
        assertEquals("5.00", result?.amount)
        assertEquals("KUDOS", result?.currency)
    }

    @Test
    fun `take is single-use - second take returns null`() {
        PendingSendStore.put(request("corr-2"))
        PendingSendStore.take("corr-2")
        val second = PendingSendStore.take("corr-2")
        assertNull(second)
    }

    @Test
    fun `unknown correlationId returns null`() {
        assertNull(PendingSendStore.take("does-not-exist"))
    }

    @Test
    fun `expired entry (older than 15 minutes) returns null`() {
        PendingSendStore.put(request("corr-3"))
        val farFuture = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(16)
        assertNull(PendingSendStore.take("corr-3", now = farFuture))
    }
}
