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

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * In-memory fake of [SharedPreferences] - avoids pulling in Robolectric just
 * for this store, same "no Android runtime needed" spirit as
 * PendingRefundStoreTest.kt's plain-JUnit tests. Only getString/edit/
 * putString/remove/apply are exercised by SentRefundStore; everything else
 * throws if ever called, so an accidental new dependency on unimplemented
 * SharedPreferences behavior fails loudly instead of silently no-op-ing.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            pending[key] = null
            return this
        }
        override fun apply() {
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun clear() = throw NotImplementedError()
        override fun putBoolean(key: String, value: Boolean) = throw NotImplementedError()
        override fun putInt(key: String, value: Int) = throw NotImplementedError()
        override fun putLong(key: String, value: Long) = throw NotImplementedError()
        override fun putFloat(key: String, value: Float) = throw NotImplementedError()
        override fun putStringSet(key: String, values: MutableSet<String>?) = throw NotImplementedError()
    }

    override fun getAll() = throw NotImplementedError()
    override fun getInt(key: String, defValue: Int) = throw NotImplementedError()
    override fun getLong(key: String, defValue: Long) = throw NotImplementedError()
    override fun getFloat(key: String, defValue: Float) = throw NotImplementedError()
    override fun getBoolean(key: String, defValue: Boolean) = throw NotImplementedError()
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = throw NotImplementedError()
    override fun contains(key: String) = throw NotImplementedError()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw NotImplementedError()
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw NotImplementedError()
}

class SentRefundStoreTest {

    private fun store() = SentRefundStore(FakeSharedPreferences())

    @Test
    fun `entryFor returns null when nothing was ever recorded`() {
        assertNull(store().entryFor("tx-original-1"))
    }

    @Test
    fun `record then entryFor returns the stored refund transaction and timestamp`() {
        val sut = store()
        sut.record("tx-original-2", "tx-refund-2", sentAtMillis = 1234L)
        val entry = sut.entryFor("tx-original-2")
        assertEquals("tx-refund-2", entry?.refundTransactionId)
        assertEquals(1234L, entry?.sentAtMillis)
    }

    @Test
    fun `recording a second refund for the same original transaction overwrites the first`() {
        val sut = store()
        sut.record("tx-original-3", "tx-refund-3a", sentAtMillis = 1000L)
        sut.record("tx-original-3", "tx-refund-3b", sentAtMillis = 2000L)
        val entry = sut.entryFor("tx-original-3")
        assertEquals("tx-refund-3b", entry?.refundTransactionId)
        assertEquals(2000L, entry?.sentAtMillis)
    }

    @Test
    fun `entries for different original transactions do not interfere`() {
        val sut = store()
        sut.record("tx-original-4", "tx-refund-4", sentAtMillis = 1L)
        assertNull(sut.entryFor("tx-original-5"))
    }
}
