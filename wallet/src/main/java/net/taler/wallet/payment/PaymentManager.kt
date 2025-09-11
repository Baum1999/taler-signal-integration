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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.taler.common.Amount
import net.taler.common.ContractInput
import net.taler.common.ContractOutput
import net.taler.common.ContractTerms
import net.taler.common.TalerUtils.getLocalizedString
import net.taler.wallet.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.exchanges.ExchangeManager
import net.taler.wallet.payment.PayStatus.AlreadyPaid
import net.taler.wallet.payment.PayStatus.InsufficientBalance
import net.taler.wallet.payment.PreparePayResponse.AlreadyConfirmedResponse
import net.taler.wallet.payment.PreparePayResponse.InsufficientBalanceResponse
import net.taler.wallet.payment.PreparePayResponse.PaymentPossibleResponse
import org.json.JSONObject
import net.taler.wallet.payment.GetChoicesForPaymentResponse.ChoiceSelectionDetail
import net.taler.wallet.payment.GetChoicesForPaymentResponse.ChoiceSelectionDetail.PaymentPossible

sealed class PayStatus {
    data object None : PayStatus()
    data object Loading : PayStatus()
    data class Prepared(
        val transactionId: String,
        val contractTerms: ContractTerms,
    ) : PayStatus()

    data class Choices(
        val transactionId: String,
        val contractTerms: ContractTerms,
        val choices: List<PayChoiceDetails>,
        val defaultChoiceIndex: Int? = null,
    ) : PayStatus()

    data class Checked(
        val details: WalletTemplateDetails,
        val supportedCurrencies: List<String>,
    ) : PayStatus()

    data class InsufficientBalance(
        val transactionId: String,
        val contractTerms: ContractTerms,
        val amountRaw: Amount,
        val balanceDetails: PaymentInsufficientBalanceDetails,
    ) : PayStatus()

    data class AlreadyPaid(
        val transactionId: String,
    ) : PayStatus()

    data class Pending(
        val transactionId: String? = null,
        val error: TalerErrorInfo? = null,
    ) : PayStatus()
    data class Success(
        val transactionId: String,
        val automaticExecution: Boolean,
    ) : PayStatus()
}

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

    @UiThread
    fun preparePay(url: String) = scope.launch {
        mPayStatus.value = PayStatus.Loading
        api.request("preparePayForUri", PreparePayResponse.serializer()) {
            put("talerPayUri", url)
        }.onError {
            handleError("preparePayForUri", it)
        }.onSuccess { response ->
            if (response is AlreadyConfirmedResponse) {
                mPayStatus.value = AlreadyPaid(response.transactionId)
                return@onSuccess
            }

            val transactionId = when (response) {
                is PaymentPossibleResponse -> response.transactionId
                is InsufficientBalanceResponse -> response.transactionId
                is PreparePayResponse.ChoiceSelection -> response.transactionId
                else -> return@onSuccess
            }

            preparePay(transactionId) {}
        }
    }

    @UiThread
    fun preparePay(
        transactionId: String,
        onSuccess: () -> Unit,
    ) = scope.launch {
        api.request("getChoicesForPayment", GetChoicesForPaymentResponse.serializer()) {
            put("transactionId", transactionId)
        }.onSuccess { res ->
            if (res.automaticExecution == true && res.automaticExecutableIndex != null) {
                confirmPay(transactionId, res.automaticExecutableIndex, automaticExecution = true)
                return@onSuccess
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
                    ) ?: exchangeManager.getSpecForCurrency(choice.amountRaw.currency)

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
                        it.amountRaw
                    },
                ),
            )

            onSuccess()
        }.onError { error ->
            handleError("getChoicesForPayment", error)
        }
    }

    fun confirmPay(
        transactionId: String,
        choiceIndex: Int? = null,
        automaticExecution: Boolean = false,
    ) = scope.launch {
        mPayStatus.postValue(PayStatus.Loading)
        api.request("confirmPay", ConfirmPayResult.serializer()) {
            choiceIndex?.let { put("choiceIndex", it) }
            put("transactionId", transactionId)
        }.onError {
            handleError("confirmPay", it)
        }.onSuccess { response ->
            mPayStatus.postValue(when (response) {
                is ConfirmPayResult.Done -> PayStatus.Success(
                    transactionId = response.transactionId,
                    automaticExecution = automaticExecution,
                )
                is ConfirmPayResult.Pending -> PayStatus.Pending(
                    transactionId = response.transactionId,
                    error = response.lastError,
                )
            })
        }
    }

    fun checkPayForTemplate(url: String) = scope.launch {
        mPayStatus.value = PayStatus.Loading
        api.request("checkPayForTemplate", CheckPayTemplateResponse.serializer()) {
            put("talerPayTemplateUri", url)
        }.onError {
            handleError("checkPayForTemplate", it)
        }.onSuccess { response ->
            mPayStatus.value = PayStatus.Checked(
                details = response.templateDetails,
                supportedCurrencies = response.supportedCurrencies,
            )
        }
    }

    fun preparePayForTemplate(url: String, params: TemplateParams) = scope.launch {
        mPayStatus.value = PayStatus.Loading
        api.request("preparePayForTemplate", PreparePayResponse.serializer()) {
            put("talerPayTemplateUri", url)
            put("templateParams", JSONObject(BackendManager.json.encodeToString(params)))
        }.onError {
            handleError("preparePayForTemplate", it)
        }.onSuccess { response ->
            mPayStatus.value = when (response) {
                is PaymentPossibleResponse -> response.toPayStatusPrepared()
                is InsufficientBalanceResponse -> InsufficientBalance(
                    transactionId = response.transactionId,
                    contractTerms = response.contractTerms,
                    amountRaw = response.amountRaw,
                    balanceDetails = response.balanceDetails,
                )

                is AlreadyConfirmedResponse -> AlreadyPaid(
                    transactionId = response.transactionId,
                )

                // only applies to regular payments
                is PreparePayResponse.ChoiceSelection -> return@onSuccess
            }
        }
    }

    @UiThread
    fun resetPayStatus() {
        mPayStatus.value = PayStatus.None
    }

    private fun handleError(operation: String, error: TalerErrorInfo) {
        Log.e(TAG, "got $operation error result $error")
        mPayStatus.value = PayStatus.Pending(error = error)
    }

}
