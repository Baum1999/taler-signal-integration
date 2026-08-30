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

package net.taler.merchantpos.history

import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.taler.lib.android.assertUiThread
import net.taler.merchantlib.CheckPaymentResponse
import net.taler.merchantlib.MerchantApi
import net.taler.merchantlib.OrderHistoryEntry
import net.taler.merchantpos.R
import net.taler.merchantpos.config.ConfigManager

sealed class HistoryResult {
    class Success(val items: List<OrderHistoryEntry>) : HistoryResult()
}

class HistoryError(
    @StringRes val mainResId: Int,
    val msg: String,
)

class HistoryManager(
    private val configManager: ConfigManager,
    private val scope: CoroutineScope,
    private val api: MerchantApi
) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val LOAD_MORE_THRESHOLD = 5
    }

    private val mIsLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = mIsLoading
    private val mIsLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = mIsLoadingMore

    private val mItems = MutableLiveData<HistoryResult>()
    val items: LiveData<HistoryResult> = mItems
    private val mError = MutableLiveData<HistoryError?>(null)
    val error: LiveData<HistoryError?> = mError

    private val pagination = HistoryPagination()
    private var historyLoadJob: Job? = null
    private var historyLoadGeneration = 0

    @UiThread
    internal fun fetchHistory(onFinished: (() -> Unit)? = null) {
        startHistoryLoad(reset = true, onFinished = onFinished)
    }

    @UiThread
    internal fun loadMoreHistoryIfNeeded(lastVisibleIndex: Int) {
        val currentItems = (mItems.value as? HistoryResult.Success)?.items ?: return
        if (mIsLoading.value == true || mIsLoadingMore.value == true || pagination.reachedEnd) return
        if (currentItems.isEmpty()) return
        if (lastVisibleIndex < currentItems.lastIndex - LOAD_MORE_THRESHOLD) return
        startHistoryLoad(reset = false)
    }

    @UiThread
    private fun startHistoryLoad(reset: Boolean, onFinished: (() -> Unit)? = null) {
        if (!reset && historyLoadJob?.isActive == true) return

        val generation = ++historyLoadGeneration
        historyLoadJob?.cancel()
        historyLoadJob = scope.launch {
            val succeeded = fetchHistoryPages(reset, generation)
            if (succeeded && generation == historyLoadGeneration) onFinished?.invoke()
        }
    }

    /**
     * Publishes the first page immediately, then keeps loading the remaining
     * history sequentially. This lets timestamp sorting cover the complete
     * history without requiring the user to scroll through every page first.
     */
    private suspend fun fetchHistoryPages(reset: Boolean, generation: Int): Boolean {
        if (reset) {
            mIsLoading.value = true
            mIsLoadingMore.value = false
        } else {
            mIsLoadingMore.value = true
        }
        val merchantConfig = configManager.merchantConfig!!

        var resetPage = reset
        while (true) {
            val offset = if (resetPage) null else pagination.nextOffset
            val response = api.getOrderHistory(
                merchantConfig = merchantConfig,
                limit = -PAGE_SIZE,
                offset = offset,
            )

            // A refresh may have superseded this request while it was in flight.
            if (generation != historyLoadGeneration) return false

            var succeeded = false
            response.handle({ onError(R.string.error_history, it) }) { history ->
                assertUiThread()
                succeeded = true
                val newItems = pagination.addPage(history.orders, resetPage)
                val hasMorePages = !pagination.reachedEnd
                mIsLoading.value = false
                mIsLoadingMore.value = hasMorePages
                publishItems()
                enrichOrders(newItems)
            }

            if (!succeeded) return false
            if (pagination.reachedEnd) return true
            resetPage = false
        }
    }

    private val mForceDeleteOrderId = MutableLiveData<String?>(null)
    val forceDeleteOrderId: LiveData<String?> = mForceDeleteOrderId

    @UiThread
    internal fun deleteOrder(orderId: String) = scope.launch {
        mIsLoading.value = true
        mIsLoadingMore.value = false
        val merchantConfig = configManager.merchantConfig!!
        api.deleteOrder(merchantConfig, orderId).handle({ errorMsg ->
            assertUiThread()
            mIsLoading.value = false
            mForceDeleteOrderId.value = orderId
        }) {
            assertUiThread()
            configManager.refreshInventory()
            fetchHistory()
        }
    }

    @UiThread
    internal fun forceDeleteOrder(orderId: String) = scope.launch {
        mForceDeleteOrderId.postValue(null)
        mIsLoading.value = true
        val merchantConfig = configManager.merchantConfig!!
        api.deleteOrder(merchantConfig, orderId, force = true).handle({ errorMsg ->
            onError(R.string.error_delete_order, errorMsg)
        }) {
            assertUiThread()
            configManager.refreshInventory()
            fetchHistory()
        }
    }

    @UiThread
    internal fun clearForceDeletePrompt() {
        mForceDeleteOrderId.value = null
    }

    @UiThread
    internal fun clearError() {
        mError.value = null
    }

    private fun publishItems() {
        mItems.value = HistoryResult.Success(pagination.items)
    }

    @UiThread
    internal fun debugSetHistory(items: List<OrderHistoryEntry>) {
        pagination.replaceItems(items)
        publishItems()
    }

    private fun enrichOrders(items: List<OrderHistoryEntry>) = scope.launch {
        val merchantConfig = configManager.merchantConfig ?: return@launch
        items.filter { it.paid }.forEach { item ->
            api.checkOrder(merchantConfig, item.orderId).handle(null) { response ->
                assertUiThread()
                val paidResponse = response as? CheckPaymentResponse.Paid ?: return@handle
                updateItem(
                    orderId = item.orderId,
                    transform = { current ->
                        current.copy(
                            refunded = paidResponse.refunded,
                            refundAmount = paidResponse.refundAmount ?: current.refundAmount,
                            refundPending = paidResponse.refundPending,
                        )
                    }
                )
            }
        }
    }

    private fun updateItem(
        orderId: String,
        transform: (OrderHistoryEntry) -> OrderHistoryEntry,
    ) {
        if (!pagination.updateItem(orderId, transform)) return
        publishItems()
    }

    private fun onError(@StringRes mainResId: Int, msg: String) {
        assertUiThread()
        mIsLoading.value = false
        mIsLoadingMore.value = false
        mError.value = HistoryError(mainResId, msg)
    }
}

internal class HistoryPagination {
    private val newestFirst = compareByDescending<OrderHistoryEntry> { it.timestamp.ms }
        .thenByDescending { it.rowId ?: Long.MIN_VALUE }
        .thenByDescending { it.orderId }

    private val loadedItems = mutableListOf<OrderHistoryEntry>()

    var nextOffset: Long? = null
        private set

    var reachedEnd: Boolean = false
        private set

    val items: List<OrderHistoryEntry>
        get() = loadedItems.toList()

    /**
     * Adds one backend page and returns only entries that were not loaded before.
     *
     * The backend's limit is an upper bound, so a short page does not prove that
     * iteration is complete. Depending on backend cursor semantics, a page may
     * also repeat the order at [nextOffset]. In that case it is de-duplicated.
     */
    fun addPage(page: List<OrderHistoryEntry>, reset: Boolean): List<OrderHistoryEntry> {
        if (reset) {
            loadedItems.clear()
            nextOffset = null
            reachedEnd = false
        }

        val previousOffset = nextOffset
        val existingOrderIds = loadedItems.mapTo(mutableSetOf()) { it.orderId }
        val newItems = page.filterNot { it.orderId in existingOrderIds }
        loadedItems += newItems
        loadedItems.sortWith(newestFirst)

        // Pagination follows the backend's raw page order, independently from
        // the timestamp order used to present the combined list.
        val pageOffset = page.lastOrNull()?.rowId
        val cursorAdvanced = pageOffset != null && (reset || pageOffset != previousOffset)
        if (pageOffset != null) nextOffset = pageOffset

        reachedEnd = page.isEmpty() || newItems.isEmpty() || !cursorAdvanced
        return newItems
    }

    fun replaceItems(items: List<OrderHistoryEntry>) {
        loadedItems.clear()
        loadedItems.addAll(items)
        loadedItems.sortWith(newestFirst)
        nextOffset = items.lastOrNull()?.rowId
        reachedEnd = false
    }

    fun updateItem(
        orderId: String,
        transform: (OrderHistoryEntry) -> OrderHistoryEntry,
    ): Boolean {
        val index = loadedItems.indexOfFirst { it.orderId == orderId }
        if (index == -1) return false
        loadedItems[index] = transform(loadedItems[index])
        loadedItems.sortWith(newestFirst)
        return true
    }
}
