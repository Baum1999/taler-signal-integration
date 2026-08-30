/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
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

package net.taler.merchantpos.payment

import net.taler.merchantpos.order.Order

data class Payment(
    val order: Order,
    val summary: String,
    val currency: String,
    val orderId: String? = null,
    val talerPayUri: String? = null,
    val insecureTalerPayUri: String? = null,
    val httpsUpgradeAccepted: Boolean = false,
    val insecureUriAccepted: Boolean = false,
    val claimed: Boolean = false,
    val paid: Boolean = false,
    val error: String? = null
)

private const val INSECURE_TALER_SCHEME = "taler+http://"

internal fun upgradeTalerPayUriToHttps(uri: String): String? {
    if (!uri.startsWith(INSECURE_TALER_SCHEME, ignoreCase = true)) return null
    return "taler://${uri.substring(INSECURE_TALER_SCHEME.length)}"
}
