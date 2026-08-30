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
 * this program; if not, see <http://www.gnu.org/licenses/>.
 */

package net.taler.merchantpos.history

import net.taler.common.Amount
import net.taler.common.Timestamp
import net.taler.merchantlib.OrderHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPaginationTest {

    @Test
    fun `short intermediate page keeps pagination open`() {
        val pagination = HistoryPagination()
        pagination.addPage((100L downTo 81L).map(::entry), reset = true)

        val shortPage = pagination.addPage((81L downTo 75L).map(::entry), reset = false)

        assertEquals((80L downTo 75L).toList(), shortPage.map { it.rowId })
        assertEquals(75L, pagination.nextOffset)
        assertFalse(pagination.reachedEnd)

        val followingPage = pagination.addPage((75L downTo 56L).map(::entry), reset = false)

        assertEquals((74L downTo 56L).toList(), followingPage.map { it.rowId })
        assertEquals((100L downTo 56L).toList(), pagination.items.map { it.rowId })
        assertFalse(pagination.reachedEnd)
    }

    @Test
    fun `next page de-duplicates an inclusive cursor and advances`() {
        val pagination = HistoryPagination()
        pagination.addPage((100L downTo 81L).map(::entry), reset = true)

        val added = pagination.addPage((81L downTo 62L).map(::entry), reset = false)

        assertEquals((80L downTo 62L).toList(), added.map { it.rowId })
        assertEquals((100L downTo 62L).toList(), pagination.items.map { it.rowId })
        assertEquals(62L, pagination.nextOffset)
        assertFalse(pagination.reachedEnd)
    }

    @Test
    fun `page containing only the cursor marks the end`() {
        val pagination = HistoryPagination()
        pagination.addPage(listOf(entry(2), entry(1)), reset = true)

        val added = pagination.addPage(listOf(entry(1)), reset = false)

        assertTrue(added.isEmpty())
        assertTrue(pagination.reachedEnd)
    }

    @Test
    fun `empty page marks the end`() {
        val pagination = HistoryPagination()
        pagination.addPage(listOf(entry(1)), reset = true)

        pagination.addPage(emptyList(), reset = false)

        assertTrue(pagination.reachedEnd)
    }

    private fun entry(rowId: Long) = OrderHistoryEntry(
        orderId = "order-$rowId",
        rowId = rowId,
        timestamp = Timestamp.fromMillis(rowId * 1_000),
        amount = Amount("KUDOS", rowId, 0),
        summary = "Order $rowId",
        paid = false,
        refundable = false,
    )
}
