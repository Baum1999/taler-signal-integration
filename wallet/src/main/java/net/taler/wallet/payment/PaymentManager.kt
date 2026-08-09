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

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.common.ContractInput
import net.taler.common.ContractOutput
import net.taler.common.ContractTerms
import net.taler.common.TalerUtils.getLocalizedString
import net.taler.wallet.main.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.NotificationPayload
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletResponse
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.donau.DonauInfo
import net.taler.wallet.donau.GetDonauResponse
import net.taler.wallet.exchanges.ExchangeManager
import org.json.JSONObject
import net.taler.wallet.payment.GetChoicesForPaymentResponse.ChoiceSelectionDetail
import net.taler.wallet.payment.GetChoicesForPaymentResponse.ChoiceSelectionDetail.PaymentPossible
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionMinorState

sealed class PayStatus(
    open val transactionId: String? = null,
) {
    data object None : PayStatus()
    data object Loading : PayStatus()
    data class Prepared(
        override val transactionId: String,
    ) : PayStatus(transactionId)

    data class Choices(
        override val transactionId: String,
        val contractTerms: ContractTerms,
        val choices: List<PayChoiceDetails>,
        val defaultChoiceIndex: Int? = null,
    ) : PayStatus(transactionId)

    data class Checked(
        val details: WalletTemplateDetails,
        val supportedCurrencies: List<String>,
    ) : PayStatus()

    data class InsufficientBalance(
        override val transactionId: String,
        val contractTerms: ContractTerms,
        val amountRaw: Amount,
        val balanceDetails: PaymentInsufficientBalanceDetails,
    ) : PayStatus(transactionId)

    data class AlreadyPaid(
        override val transactionId: String,
    ) : PayStatus(transactionId)

    data class Pending(
        override val transactionId: String? = null,
        val error: TalerErrorInfo? = null,
    ) : PayStatus(transactionId)

    data class Error(
        val error: TalerErrorInfo,
        override val transactionId: String? = null,
    ) : PayStatus(transactionId)

    data class Success(
        override val transactionId: String,
        val automaticExecution: Boolean,
    ) : PayStatus(transactionId)
}

/**
 * Payment status is shared between all payment attempts, so screens must
 * scope the observed status to their own transaction: status produced by
 * another transaction is never rendered, instead the screen keeps loading.
 */
internal fun PayStatus.forTransaction(transactionId: String): PayStatus =
    if (this.transactionId == transactionId) this else PayStatus.Loading

data class PayChoiceDetails(
    val choiceIndex: Int,
    val amountRaw: Amount,
    val description: String? = null,
    val descriptionI18n: Map<String, String>? = null,
    val inputs: List<ContractInput>,
    val outputs: List<ContractOutput>,
    val details: ChoiceSelectionDetail,
) {
    val localizedDescription: String?
        get() = description?.let {
            getLocalizedString(descriptionI18n, it)
        }
}

@Serializable
data class CheckPayTemplateResponse(
    val templateDetails: WalletTemplateDetails,
    val supportedCurrencies: List<String>,
)

class PaymentManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
    private val exchangeManager: ExchangeManager,
) {
    private val mPayStatus = MutableLiveData<PayStatus>(PayStatus.None)
    internal val payStatus: LiveData<PayStatus> = mPayStatus

    /**
     * Transaction id of the currently active payment. Watched for
     * transaction-state-transition notifications so that a transaction
     * deleted by wallet-core (e.g. after a failed claim) surfaces an
     * error in the UI instead of leaving the user on a loading screen.
     * Cleared whenever a new payment attempt starts or the currently active
     * payment completes, so failures of an older payment are never shown
     * during a newer one.
     */
    @Volatile
    private var currentTransactionId: String? = null

    /**
     * In-flight request of the currently active payment attempt. A new
     * payment attempt cancels it, so an error or late response from an
     * older attempt can never appear during a newer payment.
     */
    private var attemptJob: Job? = null

    private fun cancelPreviousAttempt() {
        attemptJob?.cancel()
        attemptJob = null
    }

    private fun startNewAttempt(block: suspend CoroutineScope.() -> Unit) {
        cancelPreviousAttempt()
        attemptJob = scope.launch { block() }
    }

    suspend fun preparePay(url: String): String? {
        // Runs in the caller's scope, which is cancelled when the caller
        // leaves the screen, so a response can never outlive this payment.
        cancelPreviousAttempt()
        currentTransactionId = null
        mPayStatus.value = PayStatus.Loading
        var transactionId: String? = null
        api.request("preparePayForUriV2", PreparePayV2Response.serializer()) {
            put("talerPayUri", url)
        }.onError {
            handleError("preparePayForUriV2", it)
        }.onSuccess { response ->
            currentTransactionId = response.transactionId
            transactionId = response.transactionId
        }
        return transactionId
    }

    @UiThread
    fun getPaymentChoices(
        transactionId: String,
        onSuccess: () -> Unit,
    ) {
        currentTransactionId = transactionId
        startNewAttempt {
            // Fresh payments start at a loading prompt.
            mPayStatus.value = PayStatus.Loading
            when (val response = api.request("getChoicesForPayment", GetChoicesForPaymentResponse.serializer()) {
                put("transactionId", transactionId)
            }) {
                is WalletResponse.Error -> {
                    val error = response.error
                    if (error.code == TalerErrorCode.WALLET_REQUEST_TRANSACTION_STATE_UNSUPPORTED) {
                        // The proposal is not downloaded yet; keep loading until it is.
                        Log.d(TAG, "getChoicesForPayment not ready yet: $error")
                    } else {
                        handleError("getChoicesForPayment", error, transactionId)
                    }
                }

                is WalletResponse.Success -> {
                    val res = response.result
                    if (res.automaticExecution == true && res.automaticExecutableIndex != null) {
                        confirmPay(
                            transactionId,
                            res.automaticExecutableIndex,
                            automaticExecution = true
                        )
                        return@startNewAttempt
                    }

                    mPayStatus.value = PayStatus.Choices(
                        transactionId = transactionId,
                        contractTerms = res.contractTerms,
                        defaultChoiceIndex = res.defaultChoiceIndex,
                        choices = res.choices.map { choice ->
                            val spec = exchangeManager.getSpecForCurrency(
                                choice.amountRaw.currency,
                                res.contractTerms.exchanges.map {
                                    ScopeInfo.Exchange(choice.amountRaw.currency, it.url)
                                },
                            ) ?: res.contractTerms.exchanges.firstOrNull()?.let {
                                exchangeManager.getSpecForScopeInfo(
                                    ScopeInfo.Exchange(choice.amountRaw.currency, it.url)
                                )
                            }

                            when (choice) {
                                is PaymentPossible -> {
                                    choice.copy(
                                        amountRaw = choice.amountRaw.withSpec(spec),
                                        amountEffective = choice.amountEffective.withSpec(spec),
                                    )
                                }

                                is ChoiceSelectionDetail.InsufficientBalance -> {
                                    choice.copy(amountRaw = choice.amountRaw.withSpec(spec))
                                }
                            }
                        }.mapIndexed { i, choice ->
                            PayChoiceDetails(
                                choiceIndex = i,
                                description = choice.description,
                                descriptionI18n = choice.descriptionI18n,
                                amountRaw = choice.amountRaw,
                                inputs = (res.contractTerms as? ContractTerms.V1)
                                    ?.choices?.get(i)?.inputs ?: listOf(),
                                outputs = (res.contractTerms as? ContractTerms.V1)
                                    ?.choices?.get(i)?.outputs ?: listOf(),
                                details = choice,
                            )
                        }.filter {
                            // Hide auto executable choice
                            res.automaticExecutableIndex != it.choiceIndex
                        }.sortedWith(
                            compareByDescending<PayChoiceDetails> {
                                it.choiceIndex == res.defaultChoiceIndex
                            }.thenByDescending {
                                it.details is PaymentPossible
                            }.thenByDescending {
                                it.amountRaw.toString()
                            },
                        ),
                    )

                    onSuccess()
                }
            }
        }
    }

    fun confirmPay(
        transactionId: String,
        choiceIndex: Int? = null,
        automaticExecution: Boolean = false,
        useDonau: Boolean = false,
    ) {
        currentTransactionId = transactionId
        startNewAttempt {
            mPayStatus.postValue(PayStatus.Loading)
            api.request("confirmPay", ConfirmPayResult.serializer()) {
                choiceIndex?.let { put("choiceIndex", it) }
                put("transactionId", transactionId)
                put("useDonau", useDonau)
            }.onError {
                handleError("confirmPay", it, transactionId)
            }.onSuccess { response ->
                mPayStatus.postValue(when (response) {
                    is ConfirmPayResult.Done -> {
                        currentTransactionId = null
                        PayStatus.Success(
                            transactionId = response.transactionId,
                            automaticExecution = automaticExecution,
                        )
                    }
                    is ConfirmPayResult.Pending -> PayStatus.Pending(
                        transactionId = response.transactionId,
                        error = response.lastError,
                    )
                })
            }
        }
    }

    suspend fun checkDonauForChoice(
        choiceDetails: PayChoiceDetails
    ): DonauStatus {
        val taxReceipt = (choiceDetails.outputs)
            .find { it is ContractOutput.TaxReceipt }
                as ContractOutput.TaxReceipt?

        return if (taxReceipt != null) {
            var donauInfo: DonauInfo? = null
            api.request("getDonau", GetDonauResponse.serializer())
                .onSuccess { donauInfo = it.currentDonauInfo }

            if (donauInfo == null) {
                DonauStatus.Unset(taxReceipt.donauUrls.distinct())
            } else if (taxReceipt.donauUrls.contains(donauInfo!!.donauBaseUrl)) {
                DonauStatus.Available
            } else {
                DonauStatus.Mismatch(
                    donauInfo = donauInfo!!,
                    donauUrls = taxReceipt.donauUrls.distinct(),
                )
            }
        } else {
            DonauStatus.Unavailable
        }
    }

    fun checkPayForTemplate(url: String) {
        currentTransactionId = null
        mPayStatus.value = PayStatus.Loading
        startNewAttempt {
            when (val response = api.request("checkPayForTemplate", CheckPayTemplateResponse.serializer()) {
                put("talerPayTemplateUri", url)
            }) {
                is WalletResponse.Error -> handleError("checkPayForTemplate", response.error)

                is WalletResponse.Success -> {
                    // The auto-prepare runs in this attempt, so it is
                    // cancelled together with it when a new attempt starts.
                    val res = response.result
                    if (res.templateDetails.templateContract.templateType == TemplateType.Paivana) {
                        mPayStatus.value = PayStatus.Loading
                        preparePayForTemplate(url, TemplateParams())?.let { transactionId ->
                            mPayStatus.value = PayStatus.Prepared(transactionId = transactionId)
                        }
                    } else {
                        mPayStatus.value = PayStatus.Checked(
                            details = res.templateDetails,
                            supportedCurrencies = res.supportedCurrencies,
                        )
                    }
                }
            }
        }
    }

    suspend fun preparePayForTemplate(url: String, params: TemplateParams): String? {
        // Runs in the caller's (attempt or screen) scope; a newer payment
        // attempt cancels the enclosing attempt scope before it can produce
        // a stale result. The screen's check already superseded any earlier
        // attempt before a form can be submitted.
        mPayStatus.value = PayStatus.Loading
        var transactionId: String? = null
        api.request("preparePayForTemplateV2", PreparePayV2Response.serializer()) {
            put("talerPayTemplateUri", url)
            put("templateParams", JSONObject(BackendManager.json.encodeToString(params)))
        }.onError {
            handleError("preparePayForTemplate", it)
        }.onSuccess { response ->
            currentTransactionId = response.transactionId
            transactionId = response.transactionId
        }
        return transactionId
    }

    /**
     * Called on transaction-state-transition notifications. If the currently
     * active payment is deleted by wallet-core with cause "claim-failed"
     * (e.g. the merchant rejected or deleted the order), the UI is taken
     * out of its loading state and shown the error instead.
     */
    @Synchronized
    fun onTransactionStateTransition(payload: NotificationPayload.TransactionStateTransition) {
        if (!payload.isClaimFailureDeletion()) return
        val transactionId = checkNotNull(payload.transactionId)
        if (transactionId != currentTransactionId) return
        currentTransactionId = null
        val errorInfo = payload.errorInfo ?: TalerErrorInfo.makeCustomError(
            message = "The order could not be found. Maybe the merchant deleted it.",
            code = TalerErrorCode.WALLET_MERCHANT_ORDER_NOT_FOUND,
        )
        Log.e(TAG, "prepared transaction $transactionId deleted by wallet-core: $errorInfo")
        mPayStatus.postValue(PayStatus.Error(error = errorInfo, transactionId = transactionId))
    }

    private fun handleError(operation: String, error: TalerErrorInfo, transactionId: String? = null) {
        Log.e(TAG, "got $operation error result $error")
        mPayStatus.postValue(PayStatus.Error(error = error, transactionId = transactionId))
    }

}

/**
 * Matches the deletion notification emitted by wallet-core when a payment
 * claim failed, e.g. because the merchant rejected or deleted the order.
 * Only this specific state transition is of interest to in-progress
 * payments; other deletions must not surface as payment errors.
 */
internal fun NotificationPayload.TransactionStateTransition.isClaimFailureDeletion(): Boolean =
    transactionId != null &&
        causeHint == "claim-failed" &&
        oldTxState?.major == TransactionMajorState.Pending &&
        oldTxState.minor == TransactionMinorState.ClaimProposal &&
        newTxState?.major == TransactionMajorState.Deleted
