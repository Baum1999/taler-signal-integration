/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.taler.common.TalerUtils.getLocalizedString

// TODO: order v1 support

@Serializable
data class Order(
    val summary: String,
    @SerialName("summary_i18n")
    val summaryI18n: Map<String, String>? = null,
    val amount: Amount,
    @SerialName("fulfillment_url")
    val fulfillmentUrl: String? = null,
    @SerialName("fulfillment_message")
    val fulfillmentMessage: String? = null,
    @SerialName("fulfillment_message_i18n")
    val fulfillmentMessageI18n: Map <String, String>? = null,
    val products: List<ContractProduct>? = null,
    @SerialName("wire_transfer_deadline")
    val wireTransferDeadline: Timestamp? = null,
    @SerialName("refund_deadline")
    val refundDeadline: Timestamp? = null,
    @SerialName("pay_deadline")
    val payDeadline: Timestamp? = null
)

@Serializable
abstract class OrderProduct {
    abstract val productId: String?
    abstract val productName: String?
    abstract val description: String
    abstract val descriptionI18n: Map<String, String>?
    abstract val price: Amount?
    abstract val location: String?
    abstract val image: String?
    abstract val taxes: Set<Tax>?
    val localizedDescription: String
        get() = getLocalizedString(descriptionI18n, description)
}