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
import java.math.RoundingMode

/**
 * Rundet immer ab (RoundingMode.FLOOR), nie auf: die Summe der Anteile darf
 * den eingegebenen Gesamtbetrag nie uebersteigen. Reiner Vorschlagswert
 * (docs/API.md 2.4, Regel 4) - wird von ComposeSendScreen nur einmalig als
 * initialAmount an OutgoingPushComposable uebergeben und bleibt danach frei
 * editierbar, keine zweite Quelle der Wahrheit fuer den Betrag.
 */
fun splitAmountEvenly(total: Amount, memberCount: Int, includeSelf: Boolean): Amount {
    val divisor = if (includeSelf) memberCount else memberCount - 1
    require(divisor > 0) {
        "divisor must be positive, was $divisor (memberCount=$memberCount, includeSelf=$includeSelf)"
    }
    val perPerson = total.amountStr.toBigDecimal()
        .divide(divisor.toBigDecimal(), 8, RoundingMode.FLOOR)
    return Amount.fromString(total.currency, perPerson.toPlainString())
}
