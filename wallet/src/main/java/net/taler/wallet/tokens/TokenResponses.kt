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

package net.taler.wallet.tokens

import kotlinx.serialization.Serializable
import net.taler.common.Merchant
import net.taler.common.Timestamp

@Serializable
data class DiscountListDetail(
    val tokenFamilyHash: String,
    val tokenIssuePubHash: String,
    val merchantBaseUrl: String,
    val merchantInfo: Merchant? = null,
    val name: String,
    val description: String,
    val descriptionI18n: Map<String, String>,
    val validityStart: Timestamp,
    val validityEnd: Timestamp,
    val tokensAvailable: Int,
) {
    val isActive: Boolean get() {
        val now = Timestamp.now()
        return now in validityStart..validityEnd
    }

    val isExpired: Boolean get() {
        val now = Timestamp.now()
        return now >= validityEnd
    }
}

@Serializable
data class ListDiscountsResponse(
    val discounts: List<DiscountListDetail>,
)

@Serializable
data class SubscriptionListDetail(
    val tokenFamilyHash: String,
    val tokenIssuePubHash: String,
    val merchantBaseUrl: String,
    val merchantInfo: Merchant? = null,
    val name: String,
    val description: String,
    val descriptionI18n: Map<String, String>,
    val validityStart: Timestamp,
    val validityEnd: Timestamp,
) {
    val isActive: Boolean get() {
        val now = Timestamp.now()
        return now in validityStart..validityEnd
    }

    val isExpired: Boolean get() {
        val now = Timestamp.now()
        return now >= validityEnd
    }
}

@Serializable
data class ListSubscriptionsResponse(
    val subscriptions: List<SubscriptionListDetail>,
)