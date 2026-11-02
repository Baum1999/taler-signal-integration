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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory fake of [SharedPreferences], wie in SentRefundStoreTest.kt, hier
 * zusaetzlich mit getAll/contains, weil OwnUriTracker beides benutzt.
 */
private class FakeOwnUriPrefs(initial: Map<String, String> = emptyMap()) : SharedPreferences {
    val values = initial.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue
    override fun getAll(): Map<String, *> = values.toMap()
    override fun contains(key: String) = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableListOf<Pair<String, String?>>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending += key to value
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            pending += key to null
            return this
        }
        override fun apply() {
            // Wie Android: Entfernungen vor den Schreibvorgaengen anwenden.
            pending.filter { it.second == null }.forEach { values.remove(it.first) }
            pending.forEach { (key, value) -> if (value != null) values[key] = value }
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

    override fun getInt(key: String, defValue: Int) = throw NotImplementedError()
    override fun getLong(key: String, defValue: Long) = throw NotImplementedError()
    override fun getFloat(key: String, defValue: Float) = throw NotImplementedError()
    override fun getBoolean(key: String, defValue: Boolean) = throw NotImplementedError()
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = throw NotImplementedError()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw NotImplementedError()
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw NotImplementedError()
}

class OwnUriTrackerTest {

    private val uri = "taler://pay-push/exchange.demo.taler.net/ABC123"

    @Test
    fun `tracked uri is found again`() {
        val tracker = OwnUriTracker(FakeOwnUriPrefs())

        tracker.track(uri, "txn:peer-push-debit:1")

        assertTrue(tracker.isOwn(uri))
        assertEquals("txn:peer-push-debit:1", tracker.transactionIdFor(uri))
    }

    @Test
    fun `unknown uri is not own`() {
        val tracker = OwnUriTracker(FakeOwnUriPrefs())

        assertFalse(tracker.isOwn(uri))
        assertNull(tracker.transactionIdFor(uri))
    }

    @Test
    fun `the uri itself is never stored`() {
        val prefs = FakeOwnUriPrefs()

        OwnUriTracker(prefs).track(uri, "txn:peer-push-debit:1")

        assertEquals(setOf(OwnUriTracker.keyFor(uri)), prefs.values.keys)
        assertFalse(prefs.values.keys.any { it.contains("ABC123") })
    }

    @Test
    fun `key is the lowercase sha-256 hex of the utf-8 uri like on iOS`() {
        // printf 'abc' | shasum -a 256
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            OwnUriTracker.keyFor("abc"),
        )
    }

    @Test
    fun `plain-text entries of older versions are migrated to hashed keys`() {
        val prefs = FakeOwnUriPrefs(mapOf(uri to "txn:peer-push-debit:1"))

        val tracker = OwnUriTracker(prefs)

        assertEquals(setOf(OwnUriTracker.keyFor(uri)), prefs.values.keys)
        assertEquals("txn:peer-push-debit:1", tracker.transactionIdFor(uri))
    }

    @Test
    fun `already hashed entries are left alone`() {
        val key = OwnUriTracker.keyFor(uri)
        val prefs = FakeOwnUriPrefs(mapOf(key to "txn:peer-push-debit:1"))

        OwnUriTracker(prefs)

        assertEquals(mapOf(key to "txn:peer-push-debit:1"), prefs.values)
    }
}
