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

import net.taler.wallet.link.OwnUriTracker
import net.taler.wallet.link.ReturnIntentSender
import org.junit.Assert.assertEquals
import org.junit.Test

class MessengerUriHashesTest {

    private val uriA = "taler://pay-push/exchange.demo.taler.net/ABC123"
    private val uriB = "taler://pay-push/exchange.demo.taler.net/XYZ789"

    // printf '%s' 'taler://pay-push/exchange.demo.taler.net/ABC123' | shasum -a 256
    private val hashA = "1d8a7666225294e584de35bd8bdef6abe97e291092efd7be201d5583a3b9feae"

    private val noHashes: (String) -> List<String> = { emptyList() }

    @Test
    fun `open payment is found by the hash of its uri`() {
        assertEquals(listOf(hashA), messengerUriHashes(listOf("tx1" to uriA), noHashes))
    }

    @Test
    fun `accepted payment without uri is found by the tracked hash`() {
        // wallet-core liefert talerUri bei pay-push nur im Zustand "ready".
        val tracked = mapOf("tx1" to listOf(hashA))

        assertEquals(listOf(hashA), messengerUriHashes(listOf("tx1" to null)) { tracked[it].orEmpty() })
    }

    @Test
    fun `uri and tracked hash of the same payment count once`() {
        val tracked = mapOf("tx1" to listOf(hashA))

        assertEquals(listOf(hashA), messengerUriHashes(listOf("tx1" to uriA)) { tracked[it].orEmpty() })
    }

    @Test
    fun `group shares keep their order`() {
        val hashes = messengerUriHashes(listOf("tx1" to uriA, "tx2" to uriB), noHashes)

        assertEquals(listOf(hashA, OwnUriTracker.keyFor(uriB)), hashes)
    }

    @Test
    fun `payment without uri and without tracked hash cannot be opened`() {
        assertEquals(emptyList<String>(), messengerUriHashes(listOf("tx1" to null, "tx2" to " "), noHashes))
    }

    @Test
    fun `at most the link limit is sent`() {
        val payments = (0 until ReturnIntentSender.MAX_URI_HASHES + 3).map { "tx$it" to "$uriA$it" }

        assertEquals(ReturnIntentSender.MAX_URI_HASHES, messengerUriHashes(payments, noHashes).size)
    }
}
