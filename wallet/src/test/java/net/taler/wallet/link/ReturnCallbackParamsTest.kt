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
            "?correlationId=corr-1&returnUri=sgnl%3A%2F%2Ftaler-return"

        val result = ReturnCallbackParams.extract(uri)

        assertEquals("corr-1", result?.first)
        assertEquals("sgnl://taler-return", result?.second)
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
    fun `returns null when a param is empty`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123" +
            "?correlationId=&returnUri=sgnl%3A%2F%2Ftaler-return"

        assertNull(ReturnCallbackParams.extract(uri))
    }

    @Test
    fun `the first of duplicate params wins`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123" +
            "?correlationId=corr-1&returnUri=sgnl%3A%2F%2Ftaler-return" +
            "&correlationId=corr-2&returnUri=evil%3A%2F%2Ftaler-return"

        val result = ReturnCallbackParams.extract(uri)

        assertEquals("corr-1", result?.first)
        assertEquals("sgnl://taler-return", result?.second)
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

    @Test
    fun `contact of the chat the payment is accepted from`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123" +
            "?correlationId=corr-1&returnUri=sgnl%3A%2F%2Ftaler-return" +
            "&recipientHint=Alice%20M%C3%BCller&senderAppName=Signal&isGroup=true" +
            "&avatarUri=content%3A%2F%2Forg.example.avatar%2F42"

        val contact = ReturnCallbackParams.contact(uri)

        assertEquals("Alice Müller", contact?.displayName)
        assertEquals(true, contact?.isGroup)
        assertEquals("Signal", contact?.senderAppName)
        assertEquals("content://org.example.avatar/42", contact?.avatarUri)
        // printf '%s' 'taler://pay-push/exchange.demo.taler.net/ABC123' | shasum -a 256
        assertEquals("1d8a7666225294e584de35bd8bdef6abe97e291092efd7be201d5583a3b9feae", contact?.uriHash)
    }

    @Test
    fun `contact hash is the one of the uri without query`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123?recipientHint=Alice"

        assertEquals(
            OwnUriTracker.keyFor("taler://pay-push/exchange.demo.taler.net/ABC123"),
            ReturnCallbackParams.contact(uri)?.uriHash,
        )
    }

    @Test
    fun `no contact without a chat name`() {
        assertNull(ReturnCallbackParams.contact("taler://pay-push/exchange.demo.taler.net/ABC123"))
        assertNull(ReturnCallbackParams.contact("taler://pay-push/exchange.demo.taler.net/ABC123?recipientHint=%20"))
    }

    @Test
    fun `control characters are removed from the hints`() {
        val uri = "taler://pay-push/exchange.demo.taler.net/ABC123?recipientHint=Al%0Aice&senderAppName=%07"

        val contact = ReturnCallbackParams.contact(uri)

        assertEquals("Alice", contact?.displayName)
        assertEquals(false, contact?.isGroup)
        assertNull(contact?.senderAppName)
        assertNull(contact?.avatarUri)
    }
}
