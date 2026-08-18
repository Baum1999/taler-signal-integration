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

class ReturnCallbackParamsTest {

    @Test
    fun `extracts both params when present`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123" +
            "?correlationId=corr-1&returnUri=signalfuergnu%3A%2F%2Ftaler-return"

        val result = ReturnCallbackParams.extract(uri)

        assertEquals("corr-1", result?.first)
        assertEquals("signalfuergnu://taler-return", result?.second)
    }

    @Test
    fun `returns null when no query string present`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123"

        assertNull(ReturnCallbackParams.extract(uri))
    }

    @Test
    fun `returns null when only one of the two params is present`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123?correlationId=corr-1"

        assertNull(ReturnCallbackParams.extract(uri))
    }

    @Test
    fun `stripQuery removes everything from the question mark onward`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123?correlationId=corr-1&returnUri=x"

        assertEquals(
            "taler://pay-push/exchange.demo.taler.net/ABC123",
            ReturnCallbackParams.stripQuery(uri)
        )
    }

    @Test
    fun `stripQuery is a no-op when there is no query string`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123"

        assertEquals(uri, ReturnCallbackParams.stripQuery(uri))
    }
}
