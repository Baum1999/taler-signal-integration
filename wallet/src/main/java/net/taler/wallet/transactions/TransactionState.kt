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
    @SerialName("unknown")
    Unknown,

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

    @SerialName("dialog")
    Dialog,

    @SerialName("finalizing")
    Finalizing,

    @SerialName("suspended")
    Suspended,

    @SerialName("suspended-finalizing")
    SuspendedFinalizing,

    @SerialName("suspended-aborting")
    SuspendedAborting,

    @SerialName("failed")
    Failed,

    @SerialName("expired")
    Expired,

    @SerialName("deleted")
    Deleted,
}

@Serializable
enum class TransactionMinorState {
    @SerialName("aborting-bank")
    AbortingBank,

    @SerialName("accept-refund")
    AcceptRefund,

    @SerialName("auto-refund")
    AutoRefund,

    @SerialName("balance-kyc")
    BalanceKycRequired,

    @SerialName("bank")
    Bank,

    @SerialName("bank-confirm-transfer")
    BankConfirmTransfer,

    @SerialName("bank-register-reserve")
    BankRegisterReserve,

    @SerialName("check-refund")
    CheckRefund,

    @SerialName("claim-proposal")
    ClaimProposal,

    @SerialName("completed-by-another-wallet")
    CompletedByAnotherWallet,

    @SerialName("create-purse")
    CreatePurse,

    @SerialName("delete-purse")
    DeletePurse,

    @SerialName("deposit")
    Deposit,

    @SerialName("exchange")
    Exchange,

    @SerialName("exchange-wait-reserve")
    ExchangeWaitReserve,

    @SerialName("kyc-auth")
    KycAuthRequired,

    @SerialName("kyc-init")
    KycInit,

    @SerialName("kyc")
    KycRequired,

    @SerialName("merge")
    Merge,

    @SerialName("paid-by-other")
    PaidByOther,

    @SerialName("proposed")
    Proposed,

    @SerialName("ready")
    Ready,

    @SerialName("rebind-session")
    RebindSession,

    @SerialName("refresh")
    Refresh,

    @SerialName("refused")
    Refused,

    @SerialName("repurchase")
    Repurchase,

    @SerialName("submit-payment")
    SubmitPayment,

    @SerialName("track")
    Track,

    @SerialName("unknown")
    Unknown,

    @SerialName("withdraw")
    Withdraw,

}
