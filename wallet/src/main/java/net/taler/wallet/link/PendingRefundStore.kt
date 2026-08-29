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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * correlationId -> (PrepareRefundRequest, transactionId der bereits am
 * Aufrufzeitpunkt aufgeloesten Original-Transaktion), TTL 15 Minuten -
 * gleiches Muster wie [PendingSendStore] (eigener Store statt geteilter/
 * generischer Store, siehe dortiger Kommentar). Nur die transactionId wird
 * gemerkt, nicht Betrag/Zweck selbst - ComposeRefundScreen liest die
 * eigentlichen Werte live ueber transactionManager nach, damit es keine
 * zweite, potenziell veraltete Quelle fuer diese Zahlen gibt (eiserne Regel 4).
 * In-Memory, kein Persistenz-Bedarf, [take] ist Single-Use.
 */
object PendingRefundStore {

    data class Entry(val request: PrepareRefundRequest, val originalTransactionId: String)

    private data class StoredEntry(val entry: Entry, val createdAt: Long)

    private val TTL_MS = TimeUnit.MINUTES.toMillis(15)
    private val entries = ConcurrentHashMap<String, StoredEntry>()

    fun put(request: PrepareRefundRequest, originalTransactionId: String) {
        entries[request.correlationId] = StoredEntry(Entry(request, originalTransactionId), System.currentTimeMillis())
    }

    fun take(correlationId: String, now: Long = System.currentTimeMillis()): Entry? {
        val stored = entries.remove(correlationId) ?: return null
        if (now - stored.createdAt > TTL_MS) return null
        return stored.entry
    }
}
