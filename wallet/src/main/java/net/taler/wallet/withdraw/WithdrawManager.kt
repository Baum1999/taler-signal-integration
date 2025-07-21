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

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.common.Bech32
import net.taler.wallet.TAG
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.exchanges.ExchangeFees
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.ExchangeManager
import net.taler.wallet.exchanges.ExchangeTosStatus
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.withdraw.WithdrawStatus.Status.*
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking
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
    val currency: String? = null,
    val scopeInfo: ScopeInfo? = null,
    val uriInfo: WithdrawalDetailsForUri? = null,
    val amountInfo: WithdrawalDetailsForAmount? = null,

    // manual transfer
    val manualTransferResponse: AcceptManualWithdrawalResponse? = null,
    val withdrawalTransfers: List<TransferData> = emptyList(),
) {
    enum class Status {
        None,
        Loading,
        Updating,
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

    val currency get() = withdrawalAccount.transferAmount?.currency

    data class Taler(
        override val subject: String,
        override val amountRaw: Amount,
        override val amountEffective: Amount,
        override val transferAmount: Amount,
        override val withdrawalAccount: WithdrawalExchangeAccountDetails,
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
        val receiverName: String? = null,
        val receiverPostalCode: String? = null,
        val receiverTown: String? = null,
        val iban: String,
    ): TransferData()

    data class Bitcoin(
        override val subject: String,
        override val amountRaw: Amount,
        override val amountEffective: Amount,
        override val transferAmount: Amount,
        override val withdrawalAccount: WithdrawalExchangeAccountDetails,
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
     * Did the user accept the current version of the exchange's
     * terms of service?
     *
     * @deprecated the client should query the exchange entry instead
     */
    val tosAccepted: Boolean,

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
data class GetQrCodesForPaytoResponse(
    val codes: List<QrCodeSpec>,
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

    val qrCodes = MutableLiveData<List<QrCodeSpec>>()

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
                val status = _withdrawStatus.updateAndGet { value ->
                    value.copy(
                        status = if (tx.txState.major == TransactionMajorState.Dialog) {
                            InfoReceived
                        } else {
                            AlreadyConfirmed
                        },
                        uriInfo = details.info,
                        currency = details.info.currency,
                        exchangeBaseUrl = details.info.defaultExchangeBaseUrl,
                        transactionId = details.transactionId,
                    )
                }

                // then extend with amount details (not for cash acceptor)
                if (!status.isCashAcceptor) {
                    getWithdrawalDetails(
                        amount = details.info.amount,
                        exchangeBaseUrl = details.info.defaultExchangeBaseUrl,
                        loading = loading,
                    )
                }
            }
        }
    }

    fun getWithdrawalDetails(
        amount: Amount? = null,
        scopeInfo: ScopeInfo? = null,
        exchangeBaseUrl: String? = null,
        loading: Boolean = true,
    ) = scope.launch {
        val status = _withdrawStatus.getAndUpdate { value ->
            value.copy(status = if (loading) Loading else Updating)
        }

        val ex: ExchangeItem
        val am: Amount?

        if (amount != null && (scopeInfo != null || exchangeBaseUrl != null)) {
            // 1. caller sets both parameters
            ex = exchangeBaseUrl?.let { exchangeManager.findExchangeByUrl(it) }
                ?: scopeInfo?.let { exchangeManager.findExchange(it) }
                ?: error("could not resolve exchange")
            am = ex.currency?.let { amount.copy(currency = it) }
                ?: error("could not resolve currency")
        } else if (amount != null) {
            // 2. caller only provides amount
            //   => amount is updated
            //   => exchange URL is kept
            ex = status.exchangeBaseUrl?.let { exchangeManager.findExchangeByUrl(it) }
                ?: status.scopeInfo?.let { exchangeManager.findExchange(it) }
                ?: exchangeManager.findExchange(amount.currency)
                ?: error("could not resolve exchange")
            am = amount
        } else if (exchangeBaseUrl != null) {
            // 3. caller only provides exchange URL
            ex = exchangeManager.findExchangeByUrl(exchangeBaseUrl)
                ?: error("could not resolve exchange")
            am = status.amountInfo?.amountRaw
                ?: ex.currency?.let { Amount.zero(ex.currency) }
                ?: error("could not resolve currency")
        } else if (scopeInfo != null) {
            // 3. caller only provides scope
            ex = exchangeManager.findExchange(scopeInfo)
                ?: error("could not resolve exchange")
            am = status.amountInfo?.amountRaw
                ?: ex.currency?.let { Amount.zero(ex.currency) }
                ?: error("could not resolve currency")
        } else {
            error("no parameters specified")
        }

        api.request("getWithdrawalDetailsForAmount", WithdrawalDetailsForAmount.serializer()) {
            put("exchangeBaseUrl", ex.exchangeBaseUrl)
            put("amount", am.toJSONString())
        }.onError { error ->
            handleError("getWithdrawalDetailsForAmount", error)
        }.onSuccess { details ->
            scope.launch {
                _withdrawStatus.update { value ->
                    value.copy(
                        status = if (ex.tosStatus != ExchangeTosStatus.Accepted) {
                            TosReviewRequired
                        } else {
                            InfoReceived
                        },
                        exchangeBaseUrl = ex.exchangeBaseUrl,
                        amountInfo = details,
                        currency = details.amountRaw.currency,
                        scopeInfo = details.scopeInfo,
                    )
                }
            }
        }
    }

    @UiThread
    fun prepareManualWithdrawal(uri: String) = scope.launch {
        _withdrawStatus.value = WithdrawStatus(status = Loading)
        api.request("prepareWithdrawExchange", WithdrawExchangeResponse.serializer()) {
            put("talerUri", uri)
        }.onError {
            handleError("prepareWithdrawExchange", it)
        }.onSuccess {
            getWithdrawalDetails(
                amount = it.amount,
                exchangeBaseUrl = it.exchangeBaseUrl,
            )
        }
    }

    @UiThread
    fun refreshTosStatus(exchanges: List<ExchangeItem>) = scope.launch {
        _withdrawStatus.update { status ->
            var newStatus = status
            status.exchangeBaseUrl?.let { exchangeBaseUrl ->
                exchanges.find { it.exchangeBaseUrl == exchangeBaseUrl }?.let { exchange ->
                    if (exchange.tosStatus == ExchangeTosStatus.Accepted) {
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
            value.copy(status = Loading)
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

    fun getQrCodesForPayto(uri: String): List<QrCodeSpec> = runBlocking {
        var codes = emptyList<QrCodeSpec>()
        api.request("getQrCodesForPayto", GetQrCodesForPaytoResponse.serializer()) {
            put("paytoUri", uri)
        }.onError { error ->
            handleError("getQrCodesForPayto", error)
        }.onSuccess { response ->
            codes = response.codes
        }

        return@runBlocking codes
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
            val details = status.amountInfo ?: error("no amountInfo")
            val uri = it.paytoUri.toUri()
            if ("bitcoin".equals(uri.authority, true)) {
                val msg = uri.getQueryParameter("message").orEmpty()
                val reg = "\\b([A-Z0-9]{52})\\b".toRegex().find(msg)
                val reserve = reg?.value ?: uri.getQueryParameter("subject")!!
                val segwitAddresses =
                    Bech32.generateFakeSegwitAddress(reserve, uri.pathSegments.first())
                TransferData.Bitcoin(
                    account = uri.lastPathSegment!!,
                    segwitAddresses = segwitAddresses,
                    subject = reserve,
                    amountRaw = details.amountRaw,
                    amountEffective = details.amountEffective,
                    transferAmount = it.transferAmount
                        ?.withSpec(it.currencySpecification)
                        ?: details.amountEffective,
                    withdrawalAccount = it.copy(paytoUri = uri.toString()),
                )
            } else if (uri.authority.equals("x-taler-bank", true)) {
                TransferData.Taler(
                    account = uri.lastPathSegment!!,
                    receiverName = uri.getQueryParameter("receiver-name"),
                    subject = uri.getQueryParameter("message") ?: "Error: No message in URI",
                    amountRaw = details.amountRaw,
                    amountEffective = details.amountEffective,
                    exchangeBaseUrl = uri.host!!,
                    transferAmount = it.transferAmount
                        ?.withSpec(it.currencySpecification)
                        ?: details.amountEffective,
                    withdrawalAccount = it.copy(paytoUri = uri.toString()),
                )
            } else if (uri.authority.equals("iban", true)) {
                TransferData.IBAN(
                    iban = uri.lastPathSegment!!,
                    receiverName = uri.getQueryParameter("receiver-name"),
                    receiverTown = uri.getQueryParameter("receiver-town"),
                    receiverPostalCode = uri.getQueryParameter("receiver-postal-code"),
                    subject = uri.getQueryParameter("message") ?: "Error: No message in URI",
                    amountRaw = details.amountRaw,
                    amountEffective = details.amountEffective,
                    transferAmount = it.transferAmount
                        ?.withSpec(it.currencySpecification)
                        ?: details.amountEffective,
                    withdrawalAccount = it.copy(paytoUri = uri.toString()),
                )
            } else null
        },
    )
}