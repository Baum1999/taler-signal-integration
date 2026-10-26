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

package net.taler.wallet.deposit

import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.main.TAG
import net.taler.wallet.accounts.KnownBankAccountInfo
import net.taler.wallet.accounts.PaytoUriBitcoin
import net.taler.wallet.accounts.PaytoUriIban
import net.taler.wallet.accounts.PaytoUriTalerBank
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorCode.WALLET_DEPOSIT_GROUP_INSUFFICIENT_BALANCE
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletResponse
import net.taler.wallet.balances.BalanceManager
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.exchanges.ExchangeManager
import org.json.JSONObject
import androidx.core.net.toUri

class DepositManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
    private val balanceManager: BalanceManager,
    private val exchangeManager: ExchangeManager,
) {

    private val mDepositState = MutableStateFlow<DepositState>(DepositState.Start)
    internal val depositState = mDepositState.asStateFlow()

    private suspend fun getCurrencySpec(currency: String): CurrencySpecification? =
        exchangeManager.getCachedSpecForCurrency(currency)
            ?: exchangeManager.getSpecForScopeInfo(ScopeInfo.Global(currency))

    fun isSupportedPayToUri(uriString: String): Boolean {
        if (!uriString.startsWith("payto://")) return false
        val u = uriString.toUri()
        if (!u.authority.equals("iban", ignoreCase = true)) return false
        return u.pathSegments.isNotEmpty()
    }

    @UiThread
    fun selectAccount(account: KnownBankAccountInfo) = scope.launch {
        val (maxDepositable, error) = getMaxDepositableForPayto(account.paytoUri)
        mDepositState.value = DepositState.AccountSelected(
            account = account,
            maxDepositable = maxDepositable,
            maxDepositError = error,
        )
    }

    suspend fun checkDepositFees(paytoUri: String, amount: Amount): CheckDepositResult {
        val spec = getCurrencySpec(amount.currency)
        var response: CheckDepositResult = CheckDepositResult.None
        when (val res = api.request(
            "checkDeposit",
            CheckDepositResponse.serializer(),
            progressToken = paytoUri,
        ) {
            put("depositPaytoUri", paytoUri)
            put("amount", amount.toJSONString())
        }) {
            is WalletResponse.Error -> {
                Log.e(TAG, "Error checkDeposit ${res.error}")
                if (res.error.code == WALLET_DEPOSIT_GROUP_INSUFFICIENT_BALANCE) {
                    res.error.extra["insufficientBalanceDetails"]?.let { details ->
                        val maxAmountRaw = details.jsonObject["balanceAvailable"]?.let { amount ->
                            Amount.fromJSONString(amount.jsonPrimitive.content)
                        }?.withSpec(spec)

                        val maxAmountEffective = details.jsonObject["maxEffectiveSpendAmount"]?.let { amount ->
                            Amount.fromJSONString(amount.jsonPrimitive.content)
                        }?.withSpec(spec) ?: maxAmountRaw

                        response = CheckDepositResult.InsufficientBalance(
                            maxAmountEffective = maxAmountEffective,
                            maxAmountRaw = maxAmountRaw,
                        )
                    }
                }
            }

            is WalletResponse.Success -> {
                val max = getMaxDepositAmount(amount.currency, paytoUri)
                response = if (max?.effectiveAmount != null && amount > max.effectiveAmount) {
                    CheckDepositResult.ExceedsLimit(
                        maxDepositAmountEffective = max.effectiveAmount,
                        maxDepositAmountRaw = max.rawAmount,
                    )
                } else {
                    CheckDepositResult.Success(
                        totalDepositCost = res.result.totalDepositCost.withSpec(spec),
                        effectiveDepositAmount = res.result.effectiveDepositAmount.withSpec(spec),
                        kycSoftLimit = res.result.kycSoftLimit?.withSpec(spec),
                        kycHardLimit = res.result.kycHardLimit?.withSpec(spec),
                        kycExchanges = res.result.kycExchanges,
                    )
                }
            }
        }

        return response
    }

    private suspend fun getMaxDepositAmount(
        currency: String,
        depositPaytoUri: String?,
        onError: (TalerErrorInfo) -> Unit = {},
    ): GetMaxDepositAmountResponse? {
        var response: GetMaxDepositAmountResponse? = null
        api.request("getMaxDepositAmount", GetMaxDepositAmountResponse.serializer()) {
            depositPaytoUri?.let { put("depositPaytoUri", it) }
            put("currency", currency)
        }.onSuccess {
            val spec = getCurrencySpec(currency)
            response = it.copy(
                effectiveAmount = it.effectiveAmount.withSpec(spec),
                rawAmount = it.rawAmount.withSpec(spec),
            )
        }.onError { error ->
            Log.e(TAG, "Error getMaxDepositAmount $error")
            onError(error)
        }
        return response
    }

    fun makeDeposit(amount: Amount, paytoUri: String) {
        mDepositState.value = DepositState.MakingDeposit

        scope.launch {
            api.request(
                "createDepositGroup",
                CreateDepositGroupResponse.serializer(),
                progressToken = paytoUri,
            ) {
                put("depositPaytoUri", paytoUri)
                put("amount", amount.toJSONString())
            }.onError {
                Log.e(TAG, "Error createDepositGroup $it")
                mDepositState.value = DepositState.Error(it)
            }.onSuccess {
                mDepositState.value = DepositState.Success(it.transactionId)
            }
        }
    }

    @UiThread
    fun resetDepositState() {
        mDepositState.value = DepositState.Start
    }

    suspend fun validateIban(iban: String): Boolean {
        var response = false
        api.request("validateIban", ValidateIbanResponse.serializer()) {
            put("iban", iban)
        }.onError {
            Log.d(TAG, "Error validateIban $it")
            response = false
        }.onSuccess {
            response = it.valid
        }
        return response
    }

    suspend fun getDepositWireTypes(
        currency: String? = null,
        scopeInfo: ScopeInfo? = null,
        onSuccess: (GetDepositWireTypesResponse) -> Unit,
        onError: (TalerErrorInfo) -> Unit,
    ) {
        api.request("getDepositWireTypes", GetDepositWireTypesResponse.serializer()) {
            scopeInfo?.let { put("scopeInfo", JSONObject(BackendManager.json.encodeToString(it))) }
            currency?.let { put("currency", it) }
            this
        }.onSuccess {
            onSuccess(it)
        }.onError {
            Log.e(TAG, "Error getDepositWireTypes $it")
            onError(it)
        }
    }

    private suspend fun getMaxDepositableForPayto(
        paytoUri: String,
    ): Pair<Map<String, GetMaxDepositAmountResponse?>, TalerErrorInfo?> {
        var error: TalerErrorInfo? = null
        val maxDepositable = balanceManager.getCurrencies().associateWith { currency ->
            getMaxDepositAmount(currency, paytoUri) {
                if (error == null) error = it
            }
        }
        return maxDepositable to error
    }
}

fun getIbanPayto(
    receiverName: String,
    receiverPostalCode: String?,
    receiverTown: String?,
    iban: String,
) = PaytoUriIban(
    iban = iban,
    bic = null,
    targetPath = "",
    params = mapOf(),
    receiverName = receiverName,
    receiverPostalCode = receiverPostalCode,
    receiverTown = receiverTown,
).paytoUri

fun getTalerPayto(receiverName: String, host: String, account: String) = PaytoUriTalerBank(
    host = host,
    account = account,
    targetPath = "",
    params = mapOf(),
    receiverName = receiverName,
).paytoUri

fun getBitcoinPayto(bitcoinAddress: String, receiverName: String? = null) = PaytoUriBitcoin(
    segwitAddresses = listOf(bitcoinAddress),
    targetPath = bitcoinAddress,
    receiverName = receiverName,
).paytoUri

@Serializable
data class ValidateIbanResponse(
    val valid: Boolean,
)

@Serializable
data class CheckDepositResponse(
    val totalDepositCost: Amount,
    val effectiveDepositAmount: Amount,
    val kycSoftLimit: Amount? = null,
    val kycHardLimit: Amount? = null,
    val kycExchanges: List<String>? = null,
)

@Serializable
sealed class CheckDepositResult {
    data object None: CheckDepositResult()

    data class InsufficientBalance(
        val maxAmountEffective: Amount?,
        val maxAmountRaw: Amount?,
    ): CheckDepositResult()

    data class ExceedsLimit(
        val maxDepositAmountEffective: Amount,
        val maxDepositAmountRaw: Amount,
    ): CheckDepositResult()

    data class Success(
        val totalDepositCost: Amount,
        val effectiveDepositAmount: Amount,
        val kycSoftLimit: Amount? = null,
        val kycHardLimit: Amount? = null,
        val kycExchanges: List<String>? = null,
    ): CheckDepositResult()
}

@Serializable
data class GetMaxDepositAmountResponse(
    val effectiveAmount: Amount,
    val rawAmount: Amount,
)

@Serializable
data class CreateDepositGroupResponse(
    val depositGroupId: String,
    val transactionId: String,
)

@Serializable
data class GetDepositWireTypesResponse(
    val wireTypeDetails: List<WireTypeDetails>,
) {
    val wireTypes: List<WireType>
        get() = wireTypeDetails.map { it.paymentTargetType }

    val hostNames: List<String>
        get() = wireTypeDetails
            .flatMap { it.talerBankHostnames ?: emptyList() }
            .distinct()
}

@Serializable
data class GetScopesForPaytoResponse(
    val scopes: List<Scope>,
) {
    @Serializable
    data class Scope(
        val scopeInfo: ScopeInfo,
        val available: Boolean,
        // val restrictedAccounts: List<ExchangeWireAccount>,
    )
}

@Serializable
enum class WireType {
    Unknown,

    @SerialName("iban")
    IBAN,

    @SerialName("x-taler-bank")
    TalerBank,

    @SerialName("bitcoin")
    Bitcoin,
}

@Serializable
data class WireTypeDetails(
    val paymentTargetType: WireType = WireType.Unknown,
    val talerBankHostnames: List<String>? = null,
)