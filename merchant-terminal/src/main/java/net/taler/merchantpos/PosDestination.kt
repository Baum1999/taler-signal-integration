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

package net.taler.merchantpos

import net.taler.merchantpos.config.ConfigManager
import net.taler.merchantpos.config.InitialOrderScreen

sealed class PosDestination(
    val route: String,
) {
    data object AmountEntry : PosDestination("amount-entry")
    data object Order : PosDestination("order")
    data object History : PosDestination("history")
    data object Settings : PosDestination("settings")
    data object Config : PosDestination("config")
    data object ConfigFetcher : PosDestination("config-fetcher")
    data object ProcessPayment : PosDestination("process-payment")
    data object PaymentSuccess : PosDestination("payment-success")
    data object Refund : PosDestination("refund")
    data object RefundUri : PosDestination("refund-uri")
}

fun ConfigManager.initialDestination(): PosDestination {
    return when (initialOrderScreen) {
        InitialOrderScreen.AmountEntry -> PosDestination.AmountEntry
        InitialOrderScreen.Inventory -> PosDestination.Order
    }
}
