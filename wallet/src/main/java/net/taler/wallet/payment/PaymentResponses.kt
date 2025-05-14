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

package net.taler.wallet.payment

import androidx.annotation.StringRes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.taler.common.Amount
import net.taler.common.ContractTerms
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.payment.InsufficientBalanceHint.AgeRestricted
import net.taler.wallet.payment.InsufficientBalanceHint.ExchangeMissingGlobalFees
import net.taler.wallet.payment.InsufficientBalanceHint.FeesNotCovered
import net.taler.wallet.payment.InsufficientBalanceHint.MerchantAcceptInsufficient
import net.taler.wallet.payment.InsufficientBalanceHint.MerchantDepositInsufficient
import net.taler.wallet.payment.InsufficientBalanceHint.Unknown
import net.taler.wallet.payment.InsufficientBalanceHint.WalletBalanceAvailableInsufficient
import net.taler.wallet.payment.InsufficientBalanceHint.WalletBalanceMaterialInsufficient

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
sealed class PreparePayResponse {

    @Serializable
    @SerialName("payment-possible")
    data class PaymentPossibleResponse(
        val transactionId: String,
        val amountRaw: Amount,
        val amountEffective: Amount,
        val contractTerms: ContractTerms,
    ) : PreparePayResponse() {
        fun toPayStatusPrepared() = PayStatus.Prepared(
            contractTerms = contractTerms,
            transactionId = transactionId,
            amountRaw = amountRaw,
            amountEffective = amountEffective,
        )
    }

    @Serializable
    @SerialName("insufficient-balance")
    data class InsufficientBalanceResponse(
        val transactionId: String,
        val amountRaw: Amount,
        val contractTerms: ContractTerms,
        val balanceDetails: PaymentInsufficientBalanceDetails,
    ) : PreparePayResponse()

    @Serializable
    @SerialName("already-confirmed")
    data class AlreadyConfirmedResponse(
        val transactionId: String,
        /**
         * Did the payment succeed?
         */
        val paid: Boolean,
        val amountRaw: Amount,
        val amountEffective: Amount? = null,
        val contractTerms: ContractTerms,
    ) : PreparePayResponse()
}

@Serializable
enum class InsufficientBalanceHint {
    Unknown,

    /**
     *  Merchant doesn't accept money from exchange(s) that the wallet supports.
     */
    @SerialName("merchant-accept-insufficient")
    MerchantAcceptInsufficient,

    /**
     * Merchant accepts funds from a matching exchange, but the funds can't be
     * deposited with the wire method.
     */
    @SerialName("merchant-deposit-insufficient")
    MerchantDepositInsufficient,

    /**
     * While in principle the balance is sufficient,
     * the age restriction on coins causes the spendable
     * balance to be insufficient.
     */
    @SerialName("age-restricted")
    AgeRestricted,

    /**
     * Wallet has enough available funds,
     * but the material funds are insufficient. Usually because there is a
     * pending refresh operation.
     */
    @SerialName("wallet-balance-material-insufficient")
    WalletBalanceMaterialInsufficient,

    /**
     * The wallet simply doesn't have enough available funds.
     * This is the "obvious" case of insufficient balance.
     */
    @SerialName("wallet-balance-available-insufficient")
    WalletBalanceAvailableInsufficient,

    /**
     * Exchange is missing the global fee configuration, thus fees are unknown
     * and funds from this exchange can't be used for p2p payments.
     */
    @SerialName("exchange-missing-global-fees")
    ExchangeMissingGlobalFees,

    /**
     * Even though the balance looks sufficient for the instructed amount,
     * the fees can be covered by neither the merchant nor the remaining wallet
     * balance.
     */
    @SerialName("fees-not-covered")
    FeesNotCovered;
}

@StringRes
fun InsufficientBalanceHint.stringResId(): Int? = when(this) {
    Unknown -> null
    MerchantAcceptInsufficient -> R.string.payment_balance_insufficient_hint_merchant_accept_insufficient
    MerchantDepositInsufficient -> R.string.payment_balance_insufficient_hint_merchant_deposit_insufficient
    AgeRestricted -> R.string.payment_balance_insufficient_hint_age_restricted
    WalletBalanceMaterialInsufficient -> R.string.payment_balance_insufficient_hint_wallet_balance_material_insufficient
    WalletBalanceAvailableInsufficient -> null // "normal case"
    ExchangeMissingGlobalFees -> R.string.payment_balance_insufficient_hint_exchange_missing_global_fees
    FeesNotCovered -> R.string.payment_balance_insufficient_hint_fees_not_covered
}

@Serializable
data class PaymentInsufficientBalanceDetails(
    /**
     * Amount requested by the merchant.
     */
    val amountRequested: Amount,

    /**
     * Wire method for the requested payment, only applicable
     * for merchant payments.
     */
    val wireMethod: String? = null,

    /**
     * Hint as to why the balance is insufficient.
     *
     * If this hint is not provided, the balance hints of
     * the individual exchanges should be shown, as the overall
     * reason might be a combination of the reasons for different exchanges.
     */
    val causeHint: InsufficientBalanceHint? = null,

    /**
     * Balance of type "available" (see balance.ts for definition).
     */
    val balanceAvailable: Amount,

    /**
     * Balance of type "material" (see balance.ts for definition).
     */
    val balanceMaterial: Amount,

    /**
     * Balance of type "age-acceptable" (see balance.ts for definition).
     */
    val balanceAgeAcceptable: Amount,

    /**
     * Balance of type "merchant-acceptable" (see balance.ts for definition).
     */
    val balanceReceiverAcceptable: Amount,

    /**
     * Balance of type "merchant-depositable" (see balance.ts for definition).
     */
    val balanceReceiverDepositable: Amount,

    val balanceExchangeDepositable: Amount,

    /**
     * Maximum effective amount that the wallet can spend,
     * when all fees are paid by the wallet.
     */
    val maxEffectiveSpendAmount: Amount,

    val perExchange: Map<String, PerExchange>,
) {

    @Serializable
    data class PerExchange(
        val balanceAvailable: Amount,
        val balanceMaterial: Amount,
        val balanceExchangeDepositable: Amount,
        val balanceAgeAcceptable: Amount,
        val balanceReceiverAcceptable: Amount,
        val balanceReceiverDepositable: Amount,
        val maxEffectiveSpendAmount: Amount,

        /**
         * Exchange doesn't have global fees configured for the relevant year,
         * p2p payments aren't possible.
         *
         * @deprecated (2025-02-18) use causeHint instead
         */
        val missingGlobalFees: Boolean,

        /**
         * Hint that UIs should show to explain the insufficient
         * balance.
         */
        val causeHint: InsufficientBalanceHint? = null,
    )
}

@Serializable
sealed class ConfirmPayResult {
    @Serializable
    @SerialName("done")
    data class Done(
        val transactionId: String,
        val contractTerms: ContractTerms,
    ) : ConfirmPayResult()

    @Serializable
    @SerialName("pending")
    data class Pending(
        val transactionId: String,
        val lastError: TalerErrorInfo? = null,
    ) : ConfirmPayResult()
}
