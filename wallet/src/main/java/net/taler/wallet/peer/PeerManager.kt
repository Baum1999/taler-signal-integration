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

import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.taler.common.Amount
import net.taler.common.Timestamp
import net.taler.wallet.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorCode.WALLET_PEER_PUSH_PAYMENT_INSUFFICIENT_BALANCE
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.ExchangeManager
import net.taler.wallet.exchanges.ExchangeTosStatus
import org.json.JSONObject
import java.util.concurrent.TimeUnit.HOURS

const val MAX_LENGTH_SUBJECT = 100
val DEFAULT_EXPIRY = ExpirationOption.DAYS_1

sealed class CheckFeeResult {
    abstract val maxDepositAmountEffective: Amount?

    data class None(
        override val maxDepositAmountEffective: Amount? = null,
    ): CheckFeeResult()

    data class InsufficientBalance(
        val maxAmountEffective: Amount?,
        val maxAmountRaw: Amount?,
        override val maxDepositAmountEffective: Amount? = null,
    ): CheckFeeResult()

    data class Success(
        val amountRaw: Amount,
        val amountEffective: Amount,
        val exchangeBaseUrl: String,
        override val maxDepositAmountEffective: Amount? = null,
    ): CheckFeeResult()
}

@Serializable
data class GetMaxPeerPushDebitAmountResponse(
    val effectiveAmount: Amount,
    val rawAmount: Amount,
    val exchangeBaseUrl: String? = null,
)

class PeerManager(
    private val api: WalletBackendApi,
    private val exchangeManager: ExchangeManager,
    private val scope: CoroutineScope,
) {

    private val _outgoingPullState = MutableStateFlow<OutgoingState>(OutgoingIntro)
    val pullState: StateFlow<OutgoingState> = _outgoingPullState

    private val _outgoingPushState = MutableStateFlow<OutgoingState>(OutgoingIntro)
    val pushState: StateFlow<OutgoingState> = _outgoingPushState

    private val _incomingPullState = MutableStateFlow<IncomingState>(IncomingChecking)
    val incomingPullState: StateFlow<IncomingState> = _incomingPullState

    private val _incomingPushState = MutableStateFlow<IncomingState>(IncomingChecking)
    val incomingPushState: StateFlow<IncomingState> = _incomingPushState

    suspend fun checkPeerPullCredit(
        amount: Amount,
        exchangeBaseUrl: String? = null,
        scopeInfo: ScopeInfo? = null,
    ): CheckPeerPullCreditResult? {
        var response: CheckPeerPullCreditResult? = null
        val exchangeItem = exchangeManager.findExchange(amount.currency) ?: return null

        api.request("checkPeerPullCredit", CheckPeerPullCreditResponse.serializer()) {
            exchangeBaseUrl?.let { put("exchangeBaseUrl", it) }
            scopeInfo?.let { put("restrictScope", JSONObject(BackendManager.json.encodeToString(scopeInfo))) }
            put("amount", amount.toJSONString())
        }.onSuccess {
            response = CheckPeerPullCreditResult(
                amountEffective = it.amountEffective,
                amountRaw = it.amountRaw,
                exchangeBaseUrl = it.exchangeBaseUrl,
                tosStatus = exchangeItem.tosStatus,
            )
        }.onError { error ->
            Log.e(TAG, "got checkPeerPullCredit error result $error")
        }

        return response
    }

    fun initiatePeerPullCredit(amount: Amount, summary: String, expirationHours: Long, exchangeBaseUrl: String) {
        _outgoingPullState.value = OutgoingCreating
        scope.launch(Dispatchers.IO) {
            val expiry = Timestamp.fromMillis(System.currentTimeMillis() + HOURS.toMillis(expirationHours))
            api.request("initiatePeerPullCredit", InitiatePeerPullPaymentResponse.serializer()) {
                put("exchangeBaseUrl", exchangeBaseUrl)
                put("partialContractTerms", JSONObject().apply {
                    put("amount", amount.toJSONString())
                    put("summary", summary)
                    put("purse_expiration", JSONObject(Json.encodeToString(expiry)))
                })
            }.onSuccess {
                _outgoingPullState.value = OutgoingResponse(it.transactionId)
            }.onError { error ->
                Log.e(TAG, "got initiatePeerPullCredit error result $error")
                _outgoingPullState.value = OutgoingError(error)
            }
        }
    }

    fun resetPullPayment() {
        _outgoingPullState.value = OutgoingIntro
    }

    suspend fun checkPeerPushFees(amount: Amount, exchangeBaseUrl: String? = null): CheckFeeResult {
        val max = getMaxPeerPushDebitAmount(amount.currency, exchangeBaseUrl)
        var response: CheckFeeResult = CheckFeeResult.None(maxDepositAmountEffective = max?.effectiveAmount)
        api.request("checkPeerPushDebit", CheckPeerPushDebitResponse.serializer()) {
            exchangeBaseUrl?.let { put("exchangeBaseUrl", it) }
            put("amount", amount.toJSONString())
        }.onSuccess {
            response = CheckFeeResult.Success(
                amountRaw = it.amountRaw,
                amountEffective = it.amountEffective,
                maxDepositAmountEffective = max?.effectiveAmount,
                exchangeBaseUrl = it.exchangeBaseUrl,
            )
        }.onError { error ->
            Log.e(TAG, "got checkPeerPushDebit error result $error")
            if (error.code == WALLET_PEER_PUSH_PAYMENT_INSUFFICIENT_BALANCE) {
                error.extra["insufficientBalanceDetails"]?.let { details ->
                    val maxAmountRaw = details.jsonObject["balanceAvailable"]?.let { amount ->
                        Amount.fromJSONString(amount.jsonPrimitive.content)
                    }

                    val maxAmountEffective = details.jsonObject["maxEffectiveSpendAmount"]?.let { amount ->
                        Amount.fromJSONString(amount.jsonPrimitive.content)
                    } ?: maxAmountRaw

                    response = CheckFeeResult.InsufficientBalance(
                        maxAmountEffective = maxAmountEffective,
                        maxAmountRaw = maxAmountRaw,
                        maxDepositAmountEffective = max?.effectiveAmount,
                    )
                }
            }
        }

        return response
    }

    private suspend fun getMaxPeerPushDebitAmount(
        currency: String,
        exchangeBaseUrl: String? = null,
        restrictScope: ScopeInfo? = null,
    ): GetMaxPeerPushDebitAmountResponse? {
        var response: GetMaxPeerPushDebitAmountResponse? = null
        api.request("getMaxPeerPushDebitAmount", GetMaxPeerPushDebitAmountResponse.serializer()) {
            exchangeBaseUrl?.let { put("exchangeBaseUrl", it) }
            restrictScope?.let { put("restrictScope", it) }
            put("currency", currency)
        }.onError { error ->
            Log.e(TAG, "got getMaxPeerPushDebitAmount error result $error")
        }.onSuccess {
            response = it
        }

        return response
    }

    fun initiatePeerPushDebit(amount: Amount, summary: String, expirationHours: Long) {
        _outgoingPushState.value = OutgoingCreating
        scope.launch(Dispatchers.IO) {
            val expiry = Timestamp.fromMillis(System.currentTimeMillis() + HOURS.toMillis(expirationHours))
            api.request("initiatePeerPushDebit", InitiatePeerPushDebitResponse.serializer()) {
                put("amount", amount.toJSONString())
                put("partialContractTerms", JSONObject().apply {
                    put("amount", amount.toJSONString())
                    put("summary", summary)
                    put("purse_expiration", JSONObject(Json.encodeToString(expiry)))
                })
            }.onSuccess { response ->
                _outgoingPushState.value = OutgoingResponse(response.transactionId)
            }.onError { error ->
                Log.e(TAG, "got initiatePeerPushDebit error result $error")
                _outgoingPushState.value = OutgoingError(error)
            }
        }
    }

    fun resetPushPayment() {
        _outgoingPushState.value = OutgoingIntro
    }

    fun preparePeerPullDebit(talerUri: String) {
        _incomingPullState.value = IncomingChecking
        scope.launch(Dispatchers.IO) {
            api.request("preparePeerPullDebit", PreparePeerPullDebitResponse.serializer()) {
                put("talerUri", talerUri)
            }.onSuccess { response ->
                _incomingPullState.value = IncomingTerms(
                    amountRaw = response.amountRaw,
                    amountEffective = response.amountEffective,
                    contractTerms = response.contractTerms,
                    id = response.transactionId,
                )
            }.onError { error ->
                Log.e(TAG, "got preparePeerPullDebit error result $error")
                _incomingPullState.value = IncomingError(error)
            }
        }
    }

    fun confirmPeerPullDebit(terms: IncomingTerms) {
        _incomingPullState.value = IncomingAccepting(terms)
        scope.launch(Dispatchers.IO) {
            api.request<Unit>("confirmPeerPullDebit") {
                put("transactionId", terms.id)
            }.onSuccess {
                _incomingPullState.value = IncomingAccepted
            }.onError { error ->
                Log.e(TAG, "got confirmPeerPullDebit error result $error")
                _incomingPullState.value = IncomingError(error)
            }
        }
    }

    fun preparePeerPushCredit(talerUri: String) {
        _incomingPushState.value = IncomingChecking
        scope.launch(Dispatchers.IO) a@ {
            api.request("preparePeerPushCredit", PreparePeerPushCreditResponse.serializer()) {
                put("talerUri", talerUri)
            }.onSuccess { response ->
                scope.launch(Dispatchers.IO) b@ {
                    val exchange = exchangeManager.findExchangeByUrl(response.exchangeBaseUrl)

                    if (exchange == null) {
                        Log.d(TAG, "exchange entry for ${response.exchangeBaseUrl} was not found")
                        _incomingPushState.value = IncomingError(
                            TalerErrorInfo.makeCustomError( // TODO: localize error
                                "No provider with URL ${cleanExchange(response.exchangeBaseUrl)} was found in the wallet",
                            )
                        )
                        return@b
                    }

                    _incomingPushState.value = if (exchange.tosStatus == ExchangeTosStatus.Accepted) {
                        IncomingTerms(
                            amountRaw = response.amountRaw,
                            amountEffective = response.amountEffective,
                            contractTerms = response.contractTerms,
                            id = response.transactionId,
                        )
                    } else {
                        IncomingTosReview(
                            amountRaw = response.amountRaw,
                            amountEffective = response.amountEffective,
                            contractTerms = response.contractTerms,
                            exchangeBaseUrl = response.exchangeBaseUrl,
                            id = response.transactionId,
                        )
                    }
                }
            }.onError { error ->
                Log.e(TAG, "got preparePeerPushCredit error result $error")
                _incomingPushState.value = IncomingError(error)
            }
        }
    }

    fun confirmPeerPushCredit(terms: IncomingTerms) {
        _incomingPushState.value = IncomingAccepting(terms)
        scope.launch(Dispatchers.IO) {
            api.request<Unit>("confirmPeerPushCredit") {
                put("transactionId", terms.id)
            }.onSuccess {
                _incomingPushState.value = IncomingAccepted
            }.onError { error ->
                Log.e(TAG, "got confirmPeerPushCredit error result $error")
                _incomingPushState.value = IncomingError(error)
            }
        }
    }

    @UiThread
    fun refreshPeerPushCreditTos(exchanges: List<ExchangeItem>) = scope.launch {
        _incomingPushState.update { state ->
            var newState = state
            if (state is IncomingTosReview) {
                exchanges.find { it.exchangeBaseUrl == state.exchangeBaseUrl }?.let { exchange ->
                    if (exchange.tosStatus == ExchangeTosStatus.Accepted) {
                        newState = IncomingTerms(
                            amountRaw = state.amountRaw,
                            amountEffective = state.amountEffective,
                            contractTerms = state.contractTerms,
                            id = state.id,
                        )
                    }
                } ?: run {
                    Log.d(TAG, "could not refresh ToS status, exchange ${state.exchangeBaseUrl} was not found")
                }
            }
            newState
        }
    }

    @UiThread
    fun refreshPeerPullCreditTos(exchanges: List<ExchangeItem>) = scope.launch {
        _outgoingPullState.update { state ->
            var newState = state
            if (state is OutgoingChecked) {
                exchanges.find { it.exchangeBaseUrl == state.exchangeBaseUrl }?.let { exchange ->
                    if (exchange.tosStatus == ExchangeTosStatus.Accepted) {
                        newState = OutgoingChecked(
                            amountRaw = state.amountRaw,
                            amountEffective = state.amountEffective,
                            exchangeBaseUrl = state.exchangeBaseUrl,
                            tosStatus = exchange.tosStatus,
                        )
                    }
                } ?: run {
                    Log.d(TAG, "could not refresh ToS status, exchange ${state.exchangeBaseUrl} was not found")
                }
            }
            newState
        }
    }
}
