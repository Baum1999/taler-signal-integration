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

import net.taler.common.Amount
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AmountSplitTest {

    @Test
    fun `splits an evenly divisible amount including the sender`() {
        val total = Amount.fromString("KUDOS", "10")
        assertEquals(
            Amount.fromString("KUDOS", "2.5"),
            splitAmountEvenly(total, memberCount = 4, includeSelf = true),
        )
    }

    @Test
    fun `excluding the sender divides by memberCount minus one`() {
        val total = Amount.fromString("KUDOS", "10")
        assertEquals(
            Amount.fromString("KUDOS", "5"),
            splitAmountEvenly(total, memberCount = 3, includeSelf = false),
        )
    }

    @Test
    fun `rounds down so shares never exceed the total`() {
        val total = Amount.fromString("KUDOS", "10")
        val perPerson = splitAmountEvenly(total, memberCount = 3, includeSelf = true)
        assertEquals(Amount.fromString("KUDOS", "3.33333333"), perPerson)
    }

    @Test
    fun `dividing by one returns the original amount`() {
        val total = Amount.fromString("KUDOS", "7.5")
        assertEquals(total, splitAmountEvenly(total, memberCount = 1, includeSelf = true))
    }

    @Test
    fun `throws when the resulting divisor is not positive`() {
        val total = Amount.fromString("KUDOS", "10")
        try {
            splitAmountEvenly(total, memberCount = 1, includeSelf = false)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
