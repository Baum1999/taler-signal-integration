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

package net.taler.wallet.balances

import android.util.Log
import androidx.annotation.UiThread
import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.TAG
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.donau.DonauStatement
import net.taler.wallet.donau.GetDonauStatementsResponse
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.ExchangeManager

@Serializable
data class BalanceResponse(
    val balances: List<BalanceItem>
)

@Serializable
data class GetCurrencySpecificationResponse(
    val currencySpecification: CurrencySpecification,
)

// TODO: rename to AssetsState
sealed class BalanceState {
    data object None: BalanceState()
    data object Loading: BalanceState()

    data class Success(
        val balances: List<BalanceItem>,
        val statements: List<DonauStatement>,
    ): BalanceState()

    data class Error(
        val error: TalerErrorInfo,
    ): BalanceState()
}

// TODO: rename to AssetsManager
class BalanceManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
    private val exchangeManager: ExchangeManager,
) {
    private val mBalances = MutableLiveData<List<BalanceItem>>(emptyList())
    val balances: LiveData<List<BalanceItem>> = mBalances

    private val mState = MutableLiveData<BalanceState>(BalanceState.None)
    val state: LiveData<BalanceState> = mState.distinctUntilChanged()

    fun loadAssets(loading: Boolean = false) = scope.launch {
        if (loading) mState.postValue(BalanceState.Loading)
        loadBalances()?.let { balancesList ->
            mState.postValue(BalanceState.Success(
                balances = balancesList,
                statements = emptyList(),
            ))

            // TODO: load lists together when getDonationStatements stops relying on network
            loadDonauStatements()?.let { statementsList ->
                mState.postValue(BalanceState.Success(
                    balances = balancesList,
                    statements = statementsList,
                ))
            }
        }
    }

    private suspend fun loadBalances(): List<BalanceItem>? {
        var res: List<BalanceItem>? = null
        api.request("getBalances", BalanceResponse.serializer())
            .onError {
                Log.e(TAG, "Error retrieving balances: $it")
                mState.postValue(BalanceState.Error(it))
            }.onSuccess {
                it.balances.map { balance ->
                    val spec = runBlocking { exchangeManager
                        .getCurrencySpecification(balance.scopeInfo) }
                    balance.copy(
                        available = balance.available.withSpec(spec),
                        pendingIncoming = balance.pendingIncoming.withSpec(spec),
                        pendingOutgoing = balance.pendingOutgoing.withSpec(spec),
                    )
                }.let { balances ->
                    res = balances
                    mBalances.postValue(balances)
                }
            }
        return res
    }

    private suspend fun loadDonauStatements(): List<DonauStatement>? {
        var list: List<DonauStatement>? = null
        api.request("getDonauStatements", GetDonauStatementsResponse.serializer())
            .onError {
                Log.e(TAG, "Error retrieving donau statements: $it")
                // TODO: throw error when getDonationStatements stop relying on network
                // mState.postValue(BalanceState.Error(it))
            }.onSuccess { res ->
                // only return last year for each authority
                list = res.statements.map { statement ->
                    val spec = runBlocking { exchangeManager
                        .getSpecForCurrency(statement.total.currency) }
                    statement.copy(total = statement.total.withSpec(spec))
                }.sortedByDescending {
                    it.year
                }.fastDistinctBy {
                    it.host
                }
            }
        return list
    }

    @UiThread
    fun getCurrencies() = balances.value?.map { balanceItem ->
        balanceItem.currency
    } ?: emptyList()

    @UiThread
    fun getScopes() = balances.value?.map { balanceItem ->
        balanceItem.scopeInfo
    } ?: emptyList()

    @UiThread
    fun hasSufficientBalance(amount: Amount): Boolean {
        balances.value?.forEach { balanceItem ->
            if (balanceItem.currency == amount.currency) {
                return balanceItem.available >= amount
            }
        }
        return false
    }

    fun addGlobalCurrencyExchange(
        currency: String,
        exchange: ExchangeItem,
        onSuccess: () -> Unit,
        onError: (error: TalerErrorInfo) -> Unit,
    ) = scope.launch {
        api.request<Unit>("addGlobalCurrencyExchange") {
            put("currency", currency)
            put("exchangeBaseUrl", exchange.exchangeBaseUrl)
            put("exchangeMasterPub", exchange.masterPub)
        }.onError { error ->
            Log.e(TAG, "got addGlobalCurrencyExchange error: $error")
            onError(error)
        }.onSuccess {
            onSuccess()
        }
    }

    fun removeGlobalCurrencyExchange(
        currency: String,
        exchange: ExchangeItem,
        onSuccess: () -> Unit,
        onError: (error: TalerErrorInfo) -> Unit,
    ) = scope.launch {
        api.request<Unit>("removeGlobalCurrencyExchange") {
            put("currency", currency)
            put("exchangeBaseUrl", exchange.exchangeBaseUrl)
            put("exchangeMasterPub", exchange.masterPub)
        }.onError { error ->
            Log.e(TAG, "got removeGlobalCurrencyExchange error: $error")
            onError(error)
        }.onSuccess {
            onSuccess()
        }
    }

    fun resetBalances() {
        mState.value = BalanceState.None
    }
}