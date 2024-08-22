/*
 * This file is part of GNU Taler
 * (C) 2023 Taler Systems S.A.
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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionState(
    val major: TransactionMajorState,
    val minor: TransactionMinorState? = null,
) {
    override fun equals(other: Any?): Boolean {
        return if (other is TransactionState)
            // if other.minor is null, then ignore minor in comparison
            major == other.major && (other.minor == null || minor == other.minor)
        else false
    }

    override fun hashCode(): Int {
        var result = major.hashCode()
        result = 31 * result + (minor?.hashCode() ?: 0)
        return result
    }
}

@Serializable
enum class TransactionMajorState {
    @SerialName("none")
    None,

    @SerialName("pending")
    Pending,

    @SerialName("done")
    Done,

    @SerialName("aborting")
    Aborting,

    @SerialName("aborted")
    Aborted,

    @SerialName("suspended")
    Suspended,

    @SerialName("dialog")
    Dialog,

    @SerialName("suspended-aborting")
    SuspendedAborting,

    @SerialName("failed")
    Failed,

    @SerialName("deleted")
    Deleted,

    @SerialName("expired")
    Expired,

    @SerialName("unknown")
    Unknown;
}

@Serializable
enum class TransactionMinorState {
    @SerialName("kyc")
    KycRequired,

    @SerialName("balance-kyc")
    BalanceKycRequired,

    @SerialName("balance-kyc-init")
    BalanceKycInit,

    @SerialName("exchange")
    Exchange,

    @SerialName("create-purse")
    CreatePurse,

    @SerialName("ready")
    Ready,

    @SerialName("bank-confirm-transfer")
    BankConfirmTransfer,

    @SerialName("exchange-wait-reserve")
    ExchangeWaitReserve,
}
