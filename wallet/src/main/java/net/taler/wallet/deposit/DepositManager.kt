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

import android.net.Uri
import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.taler.common.Amount
import net.taler.wallet.TAG
import net.taler.wallet.accounts.KnownBankAccountInfo
import net.taler.wallet.accounts.PaytoUriBitcoin
import net.taler.wallet.accounts.PaytoUriIban
import net.taler.wallet.accounts.PaytoUriTalerBank
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorCode.WALLET_DEPOSIT_GROUP_INSUFFICIENT_BALANCE
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.BalanceManager
import net.taler.wallet.balances.ScopeInfo
import org.json.JSONObject

class DepositManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
    private val balanceManager: BalanceManager,
) {

    private val mDepositState = MutableStateFlow<DepositState>(DepositState.Start)
    internal val depositState = mDepositState.asStateFlow()

    fun isSupportedPayToUri(uriString: String): Boolean {
        if (!uriString.startsWith("payto://")) return false
        val u = Uri.parse(uriString)
        if (!u.authority.equals("iban", ignoreCase = true)) return false
        return u.pathSegments.size >= 1
    }

    @UiThread
    fun selectAccount(account: KnownBankAccountInfo) = scope.launch {
        getMaxDepositableForPayto(account.paytoUri).let { response ->
            mDepositState.value = DepositState.AccountSelected(account, response)
        }
    }

    suspend fun checkDepositFees(paytoUri: String, amount: Amount): CheckDepositResult {
        val max = getMaxDepositAmount(amount.currency, paytoUri)
        var response: CheckDepositResult = CheckDepositResult.None(
            maxDepositAmountEffective = max?.effectiveAmount,
            maxDepositAmountRaw = max?.rawAmount,
        )
        api.request("checkDeposit", CheckDepositResponse.serializer()) {
            put("depositPaytoUri", paytoUri)
            put("amount", amount.toJSONString())
        }.onSuccess {
            response = CheckDepositResult.Success(
                totalDepositCost = it.totalDepositCost,
                effectiveDepositAmount = it.effectiveDepositAmount,
                kycSoftLimit = it.kycSoftLimit,
                kycHardLimit = it.kycHardLimit,
                kycExchanges = it.kycExchanges,
                maxDepositAmountEffective = max?.effectiveAmount,
                maxDepositAmountRaw = max?.rawAmount,
            )
        }.onError { error ->
            Log.e(TAG, "Error checkDeposit $error")
            if (error.code == WALLET_DEPOSIT_GROUP_INSUFFICIENT_BALANCE) {
                error.extra["insufficientBalanceDetails"]?.let { details ->
                    val maxAmountRaw = details.jsonObject["balanceAvailable"]?.let { amount ->
                        Amount.fromJSONString(amount.jsonPrimitive.content)
                    }

                    val maxAmountEffective = details.jsonObject["maxEffectiveSpendAmount"]?.let { amount ->
                        Amount.fromJSONString(amount.jsonPrimitive.content)
                    } ?: maxAmountRaw

                    response = CheckDepositResult.InsufficientBalance(
                        maxAmountEffective = maxAmountEffective,
                        maxAmountRaw = maxAmountRaw,
                        maxDepositAmountEffective = max?.effectiveAmount,
                        maxDepositAmountRaw = max?.rawAmount,
                    )
                }
            }
        }

        return response
    }

    private suspend fun getMaxDepositAmount(
        currency: String,
        depositPaytoUri: String?,
    ): GetMaxDepositAmountResponse? {
        var response: GetMaxDepositAmountResponse? = null
        api.request("getMaxDepositAmount", GetMaxDepositAmountResponse.serializer()) {
            depositPaytoUri?.let { put("depositPaytoUri", it) }
            put("currency", currency)
        }.onError { error ->
            Log.e(TAG, "Error getMaxDepositAmount $error")
        }.onSuccess {
            response = it
        }

        return response
    }

    fun makeDeposit(amount: Amount, paytoUri: String) {
        mDepositState.value = DepositState.MakingDeposit

        scope.launch {
            api.request("createDepositGroup", CreateDepositGroupResponse.serializer()) {
                put("depositPaytoUri", paytoUri)
                put("amount", amount.toJSONString())
            }.onError {
                Log.e(TAG, "Error createDepositGroup $it")
                mDepositState.value = DepositState.Error(it)
            }.onSuccess {
                mDepositState.value = DepositState.Success
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
    ): GetDepositWireTypesResponse? {
        var result: GetDepositWireTypesResponse? = null
        api.request("getDepositWireTypes", GetDepositWireTypesResponse.serializer()) {
            scopeInfo?.let { put("scopeInfo", JSONObject(BackendManager.json.encodeToString(it))) }
            currency?.let { put("currency", it) }
            this
        }.onError {
            Log.e(TAG, "Error getDepositWireTypes $it")
        }.onSuccess {
            result = it
        }
        return result
    }

    private suspend fun getMaxDepositableForPayto(
        paytoUri: String,
    ): Map<String, GetMaxDepositAmountResponse?> {
        return balanceManager.getCurrencies().associateWith { currency ->
            getMaxDepositAmount(currency, paytoUri)
        }
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
    params = mapOf("receiver-name" to receiverName),
    receiverName = receiverName,
    receiverPostalCode = receiverPostalCode,
    receiverTown = receiverTown,
).paytoUri

fun getTalerPayto(receiverName: String, host: String, account: String) = PaytoUriTalerBank(
    host = host,
    account = account,
    targetPath = "",
    params = mapOf("receiver-name" to receiverName),
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
    abstract val maxDepositAmountEffective: Amount?
    abstract val maxDepositAmountRaw: Amount?

    data class None(
        override val maxDepositAmountEffective: Amount? = null,
        override val maxDepositAmountRaw: Amount? = null,
    ): CheckDepositResult()

    data class InsufficientBalance(
        val maxAmountEffective: Amount?,
        val maxAmountRaw: Amount?,
        override val maxDepositAmountEffective: Amount?,
        override val maxDepositAmountRaw: Amount? = null,
    ): CheckDepositResult()

    data class Success(
        val totalDepositCost: Amount,
        val effectiveDepositAmount: Amount,
        val kycSoftLimit: Amount? = null,
        val kycHardLimit: Amount? = null,
        val kycExchanges: List<String>? = null,
        override val maxDepositAmountEffective: Amount?,
        override val maxDepositAmountRaw: Amount? = null,
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
            .flatMap { it.talerBankHostnames }
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
    val paymentTargetType: WireType,
    val talerBankHostnames: List<String>,
)