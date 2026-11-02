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

class MessengerLinkParamsTest {

    @Test
    fun `pay-push action link becomes a taler uri with its query intact`() {
        val link = "talerlink://pay-push/exchange.example.org/7Q4XK2" +
            "?correlationId=corr-1&returnUri=sgnl%3A%2F%2Ftaler-return"

        assertEquals(
            "taler://pay-push/exchange.example.org/7Q4XK2" +
                "?correlationId=corr-1&returnUri=sgnl%3A%2F%2Ftaler-return",
            MessengerLinkParams.peerActionTalerUri(link),
        )
    }

    @Test
    fun `pay-pull action link becomes a taler uri`() {
        assertEquals(
            "taler://pay-pull/exchange.example.org/AAAA",
            MessengerLinkParams.peerActionTalerUri("talerlink://pay-pull/exchange.example.org/AAAA"),
        )
    }

    @Test
    fun `scheme is case-insensitive`() {
        assertEquals(
            "taler://pay-push/exchange.example.org/7Q4XK2",
            MessengerLinkParams.peerActionTalerUri("TALERLINK://pay-push/exchange.example.org/7Q4XK2"),
        )
    }

    @Test
    fun `compose links and other hosts are not action links`() {
        assertNull(MessengerLinkParams.peerActionTalerUri("talerlink://compose-send?correlationId=x"))
        assertNull(MessengerLinkParams.peerActionTalerUri("talerlink://withdraw/exchange.example.org/A"))
        assertNull(MessengerLinkParams.peerActionTalerUri("talerlink://pay-push-evil/exchange.example.org/A"))
        assertNull(MessengerLinkParams.peerActionTalerUri("talerlink://pay-push?correlationId=x"))
        assertNull(MessengerLinkParams.peerActionTalerUri("taler://pay-push/exchange.example.org/A"))
        assertNull(MessengerLinkParams.peerActionTalerUri("not a link"))
    }

    @Test
    fun `direction allows only pay-push and pay-pull`() {
        assertEquals("PAY_PULL", MessengerLinkParams.composeDirection("PAY_PULL"))
        assertEquals("PAY_PUSH", MessengerLinkParams.composeDirection("PAY_PUSH"))
        assertEquals("PAY_PUSH", MessengerLinkParams.composeDirection("WITHDRAW"))
        assertEquals("PAY_PUSH", MessengerLinkParams.composeDirection("REFUND"))
        assertEquals("PAY_PUSH", MessengerLinkParams.composeDirection("PAY"))
        assertEquals("PAY_PUSH", MessengerLinkParams.composeDirection(null))
    }

    @Test
    fun `empty required values count as missing`() {
        assertEquals("corr-1", MessengerLinkParams.required("corr-1"))
        assertNull(MessengerLinkParams.required(""))
        assertNull(MessengerLinkParams.required("  "))
        assertNull(MessengerLinkParams.required(null))
    }
}
