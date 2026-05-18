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

package net.taler.merchantlib

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import net.taler.common.Amount
import net.taler.common.Timestamp

@Serializable
data class OrderHistory(
    val orders: List<OrderHistoryEntry>
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OrderHistoryEntry(
    // order ID of the transaction related to this entry.
    @SerialName("order_id")
    val orderId: String,

    // row ID of the order in the database
    @SerialName("row_id")
    val rowId: Long? = null,

    // when the order was created
    val timestamp: Timestamp,

    // the amount of money the order is for
    val amount: Amount,

    // the summary of the order
    val summary: String,

    // if the order has been paid
    val paid: Boolean,

    // whether some part of the order is refundable
    val refundable: Boolean,

    // whether the order has already been refunded
    val refunded: Boolean = false,

    // total refunded amount approved for this order
    @JsonNames("refund_amount", "refunded_amount")
    val refundAmount: Amount? = null,

    // portion of refund amount not yet obtained by the wallet
    @JsonNames("pending_refund_amount", "refund_pending_amount")
    val pendingRefundAmount: Amount? = null,

    // whether the backend still reports wallet pickup as pending
    val refundPending: Boolean = false,
) {
    val hasRefund: Boolean
        get() = refunded || refundAmount?.isZero() == false

    val hasPendingRefund: Boolean
        get() = refundPending || pendingRefundAmount?.isZero() == false
}
