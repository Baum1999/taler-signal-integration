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

package net.taler.wallet.transactions

import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import net.taler.wallet.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.transactions.TransactionAction.Delete
import net.taler.wallet.transactions.TransactionMajorState.Pending
import org.json.JSONObject

sealed class TransactionsResult {
    data object None : TransactionsResult()
    data class Error(val error: TalerErrorInfo) : TransactionsResult()
    data class Success(val transactions: List<Transaction>) : TransactionsResult()
}

class TransactionManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {
    private val allTransactions = HashMap<ScopeInfo, List<Transaction>>()
    private val mTransactions = HashMap<ScopeInfo, MutableStateFlow<TransactionsResult>>()
    private val mSelectedTransaction = MutableStateFlow<Transaction?>(null)
    private val mSelectedScope = MutableStateFlow<ScopeInfo?>(null)
    private val mSearchQuery = MutableStateFlow<String?>(null)

    val selectedTransaction = mSelectedTransaction.asStateFlow()
    val selectedScope = mSelectedScope.asStateFlow()
    val searchQuery = mSearchQuery.asStateFlow()

    // This function must be called ONLY when scopeInfo / searchQuery change!
    // Use remember() {} in Compose to prevent multiple calls during recomposition
    fun transactionsFlow(
        scopeInfo: ScopeInfo? = null,
        searchQuery: String? = null,
    ): StateFlow<TransactionsResult> {
        loadTransactions()
        return if (scopeInfo != null) {
            loadTransactions(scopeInfo, searchQuery)
            mTransactions[scopeInfo]?.asStateFlow()
                ?: MutableStateFlow(TransactionsResult.None)
        } else {
            MutableStateFlow(TransactionsResult.None)
        }
    }

    @UiThread
    fun loadTransactions(
        scopeInfo: ScopeInfo? = null,
        searchQuery: String? = null,
    ) {
        Log.d(TAG, "loadTransactions($scopeInfo, $searchQuery)")
        val s = scopeInfo ?: mSelectedScope.value ?: run {
            MutableStateFlow(TransactionsResult.None)
            return
        }

        // initialize key with empty state flow
        if (mTransactions[s] == null) {
            mTransactions[s] = MutableStateFlow(TransactionsResult.None)
        }

        scope.launch {
            // return cached transactions if available
            if(searchQuery == null) allTransactions[s]?.let { txs ->
                mTransactions[s]?.value = TransactionsResult.Success(txs)
            }

            // ...then fetch new ones
            val res = getTransactions(s, searchQuery)
            if (res is TransactionsResult.Success) {
                allTransactions[s] = res.transactions
            }

            // ...and then emit them when available
            mTransactions[s]?.value = res
        }
    }

    private suspend fun getTransactions(scope: ScopeInfo, searchQuery: String?): TransactionsResult {
        var result: TransactionsResult = TransactionsResult.None
        api.request("getTransactions", Transactions.serializer()) {
            if (searchQuery != null) put("search", searchQuery)
            put("scopeInfo", JSONObject(BackendManager.json.encodeToString(scope)))
        }.onError { error ->
            Log.e(TAG, "Error: getTransactions error result: $error")
            result = TransactionsResult.Error(error)
        }.onSuccess { res ->
            val comparator = compareBy<Transaction> { it.txState.major == Pending }
            result = TransactionsResult.Success(res
                .transactions
                .sortedWith(comparator)
                .reversed())
        }

        return result
    }

    suspend fun getTransactionById(id: String): Transaction? {
        var transaction: Transaction? = null
        api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", id)
        }.onError {
            Log.e(TAG, "Error getting transaction $it")
        }.onSuccess { result ->
            transaction = result
        }

        return transaction
    }

    /**
     * Returns true if given [transactionId] was found and selected, false otherwise.
     */
    @UiThread
    suspend fun selectTransaction(transactionId: String): Boolean {
        val transaction = getTransactionById(transactionId)
        if (transaction != null) {
            mSelectedTransaction.emit(transaction)
            return true
        } else {
            return false
        }
    }

    @UiThread
    fun updateTransactionIfSelected(id: String) = scope.launch {
        val selectedTransaction = selectedTransaction.value
        if (selectedTransaction?.transactionId != id) return@launch
        getTransactionById(id)?.let { tx ->
            if (tx.transactionId == selectedTransaction.transactionId) {
                Log.d(TAG, "updating selected transaction: ${tx.transactionId}")
                mSelectedTransaction.value = tx
            }
        } ?: Log.d(TAG, "Error updating selected transaction $id")
    }

    fun selectTransaction(tx: Transaction) = scope.launch {
        mSelectedTransaction.value = tx
    }

    fun selectScope(scopeInfo: ScopeInfo?) = scope.launch {
        mSelectedScope.value = scopeInfo
    }

    fun setSearchQuery(searchQuery: String?) = scope.launch {
        mSearchQuery.value = searchQuery
    }

    fun deleteTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("deleteTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                // re-load transactions as our list is stale otherwise
                loadTransactions()
            }
        }

    fun retryTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("retryTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                loadTransactions()
            }
        }

    fun abortTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("abortTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                loadTransactions()
            }
        }

    fun failTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("failTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                loadTransactions()
            }
        }

    fun suspendTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("suspendTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                loadTransactions()
            }
        }

    fun resumeTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("resumeTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                loadTransactions()
            }
        }

    fun deleteTransactions(transactionIds: List<String>, onError: (it: TalerErrorInfo) -> Unit) {
        allTransactions[selectedScope.value]?.filter { transaction ->
            transaction.transactionId in transactionIds
        }?.forEach { toBeDeletedTx ->
            if (Delete in toBeDeletedTx.txActions) {
                deleteTransaction(toBeDeletedTx.transactionId) {
                    onError(it)
                }
            }
        }
    }
}