/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.wallet.withdraw

import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.wallet.main.TAG
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.exchanges.ExchangeFees
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.ExchangeManager
import net.taler.wallet.transactions.TransferOption
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.withdraw.WithdrawStatus.Status.*
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionManager

sealed class TestWithdrawStatus {
    data object None : TestWithdrawStatus()
    data object Withdrawing : TestWithdrawStatus()
    data object Success : TestWithdrawStatus()
    data class Error(val message: String) : TestWithdrawStatus()
}

data class WithdrawStatus(
    val status: Status = None,

    // common details
    val talerWithdrawUri: String? = null,
    val exchangeBaseUrl: String? = null,
    val transactionId: String? = null,
    val error: TalerErrorInfo? = null,

    // received details
    val uriInfo: WithdrawalDetailsForUri? = null,
    val amountInfo: WithdrawalDetailsForAmount? = null,

    // calculated selections (based on amountInfo)
    val selectedAmount: Amount? = null,
    val selectedScope: ScopeInfo? = null,
    val selectedSpec: CurrencySpecification? = null,

    // manual transfer
    val manualTransferResponse: AcceptManualWithdrawalResponse? = null,
    val withdrawalTransfers: List<TransferData> = emptyList(),
) {
    enum class Status {
        None,
        Loading,
        Updating,
        Confirming,
        InfoReceived,
        AlreadyConfirmed,
        TosReviewRequired,
        ManualTransferRequired,
        Success,
        Error,
    }

    val isCashAcceptor get() = uriInfo != null
            && uriInfo.amount == null
            && !uriInfo.editableAmount
}

sealed class TransferData {
    abstract val subject: String
    abstract val amountRaw: Amount
    abstract val amountEffective: Amount
    abstract val transferAmount: Amount
    abstract val withdrawalAccount: WithdrawalExchangeAccountDetails
    abstract val transferOption: TransferOption

    data class Taler(
        override val subject: String,
        override val amountRaw: Amount,
        override val amountEffective: Amount,
        override val transferAmount: Amount,
        override val withdrawalAccount: WithdrawalExchangeAccountDetails,
        override val transferOption: TransferOption,
        val receiverName: String? = null,
        val account: String,
        val exchangeBaseUrl: String,
    ): TransferData()

    data class IBAN(
        override val subject: String,
        override val amountRaw: Amount,
        override val amountEffective: Amount,
        override val transferAmount: Amount,
        override val withdrawalAccount: WithdrawalExchangeAccountDetails,
        override val transferOption: TransferOption,
        val receiverName: String? = null,
        val receiverPostalCode: String? = null,
        val receiverTown: String? = null,
        val iban: String,
    ): TransferData()

    data class Cyclos(
        override val subject: String,
        override val amountRaw: Amount,
        override val amountEffective: Amount,
        override val transferAmount: Amount,
        override val withdrawalAccount: WithdrawalExchangeAccountDetails,
        override val transferOption: TransferOption,
        val host: String, // host + fpath
        val account: String, // FIXME: account ID not used at all?
        val receiverName: String, // FIXME: actual ID used for payments?
    ): TransferData()

    data class Bitcoin(
        override val subject: String,
        override val amountRaw: Amount,
        override val amountEffective: Amount,
        override val transferAmount: Amount,
        override val withdrawalAccount: WithdrawalExchangeAccountDetails,
        override val transferOption: TransferOption,
        val account: String,
        val segwitAddresses: List<String>,
    ): TransferData()
}

@Serializable
enum class WithdrawalOperationStatusFlag {
    Unknown,

    @SerialName("pending")
    Pending,

    @SerialName("selected")
    Selected,

    @SerialName("aborted")
    Aborted,

    @SerialName("confirmed")
    Confirmed,
}

@Serializable
data class PrepareBankIntegratedWithdrawalResponse(
    val transactionId: String,
    val info: WithdrawalDetailsForUri,
)

@Serializable
data class WithdrawalDetailsForUri(
    val amount: Amount? = null,
    val currency: String,
    val editableAmount: Boolean = false,
    val maxAmount: Amount? = null,
    val wireFee: Amount? = null,
    val defaultExchangeBaseUrl: String? = null,
    val possibleExchanges: List<ExchangeItem> = emptyList(),
    val status: WithdrawalOperationStatusFlag,
)

@Serializable
data class WithdrawalDetailsForAmount(
    /**
     * Amount that the user will transfer to the exchange.
     */
    val amountRaw: Amount,

    /**
     * Amount that will be added to the user's wallet balance.
     */
    val amountEffective: Amount,

    /**
     * Ways to pay the exchange, including accounts that require currency conversion.
     */
    val withdrawalAccountsList: List<WithdrawalExchangeAccountDetails>,

    /**
     * If the exchange supports age-restricted coins it will return
     * the array of ages.
     */
    val ageRestrictionOptions: List<Int>? = null,

    /**
     * Scope info of the currency withdrawn.
     */
    val scopeInfo: ScopeInfo,
)

@Serializable
data class WithdrawExchangeResponse(
    val exchangeBaseUrl: String,
    val amount: Amount? = null,
)

@Serializable
data class AcceptWithdrawalResponse(
    val transactionId: String,
)

@Serializable
data class AcceptManualWithdrawalResponse(
    val reservePub: String,
    val withdrawalAccountsList: List<WithdrawalExchangeAccountDetails>,
    val transactionId: String,
)

@Serializable
data class QrCodeSpec(
    val type: Type = Type.Unknown,
    val qrContent: String,
) {
    @Serializable
    enum class Type {
        Unknown,

        @SerialName("epc-qr")
        EpcQr,

        @SerialName("spc")
        SPC,
    }
}

class WithdrawManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
    private val exchangeManager: ExchangeManager,
    private val transactionManager: TransactionManager,
) {
    private val _withdrawStatus = MutableStateFlow(WithdrawStatus())
    val withdrawStatus: StateFlow<WithdrawStatus> = _withdrawStatus.asStateFlow()

    private val _withdrawTestStatus = MutableStateFlow<TestWithdrawStatus>(TestWithdrawStatus.None)
    val withdrawTestStatus: StateFlow<TestWithdrawStatus> = _withdrawTestStatus.asStateFlow()

    var exchangeFees: ExchangeFees? = null
        private set

    fun withdrawTestBalance() = scope.launch {
        _withdrawTestStatus.value = TestWithdrawStatus.Withdrawing
        api.request<Unit>("withdrawTestBalance") {
            put("amount", "KUDOS:10")
            put("corebankApiBaseUrl", "https://bank.demo.taler.net/")
            put("exchangeBaseUrl", "https://exchange.demo.taler.net/")
            put("useForeignAccount", true)
        }.onError {
            _withdrawTestStatus.value = TestWithdrawStatus.Error(it.userFacingMsg)
        }.onSuccess {
            _withdrawTestStatus.value = TestWithdrawStatus.Success
        }
    }

    @UiThread
    fun resetWithdrawal() {
        _withdrawStatus.value = WithdrawStatus()
    }

    @UiThread
    fun resetTestWithdrawal() {
        _withdrawTestStatus.value = TestWithdrawStatus.None
    }

    fun prepareBankIntegratedWithdrawal(
        uri: String,
        context: Context,
        loading: Boolean = true,
    ) = scope.launch {
        _withdrawStatus.update {
            WithdrawStatus(
                talerWithdrawUri = uri,
                status = if (loading) Loading else Updating,
            )
        }

        // first get URI details
        api.request(
            "prepareBankIntegratedWithdrawal",
            PrepareBankIntegratedWithdrawalResponse.serializer(),
        ) {
            put("talerWithdrawUri", uri)
        }.onError { error ->
            handleError("prepareBankIntegratedWithdrawal", error)
        }.onSuccess { details ->
            Log.d(TAG, "Withdraw details: $details")
            scope.launch {
                val tx = transactionManager.getTransactionById(details.transactionId)
                    ?: error("transaction ${details.transactionId} not found")

                val exchangeBaseUrl = details.info.defaultExchangeBaseUrl
                    ?: details.info.possibleExchanges.firstOrNull()?.exchangeBaseUrl

                // Handle no exchanges configured by bank.
                if (exchangeBaseUrl == null) {
                    _withdrawStatus.updateAndGet { value ->
                        value.copy(
                            status = Error,
                            error = TalerErrorInfo.makeCustomError(
                                context.getString(R.string.withdraw_error_empty_exchanges)),
                        )
                    }
                    return@launch
                }

                val status = _withdrawStatus.updateAndGet { value ->
                    updateSelections(
                        value.copy(
                            status = if (tx.txState.major == TransactionMajorState.Dialog) {
                                InfoReceived
                            } else {
                                AlreadyConfirmed
                            },
                            uriInfo = details.info,
                            exchangeBaseUrl = details.info.defaultExchangeBaseUrl
                                ?: details.info.possibleExchanges.firstOrNull()?.exchangeBaseUrl
                        )
                    )
                }

                // then extend with amount details (not for cash acceptor)
                if (!status.isCashAcceptor) {
                    getWithdrawalDetailsForAmount(
                        amount = details.info.amount
                            ?: Amount.zero(details.info.currency),
                        defaultExchangeBaseUrl = details.info.defaultExchangeBaseUrl,
                        loading = loading,
                    )
                }
            }
        }
    }

    fun getWithdrawalDetailsForAmount(
        amount: Amount,
        scopeInfo: ScopeInfo? = null,
        defaultExchangeBaseUrl: String? = null,
        loading: Boolean = true,
    ) = scope.launch {
        // complete exchangeBaseUrl if missing
        val exchange = scopeInfo?.let { exchangeManager.findExchange(it) }
            ?: defaultExchangeBaseUrl?.let { exchangeManager.findExchange(it) }
            ?: exchangeManager.findExchange(amount.currency)
        if (exchange != null) {
            getWithdrawalDetails(
                amount = amount,
                exchange = exchange,
                loading = loading,
            )
        }
    }

    fun getWithdrawalDetailsForExchange(
        exchangeBaseUrl: String,
        amount: Amount? = null,
        loading: Boolean = true,
    ) = scope.launch {
        // complete amount if missing
        val exchange = exchangeManager
            .findExchangeByUrl(exchangeBaseUrl)
        if (exchange != null) {
            val amount = amount
                ?: exchange.currency?.let { Amount.zero(it)}
            if (amount != null) {
                _withdrawStatus.update { value ->
                    updateSelections(value.copy(
                        exchangeBaseUrl = exchangeBaseUrl))
                }

                getWithdrawalDetails(
                    amount = amount,
                    exchange = exchange,
                    loading = loading,
                )
            }
        }
    }

    fun getWithdrawalDetails(
        amount: Amount,
        exchange: ExchangeItem,
        loading: Boolean = true,
    ) = scope.launch {
        // do not interrupt confirmation
        if (_withdrawStatus.value.status == Confirming) {
            return@launch
        }

        _withdrawStatus.update { status ->
            status.copy(status = if (loading) Loading else Updating)
        }

        api.request("getWithdrawalDetailsForAmount", WithdrawalDetailsForAmount.serializer()) {
            put("exchangeBaseUrl", exchange.exchangeBaseUrl)
            put("amount", amount.toJSONString())
        }.onError { error ->
            handleError("getWithdrawalDetailsForAmount", error)
        }.onSuccess { details ->
            scope.launch {
                _withdrawStatus.update { value ->
                    updateSelections(value.copy(
                        status = if (!exchange.tosStatus.isAccepted()) {
                            TosReviewRequired
                        } else {
                            InfoReceived
                        },
                        amountInfo = details,
                        exchangeBaseUrl = exchange.exchangeBaseUrl,
                    ))
                }
            }
        }
    }

    private suspend fun updateSelections(
        status: WithdrawStatus,
    ): WithdrawStatus {
        val selectedAmount = status.amountInfo?.amountRaw ?: status.uriInfo?.amount
        val selectedScope = status.amountInfo?.scopeInfo
            ?: status.uriInfo?.defaultExchangeBaseUrl?.let { url ->
                exchangeManager.findExchangeByUrl(url)?.scopeInfo
            }
        val selectedSpec = selectedScope?.let { scope ->
            exchangeManager.getSpecForScopeInfo(scope)
        } ?: selectedAmount?.currency?.let { currency ->
            exchangeManager.getSpecForCurrency(currency)
        }

        return status.copy(
            selectedAmount = selectedAmount,
            selectedScope = selectedScope,
            selectedSpec = selectedSpec,
        )
    }

    @UiThread
    fun prepareManualWithdrawal(uri: String) = scope.launch {
        _withdrawStatus.value = WithdrawStatus(status = Loading)
        api.request("prepareWithdrawExchange", WithdrawExchangeResponse.serializer()) {
            put("talerUri", uri)
        }.onError {
            handleError("prepareWithdrawExchange", it)
        }.onSuccess {
            getWithdrawalDetailsForExchange(
                exchangeBaseUrl = it.exchangeBaseUrl,
                amount = it.amount,
            )
        }
    }

    @UiThread
    fun refreshTosStatus(exchanges: List<ExchangeItem>) = scope.launch {
        _withdrawStatus.update { status ->
            var newStatus = status
            status.exchangeBaseUrl?.let { exchangeBaseUrl ->
                exchanges.find { it.exchangeBaseUrl == exchangeBaseUrl }?.let { exchange ->
                    if (exchange.tosStatus.isAccepted()) {
                        newStatus = status.copy(status = InfoReceived)
                    }
                }
            } ?: run {
                Log.d(TAG, "could not refresh ToS status, exchange ${status.exchangeBaseUrl} was not found")
            }
            newStatus
        }
    }

    @UiThread
    fun acceptWithdrawal(restrictAge: Int? = null) = scope.launch {
        val status = _withdrawStatus.updateAndGet { value ->
            value.copy(status = Confirming)
        }

        if (status.talerWithdrawUri == null) {
            acceptManualWithdrawal(status, restrictAge)
        } else {
            acceptBankIntegratedWithdrawal(status, restrictAge)
        }
    }

    private suspend fun acceptBankIntegratedWithdrawal(
        status: WithdrawStatus,
        restrictAge: Int? = null,
    ) {
        val exchangeBaseUrl = status.exchangeBaseUrl ?: error("no exchangeBaseUrl")
        val talerWithdrawUri = status.talerWithdrawUri ?: error("no talerWithdrawUri")
        val amountInfo = status.amountInfo

        api.request("acceptBankIntegratedWithdrawal", AcceptWithdrawalResponse.serializer()) {
            restrictAge?.let { put("restrictAge", it) }
            amountInfo?.let { put("amount", it.amountRaw.toJSONString()) }
            put("exchangeBaseUrl", exchangeBaseUrl)
            put("talerWithdrawUri", talerWithdrawUri)
        }.onError { error ->
            handleError("acceptBankIntegratedWithdrawal", error)
        }.onSuccess { response ->
            _withdrawStatus.update { value ->
                value.copy(
                    status = Success,
                    transactionId = response.transactionId,
                )
            }
        }
    }

    private suspend fun acceptManualWithdrawal(
        status: WithdrawStatus,
        restrictAge: Int? = null,
    ) {
        val exchangeBaseUrl = status.exchangeBaseUrl ?: error("no exchangeBaseUrl")
        val amountInfo = status.amountInfo ?: error("no amountInfo")

        api.request("acceptManualWithdrawal", AcceptManualWithdrawalResponse.serializer()) {
            restrictAge?.let { put("restrictAge", it) }
            put("exchangeBaseUrl", exchangeBaseUrl)
            put("amount", amountInfo.amountRaw.toJSONString())
        }.onError { error ->
            handleError("acceptManualWithdrawal", error)
        }.onSuccess { response ->
            _withdrawStatus.update { value ->
                createManualTransfer(value, response)
            }
        }
    }

    private fun handleError(operation: String, error: TalerErrorInfo) {
        Log.e(TAG, "Error $operation $error")
        _withdrawStatus.update { value ->
            value.copy(status = Error, error = error)
        }
    }

    private fun createManualTransfer(
        status: WithdrawStatus,
        response: AcceptManualWithdrawalResponse,
    ) = status.copy(
        status = ManualTransferRequired,
        manualTransferResponse = response,
        transactionId = response.transactionId,
        withdrawalTransfers = response.withdrawalAccountsList.mapNotNull {
            val details = status.amountInfo ?: return@mapNotNull null
            val opt = it.transferOptions.firstOrNull() ?: return@mapNotNull null
            it.getTransferDetails(
                amountRaw = details.amountRaw,
                amountEffective = details.amountEffective,
                transferOption = opt,
            )
        },
    )
}