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
 * correlationId -> PrepareSendRequest, TTL 15 Minuten (docs/API.md 2.7,
 * gleiches TTL-Vorbild wie TalerCorrelationStore auf Signal-Seite). In-Memory,
 * kein Persistenz-Bedarf: ueberlebt einen Prozess-Neustart nicht - ein
 * verwaister Eintrag danach ist unschaedlich. [take] ist Single-Use.
 */
object PendingSendStore {

    private data class StoredEntry(val request: PrepareSendRequest, val createdAt: Long)

    private val TTL_MS = TimeUnit.MINUTES.toMillis(15)
    private val entries = ConcurrentHashMap<String, StoredEntry>()

    fun put(request: PrepareSendRequest) {
        entries[request.correlationId] = StoredEntry(request, System.currentTimeMillis())
    }

    fun take(correlationId: String, now: Long = System.currentTimeMillis()): PrepareSendRequest? {
        val stored = entries.remove(correlationId) ?: return null
        if (now - stored.createdAt > TTL_MS) return null
        return stored.request
    }
}
