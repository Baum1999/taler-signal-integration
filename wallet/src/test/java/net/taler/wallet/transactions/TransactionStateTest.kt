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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionStateTest {

    private fun decode(s: String): TransactionState =
        Json.decodeFromString(TransactionState.serializer(), s)

    private fun encode(state: TransactionState): String =
        Json.encodeToString(TransactionState.serializer(), state)

    @Test
    fun testUnknownStatesRoundTrip() {
        val state = decode("""{"major":"future-major","minor":"future-minor"}""")
        assertEquals(TransactionMajorState.Unknown, state.major)
        assertEquals(TransactionMinorState.Unknown, state.minor)
        assertEquals("future-major", state.majorName)
        assertEquals("future-minor", state.minorName)
        assertEquals("""{"major":"future-major","minor":"future-minor"}""", encode(state))
    }

    @Test
    fun testUnknownMinorRoundTrip() {
        val state = decode("""{"major":"pending","minor":"future-minor"}""")
        assertEquals(TransactionMajorState.Pending, state.major)
        assertEquals(TransactionMinorState.Unknown, state.minor)
        assertEquals("future-minor", state.minorName)
        assertEquals("""{"major":"pending","minor":"future-minor"}""", encode(state))
    }

    @Test
    fun testKnownStatesRoundTrip() {
        val state = decode("""{"major":"pending","minor":"bank"}""")
        assertEquals(TransactionMajorState.Pending, state.major)
        assertEquals(TransactionMinorState.Bank, state.minor)
        assertEquals("""{"major":"pending","minor":"bank"}""", encode(state))
    }

    @Test
    fun testCodeConstructedState() {
        assertEquals("""{"major":"pending"}""", encode(TransactionState(TransactionMajorState.Pending)))
        assertEquals(
            """{"major":"pending","minor":"bank"}""",
            encode(TransactionState(TransactionMajorState.Pending, TransactionMinorState.Bank)),
        )
    }

    @Test
    fun testCopyDoesNotStaleName() {
        val state = decode("""{"major":"future-major"}""").copy(major = TransactionMajorState.Done)
        assertEquals("""{"major":"done"}""", encode(state))
    }
}