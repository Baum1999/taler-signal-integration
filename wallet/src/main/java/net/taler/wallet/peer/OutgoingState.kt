/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.peer

import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.exchanges.ExchangeTosStatus

sealed class OutgoingState

data object OutgoingIntro : OutgoingState()

data object OutgoingChecking : OutgoingState()

data class OutgoingChecked(
    val amountRaw: Amount,
    val amountEffective: Amount,
    val exchangeBaseUrl: String,
    val tosStatus: ExchangeTosStatus?,
) : OutgoingState()

data object OutgoingCreating : OutgoingState()

data class OutgoingResponse(
    val transactionId: String,
) : OutgoingState()

data class OutgoingError(
    val info: TalerErrorInfo,
) : OutgoingState()

@Serializable
data class CheckPeerPullCreditResponse(
    val exchangeBaseUrl: String,
    val amountRaw: Amount,
    val amountEffective: Amount,
)

@Serializable
data class CheckPeerPullCreditResult(
    val exchangeBaseUrl: String,
    val amountRaw: Amount,
    val amountEffective: Amount,
    val tosStatus: ExchangeTosStatus?,
)

@Serializable
data class InitiatePeerPullPaymentResponse(
    val transactionId: String,
)

@Serializable
data class CheckPeerPushDebitResponse(
    val amountRaw: Amount,
    val amountEffective: Amount,
    val exchangeBaseUrl: String,
)

@Serializable
data class InitiatePeerPushDebitResponse(
    val exchangeBaseUrl: String,
    val transactionId: String,
)
