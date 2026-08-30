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

import android.content.Context
import android.content.SharedPreferences

data class SentRefundEntry(val refundTransactionId: String, val sentAtMillis: Long)

/**
 * Merkt sich dauerhaft, welche eingehende Zahlung (originalTransactionId)
 * bereits per initiatePeerPushDebit rueckerstattet wurde - eigene, kleine
 * SharedPreferences-Datei nach demselben Muster wie ConsentStore.kt, aus
 * demselben Grund (keine Proto-DataStore-Schemaaenderung fuer diesen engen
 * Zweck). Anders als PendingRefundStore (TTL, single-use, keyed by
 * correlationId) hier bewusst dauerhaft und ueberschreibend (kein Verlauf,
 * nur der letzte tatsaechlich gesendete Refund zaehlt) - siehe
 * ComposeRefundScreen.kt fuer den "Verwerfen & neu erstellen"-Ablauf, der
 * einen bestehenden Eintrag ersetzt statt ihn zu loeschen.
 */
class SentRefundStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun entryFor(originalTransactionId: String): SentRefundEntry? {
        val stored = prefs.getString(originalTransactionId, null) ?: return null
        val parts = stored.split("|", limit = 2)
        if (parts.size != 2) return null
        val sentAtMillis = parts[1].toLongOrNull() ?: return null
        return SentRefundEntry(refundTransactionId = parts[0], sentAtMillis = sentAtMillis)
    }

    fun record(
        originalTransactionId: String,
        refundTransactionId: String,
        sentAtMillis: Long = System.currentTimeMillis(),
    ) {
        prefs.edit().putString(originalTransactionId, "$refundTransactionId|$sentAtMillis").apply()
    }

    companion object {
        private const val PREFS_NAME = "taler_link_sent_refunds"
    }
}
