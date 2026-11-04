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

import android.content.SharedPreferences
import net.taler.common.Amount
import net.taler.common.Timestamp
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.peer.GroupShareInfo
import net.taler.wallet.peer.GroupShareStore
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory fake of [SharedPreferences], nur was GroupShareStore benutzt. */
private class FakeGroupSharePrefs : SharedPreferences {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value!!
            return this
        }
        override fun apply() {
            values += pending
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun remove(key: String) = throw NotImplementedError()
        override fun clear() = throw NotImplementedError()
        override fun putBoolean(key: String, value: Boolean) = throw NotImplementedError()
        override fun putInt(key: String, value: Int) = throw NotImplementedError()
        override fun putLong(key: String, value: Long) = throw NotImplementedError()
        override fun putFloat(key: String, value: Float) = throw NotImplementedError()
        override fun putStringSet(key: String, values: MutableSet<String>?) = throw NotImplementedError()
    }

    override fun getAll() = throw NotImplementedError()
    override fun contains(key: String) = throw NotImplementedError()
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = throw NotImplementedError()
    override fun getInt(key: String, defValue: Int) = throw NotImplementedError()
    override fun getLong(key: String, defValue: Long) = throw NotImplementedError()
    override fun getFloat(key: String, defValue: Float) = throw NotImplementedError()
    override fun getBoolean(key: String, defValue: Boolean) = throw NotImplementedError()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw NotImplementedError()
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw NotImplementedError()
}

class RecentTransactionEntriesTest {

    private val store = GroupShareStore(FakeGroupSharePrefs())

    private fun tx(id: String) = TransactionPeerPushDebit(
        transactionId = id,
        timestamp = Timestamp.fromMillis(0),
        txState = TransactionState(TransactionMajorState.Pending),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.demo.taler.net/",
        amountRaw = Amount.fromString("KUDOS", "5.00"),
        amountEffective = Amount.fromString("KUDOS", "5.00"),
        scopes = listOf(Exchange(currency = "KUDOS", url = "exchange.test.taler.net")),
        info = PeerInfoShort(summary = "Pizza"),
    )

    private fun groupShares(groupId: String, vararg ids: String): List<Transaction> =
        ids.mapIndexed { index, id ->
            store.save(id, GroupShareInfo(groupId, index, ids.size, includeSelf = true))
            tx(id)
        }

    @Test
    fun `shares of a group split collapse into one row`() {
        val shares = groupShares("g1", "a", "b")
        val older = tx("older")

        val entries = recentTransactionEntries(shares + older, store)

        assertEquals(
            listOf(
                TransactionListEntry.Group("g1", shares, includeSelf = true),
                TransactionListEntry.Single(older),
            ),
            entries,
        )
    }

    @Test
    fun `row limit counts rows, not transactions`() {
        val shares = groupShares("g1", "a", "b", "c")
        val singles = (1..5).map { tx("s$it") }

        val entries = recentTransactionEntries(shares + singles, store)

        assertEquals(
            listOf(
                TransactionListEntry.Group("g1", shares, includeSelf = true),
                TransactionListEntry.Single(singles[0]),
                TransactionListEntry.Single(singles[1]),
                TransactionListEntry.Single(singles[2]),
            ),
            entries,
        )
    }

    @Test
    fun `group with more shares than rows stays complete`() {
        val shares = groupShares("g1", "a", "b", "c", "d", "e", "f")

        val entries = recentTransactionEntries(shares, store)

        assertEquals(listOf(TransactionListEntry.Group("g1", shares, includeSelf = true)), entries)
    }
}
