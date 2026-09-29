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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import net.taler.wallet.main.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.transactions.TransactionAction.Delete
import org.json.JSONObject

sealed class TransactionsResult {
    data object None : TransactionsResult()
    data class Error(val error: TalerErrorInfo) : TransactionsResult()
    data class Success(val transactions: List<Transaction>) : TransactionsResult()
}

@Serializable
enum class TransactionStateFilter {
    @SerialName("final")
    Final,

    @SerialName("nonfinal")
    Nonfinal,

    @SerialName("done")
    Done;
}

class TransactionManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {
    private companion object {
        /**
         * Number of most recent transactions shown on the balances screen.
         * Negative values mean "the last N transactions" in wallet-core.
         */
        const val RECENT_LIMIT = -4
    }

    private data class FullListParams(
        val searchQuery: String?,
        val stateFilter: TransactionStateFilter?,
    )

    private val recentTransactions = HashMap<ScopeInfo, List<Transaction>>()
    private val fullTransactions = HashMap<ScopeInfo, List<Transaction>>()

    private val mRecentTransactions = HashMap<ScopeInfo, MutableStateFlow<TransactionsResult>>()
    private val mTransactions = HashMap<ScopeInfo, MutableStateFlow<TransactionsResult>>()

    /** In-flight loads per scope, so a newer request cancels an older one. */
    private val recentLoadJobs = HashMap<ScopeInfo, Job>()
    private val fullLoadJobs = HashMap<ScopeInfo, Job>()

    /** Parameters of the full list requested per scope, used to refresh it later. */
    private val fullListParams = HashMap<ScopeInfo, FullListParams>()

    private val mSelectedTransaction = MutableStateFlow<Transaction?>(null)
    private val mSelectedTransactionId = MutableStateFlow<String?>(null)
    private val selectionMutex = Mutex()
    private val mSelectedTransferOption = MutableStateFlow<TransferOption?>(null)

    val selectedTransaction = mSelectedTransaction.asStateFlow()
    val selectedTransferOption = mSelectedTransferOption.asStateFlow()

    /**
     * Streams the [RECENT_LIMIT] most recent transactions for [scopeInfo], as
     * shown on the balances screen.
     */
    fun recentTransactionsFlow(scopeInfo: ScopeInfo): StateFlow<TransactionsResult> {
        val flow = mRecentTransactions.getOrPut(scopeInfo) {
            MutableStateFlow(TransactionsResult.None)
        }
        loadRecentTransactions(scopeInfo)
        return flow.asStateFlow()
    }

    /**
     * Streams the full transaction list for [scopeInfo], as shown on the
     * transactions screen. [searchQuery] and [stateFilter] narrow the request.
     */
    fun transactionsFlow(
        scopeInfo: ScopeInfo?,
        searchQuery: String? = null,
        stateFilter: TransactionStateFilter? = null,
    ): StateFlow<TransactionsResult> {
        if (scopeInfo == null) return MutableStateFlow(TransactionsResult.None)
        fullListParams[scopeInfo] = FullListParams(searchQuery, stateFilter)
        val flow = mTransactions.getOrPut(scopeInfo) {
            MutableStateFlow(TransactionsResult.None)
        }
        loadFullTransactions(scopeInfo, searchQuery, stateFilter)
        return flow.asStateFlow()
    }

    private fun loadRecentTransactions(scopeInfo: ScopeInfo) {
        val flow = mRecentTransactions[scopeInfo] ?: return
        recentLoadJobs[scopeInfo]?.cancel()
        recentLoadJobs[scopeInfo] = scope.launch {
            // show cached data immediately...
            recentTransactions[scopeInfo]?.let {
                flow.value = TransactionsResult.Success(it)
            }
            // ...then fetch the latest and update the cache
            val result = getTransactions(scopeInfo, searchQuery = null, limit = RECENT_LIMIT)
            if (result is TransactionsResult.Success) {
                recentTransactions[scopeInfo] = result.transactions
            }
            flow.value = result
        }
    }

    private fun loadFullTransactions(
        scopeInfo: ScopeInfo,
        searchQuery: String?,
        stateFilter: TransactionStateFilter?,
    ) {
        val flow = mTransactions[scopeInfo] ?: return
        // Only the unfiltered list is cached, since filters can change.
        val cacheable = searchQuery == null && stateFilter == null
        fullLoadJobs[scopeInfo]?.cancel()
        fullLoadJobs[scopeInfo] = scope.launch {
            if (cacheable) fullTransactions[scopeInfo]?.let {
                flow.value = TransactionsResult.Success(it)
            }
            val result = getTransactions(scopeInfo, searchQuery, stateFilter)
            if (result is TransactionsResult.Success && cacheable) {
                fullTransactions[scopeInfo] = result.transactions
            }
            flow.value = result
        }
    }

    /**
     * Handles a transaction-state-transition notification for [transactionId] by
     * refreshing the selected transaction (if it is being viewed) and both the
     * recent and full transaction lists of the affected scopes. Lists that have
     * not been requested yet are left untouched.
     */
    fun onTransactionStateTransition(transactionId: String) = scope.launch {
        val transaction = getTransactionById(transactionId)

        if (transaction != null && mSelectedTransactionId.value == transactionId) {
            selectionMutex.withLock {
                if (mSelectedTransactionId.value == transactionId) {
                    mSelectedTransaction.value = transaction
                }
            }
        }

        if (transaction == null) return@launch
        transaction.scopes.forEach { scopeInfo ->
            loadRecentTransactions(scopeInfo)
            val params = fullListParams[scopeInfo]
            loadFullTransactions(scopeInfo, params?.searchQuery, params?.stateFilter)
        }
    }

    private suspend fun getTransactions(
        scope: ScopeInfo,
        searchQuery: String?,
        filterByState: TransactionStateFilter? = null,
        offsetTransactionId: String? = null,
        limit: Int? = null,
    ): TransactionsResult {
        var result: TransactionsResult = TransactionsResult.None
        api.request("getTransactionsV2", Transactions.serializer()) {
            if (searchQuery != null) put("search", searchQuery)
            if (filterByState != null) put(
                "filterByState",
                BackendManager.json
                    .encodeToJsonElement(filterByState)
                    .jsonPrimitive.content,
            )
            if (offsetTransactionId != null) put("offsetTransactionId", offsetTransactionId)
            if (limit != null) put("limit", limit)
            put("scopeInfo", JSONObject(BackendManager.json.encodeToString(scope)))
        }.onError { error ->
            Log.e(TAG, "Error: getTransactions error result: $error")
            result = TransactionsResult.Error(error)
        }.onSuccess { res ->
            result = if (limit == null || limit >= 0) {
                TransactionsResult.Success(res
                    .transactions
                    .reversed())
            } else {
                TransactionsResult.Success(res.transactions)
            }
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
     *
     * The [transactionId] is recorded as selected before the transaction is fetched,
     * so that a notification arriving while the fetch is in flight is not dropped.
     * The initial fetch and all notification-driven refreshes are serialized via
     * [selectionMutex] so that an older snapshot cannot overwrite a newer one.
     */
    suspend fun selectTransaction(transactionId: String): Boolean {
        mSelectedTransactionId.value = transactionId
        return refreshSelectedTransaction()
    }

    private suspend fun refreshSelectedTransaction(): Boolean {
        val transactionId = mSelectedTransactionId.value ?: return false
        return selectionMutex.withLock {
            val transaction = getTransactionById(transactionId)
            if (transaction != null) {
                if (mSelectedTransactionId.value == transactionId) {
                    mSelectedTransaction.value = transaction
                }
                true
            } else {
                false
            }
        }
    }

    fun selectTransferOption(option: TransferOption) {
        mSelectedTransferOption.value = option
    }

    fun selectTransaction(tx: Transaction?) = scope.launch {
        if (tx == null) {
            mSelectedTransactionId.value = null
            mSelectedTransaction.value = null
        } else {
            mSelectedTransactionId.value = tx.transactionId
            mSelectedTransaction.value = tx
        }
    }

    fun deleteTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("deleteTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                // re-load transactions as our list is stale otherwise
                refreshAllTransactions()
            }
        }

    fun retryTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("retryTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                refreshAllTransactions()
            }
        }

    fun abortTransaction(
        transactionId: String,
        onSuccess: () -> Unit,
        onError: (it: TalerErrorInfo) -> Unit,
    ) =
        scope.launch {
            api.request<Unit>("abortTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                onSuccess()
                refreshAllTransactions()
            }
        }

    fun failTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("failTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                refreshAllTransactions()
            }
        }

    fun suspendTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("suspendTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                refreshAllTransactions()
            }
        }

    fun resumeTransaction(transactionId: String, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            api.request<Unit>("resumeTransaction") {
                put("transactionId", transactionId)
            }.onError {
                onError(it)
            }.onSuccess {
                refreshAllTransactions()
            }
        }

    fun deleteTransactions(transactionIds: List<String>, onError: (it: TalerErrorInfo) -> Unit) =
        scope.launch {
            fullTransactions.values.flatten().filter { transaction ->
                transaction.transactionId in transactionIds && Delete in transaction.txActions
            }.forEach { toBeDeletedTx ->
                api.request<Unit>("deleteTransaction") {
                    put("transactionId", toBeDeletedTx.transactionId)
                }.onError {
                    onError(it)
                }
            }
            refreshAllTransactions()
        }

    /** Reloads every transaction list that has been requested so far. */
    private fun refreshAllTransactions() {
        mRecentTransactions.keys.toList().forEach { loadRecentTransactions(it) }
        mTransactions.keys.toList().forEach { scopeInfo ->
            val params = fullListParams[scopeInfo]
            loadFullTransactions(scopeInfo, params?.searchQuery, params?.stateFilter)
        }
    }
}
