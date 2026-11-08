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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerExpiryTest {

    private val day = 24 * 60 * 60 * 1000L
    private val created = 1_000_000_000_000L

    @Test
    fun earlyInLifetimeIsNoWarning() {
        val state = PeerExpiry.state(created, created + 30 * day, created + 2 * day)!!
        assertEquals(28 * day, state.remainingMs)
        assertFalse(state.nearExpiry)
    }

    @Test
    fun lastFivePercentIsWarning() {
        // 30 Tage Laufzeit: 5 % sind 1,5 Tage.
        assertTrue(PeerExpiry.state(created, created + 30 * day, created + 29 * day)!!.nearExpiry)
        assertFalse(PeerExpiry.state(created, created + 30 * day, created + 28 * day)!!.nearExpiry)
    }

    @Test
    fun missingNeverOrPastExpirationGivesNull() {
        assertNull(PeerExpiry.state(created, null, created))
        assertNull(PeerExpiry.state(created, -1000L, created))
        assertNull(PeerExpiry.state(created, created, created))
        assertNull(PeerExpiry.state(created, created + day, created + 2 * day))
    }

    @Test
    fun remainingRoundsDownToLargestUnit() {
        assertEquals(RemainingTime(RemainingUnit.DAYS, 28), PeerExpiry.remaining(28 * day + 5 * 60 * 60 * 1000L))
        assertEquals(RemainingTime(RemainingUnit.HOURS, 23), PeerExpiry.remaining(day - 1))
        assertEquals(RemainingTime(RemainingUnit.MINUTES, 59), PeerExpiry.remaining(60 * 60 * 1000L - 1))
        assertEquals(RemainingTime(RemainingUnit.MINUTES, 1), PeerExpiry.remaining(10_000L))
    }
}
