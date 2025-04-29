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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonClassDiscriminator
import net.taler.common.Amount
import net.taler.common.ContractTerms
import net.taler.wallet.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.payment.PayStatus.AlreadyPaid
import net.taler.wallet.payment.PayStatus.InsufficientBalance
import net.taler.wallet.payment.PreparePayResponse.AlreadyConfirmedResponse
import net.taler.wallet.payment.PreparePayResponse.InsufficientBalanceResponse
import net.taler.wallet.payment.PreparePayResponse.PaymentPossibleResponse
import net.taler.wallet.transactions.TransactionPayment
import org.json.JSONObject

sealed class PayStatus {
    data object None : PayStatus()
    data object Loading : PayStatus()
    data class Prepared(
        val contractTerms: ContractTerms,
        val transactionId: String,
        val amountRaw: Amount,
        val amountEffective: Amount,
    ) : PayStatus()

    data class Checked(
        val details: WalletTemplateDetails,
        val supportedCurrencies: List<String>,
    ) : PayStatus()

    data class InsufficientBalance(
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
        val currency: String,
    ) : PayStatus()
}

@Serializable
data class CheckPayTemplateResponse(
    val templateDetails: WalletTemplateDetails,
    val supportedCurrencies: List<String>,
)

@Serializable
data class GetChoicesForPaymentResponse(
    val choices: List<ChoiceSelectionDetail>,
    val contractData: ContractTerms,
) {
    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("status")
    sealed class ChoiceSelectionDetail {
        @Serializable
        @SerialName("payment-possible")
        data class PaymentPossible(
            val amountRaw: Amount,
            val amountEffective: Amount,
        ) : ChoiceSelectionDetail()

        @Serializable
        @SerialName("insufficient-balance")
        data class InsufficientBalance(
            val amountRaw: Amount,
            val balanceDetails: PaymentInsufficientBalanceDetails? = null,
        ) : ChoiceSelectionDetail()
    }
}

class PaymentManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
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
            mPayStatus.value = when (response) {
                is PaymentPossibleResponse -> response.toPayStatusPrepared()
                is InsufficientBalanceResponse -> InsufficientBalance(
                    contractTerms = response.contractTerms,
                    amountRaw = response.amountRaw,
                    balanceDetails = response.balanceDetails,
                )
                is AlreadyConfirmedResponse -> AlreadyPaid(
                    transactionId = response.transactionId,
                )
            }
        }
    }

    @UiThread
    fun preparePay(
        tx: TransactionPayment,
        onSuccess: () -> Unit,
    ) = scope.launch {
        api.request("getChoicesForPayment", GetChoicesForPaymentResponse.serializer()) {
            put("transactionId", tx.transactionId)
        }.onSuccess { res ->
            // TODO: this is a terrible v0-only hack!
            if (res.choices.size == 1) {
                when (val choice = res.choices[0]) {
                    is GetChoicesForPaymentResponse.ChoiceSelectionDetail.PaymentPossible -> {
                        mPayStatus.value = PayStatus.Prepared(
                            transactionId = tx.transactionId,
                            amountRaw = choice.amountRaw,
                            amountEffective = choice.amountEffective,
                            contractTerms = res.contractData,
                        )
                        onSuccess()
                    }

                    is GetChoicesForPaymentResponse.ChoiceSelectionDetail.InsufficientBalance -> {
                        if (choice.balanceDetails != null) {
                            mPayStatus.value = InsufficientBalance(
                                amountRaw = choice.amountRaw,
                                contractTerms = res.contractData,
                                balanceDetails = choice.balanceDetails,
                            )
                            onSuccess()
                        }
                    }
                }
            }
        }.onError { error ->
            handleError("getChoicesForPayment", error)
        }
    }

    fun confirmPay(transactionId: String, currency: String) = scope.launch {
        api.request("confirmPay", ConfirmPayResult.serializer()) {
            put("transactionId", transactionId)
        }.onError {
            handleError("confirmPay", it)
        }.onSuccess { response ->
            mPayStatus.postValue(when (response) {
                is ConfirmPayResult.Done -> PayStatus.Success(
                    transactionId = response.transactionId,
                    currency = currency,
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
                    contractTerms = response.contractTerms,
                    amountRaw = response.amountRaw,
                    balanceDetails = response.balanceDetails,
                )

                is AlreadyConfirmedResponse -> AlreadyPaid(
                    transactionId = response.transactionId,
                )
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
