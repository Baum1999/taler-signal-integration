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

package net.taler.wallet

import kotlinx.serialization.Serializable

@Serializable
sealed interface WalletDestination {
    @Serializable
    data object Main : WalletDestination

    @Serializable
    data class HandleUri(val uri: String) : WalletDestination

    @Serializable
    data class PaytoUri(val uri: String) : WalletDestination

    @Serializable
    data class PromptWithdraw(
        val withdrawUri: String? = null,
        val withdrawExchangeUri: String? = null,
        val exchangeBaseUrl: String? = null,
        val amount: String? = null,
        val editableCurrency: Boolean = true
    ) : WalletDestination

    @Serializable
    data object ExchangeList : WalletDestination

    @Serializable
    data class BankAccounts(val currency: String? = null) : WalletDestination

    @Serializable
    data class AddBankAccount(val bankAccountId: String? = null) : WalletDestination

    @Serializable
    data object DiscountList : WalletDestination

    @Serializable
    data object PassList : WalletDestination

    @Serializable
    data class DonauStatement(val host: String) : WalletDestination

    @Serializable
    data class SetDonau(val donauBaseUrl: String? = null) : WalletDestination

    @Serializable
    data object OutgoingPush : WalletDestination

    @Serializable
    data object OutgoingPull : WalletDestination

    @Serializable
    data class Deposit(
        val amount: String? = null,
        val receiverName: String? = null,
        val receiverPostalCode: String? = null,
        val receiverTown: String? = null,
        val IBAN: String? = null
    ) : WalletDestination

    @Serializable
    data object PromptPullPayment : WalletDestination

    @Serializable
    data object PromptPushPayment : WalletDestination

    @Serializable
    data class PromptPayTemplate(val uri: String) : WalletDestination

    @Serializable
    data class WireTransferDetails(
        val showQrCodes: Boolean,
    ) : WalletDestination

    @Serializable
    data class ReviewExchangeTOS(
        val exchangeBaseUrl: String,
        val readOnly: Boolean = false
    ) : WalletDestination

    @Serializable
    data object ExchangeShopping : WalletDestination

    @Serializable
    data object PerformanceStats : WalletDestination

    // Transaction Details
    @Serializable
    data object TransactionWithdrawal : WalletDestination
    @Serializable
    data object TransactionPayment : WalletDestination
    @Serializable
    data object TransactionRefund : WalletDestination
    @Serializable
    data object TransactionRefresh : WalletDestination
    @Serializable
    data object TransactionDeposit : WalletDestination
    @Serializable
    data object TransactionPeer : WalletDestination
    @Serializable
    data object TransactionLoss : WalletDestination
    @Serializable
    data object TransactionDummy : WalletDestination
}
