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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import net.taler.lib.android.toRelativeTime
import net.taler.wallet.R
import net.taler.wallet.transactions.TransactionMajorState.Dialog
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.ui.theme.TalerTheme

/** Restlaufzeit einer offenen Peer-Zahlung, siehe [PeerExpiry.state]. */
data class ExpiryState(val remainingMs: Long, val nearExpiry: Boolean)

/** Einheit und Zahl fuer die Anzeige "noch N Tage/Stunden/Minuten". */
data class RemainingTime(val unit: RemainingUnit, val count: Int)

enum class RemainingUnit { DAYS, HOURS, MINUTES }

object PeerExpiry {
    /** Ab diesem Anteil der Gesamtlaufzeit wird die Restlaufzeit als Warnung gezeigt. */
    const val WARN_FRACTION = 0.05

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    /**
     * Restlaufzeit zwischen [nowMs] und [expirationMs]. `null`, wenn es kein
     * sinnvolles Ablaufdatum gibt (fehlt, "never", nicht nach dem Erstellen)
     * oder es schon erreicht ist (dann zeigt der Zustand der Transaktion das
     * Ablaufen). Warnung, sobald hoechstens [WARN_FRACTION] der Laufzeit
     * [createdMs] bis [expirationMs] uebrig ist.
     */
    fun state(createdMs: Long, expirationMs: Long?, nowMs: Long): ExpiryState? {
        if (expirationMs == null || expirationMs <= 0L || expirationMs <= createdMs) return null
        val remaining = expirationMs - nowMs
        if (remaining <= 0L) return null
        val total = expirationMs - createdMs
        return ExpiryState(remaining, remaining <= total * WARN_FRACTION)
    }

    /** Abgerundet auf ganze Tage bzw. Stunden, Minuten mindestens 1. */
    fun remaining(remainingMs: Long): RemainingTime = when {
        remainingMs >= DAY_MS -> RemainingTime(RemainingUnit.DAYS, (remainingMs / DAY_MS).toInt())
        remainingMs >= HOUR_MS -> RemainingTime(RemainingUnit.HOURS, (remainingMs / HOUR_MS).toInt())
        else -> RemainingTime(RemainingUnit.MINUTES, maxOf(1, (remainingMs / MINUTE_MS).toInt()))
    }

    /** Ablaufzeit nur fuer Peer-Zahlungen, die noch offen sind. */
    fun expirationMs(tx: Transaction): Long? {
        if (tx.txState.major != Pending && tx.txState.major != Dialog) return null
        val expiration = when (tx) {
            is TransactionPeerPushDebit -> tx.info.expiration
            is TransactionPeerPullCredit -> tx.info.expiration
            is TransactionPeerPushCredit -> tx.info.expiration
            is TransactionPeerPullDebit -> tx.info.expiration
            else -> null
        }
        return expiration?.ms
    }
}

/**
 * Zeitzeile unter einer Transaktion: "vor 5 Minuten · noch 28 Tage gueltig".
 * Der zweite Teil erscheint nur bei offenen Peer-Zahlungen und wird in den
 * letzten [PeerExpiry.WARN_FRACTION] der Laufzeit in der Warnfarbe gezeigt.
 */
@Composable
fun TransactionTimeLine(
    timestampMs: Long,
    expirationMs: Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val created = timestampMs.toRelativeTime(context).toString()
    val expiry = PeerExpiry.state(timestampMs, expirationMs, now)
    val remainingText = expiry?.let {
        val remaining = PeerExpiry.remaining(it.remainingMs)
        pluralStringResource(
            when (remaining.unit) {
                RemainingUnit.DAYS -> R.plurals.transaction_valid_days
                RemainingUnit.HOURS -> R.plurals.transaction_valid_hours
                RemainingUnit.MINUTES -> R.plurals.transaction_valid_minutes
            },
            remaining.count,
            remaining.count,
        )
    }
    val warningColor = TalerTheme.extraColors.warning
    Text(
        buildAnnotatedString {
            append(created)
            if (expiry != null && remainingText != null) {
                append(" · ")
                if (expiry.nearExpiry) {
                    withStyle(SpanStyle(color = warningColor, fontWeight = FontWeight.Bold)) {
                        append(remainingText)
                    }
                } else {
                    append(remainingText)
                }
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun TransactionTimeLine(tx: Transaction, modifier: Modifier = Modifier) {
    TransactionTimeLine(tx.timestamp.ms, PeerExpiry.expirationMs(tx), modifier)
}
