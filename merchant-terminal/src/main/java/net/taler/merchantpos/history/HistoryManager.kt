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

    private val loadedItems = mutableListOf<OrderHistoryEntry>()
    private var nextOffset: Long? = null
    private var reachedEnd = false

    @UiThread
    internal fun fetchHistory() = fetchHistoryPage(reset = true)

    @UiThread
    internal fun loadMoreHistoryIfNeeded(lastVisibleIndex: Int) {
        val currentItems = (mItems.value as? HistoryResult.Success)?.items ?: return
        if (mIsLoading.value == true || mIsLoadingMore.value == true || reachedEnd) return
        if (currentItems.isEmpty()) return
        if (lastVisibleIndex < currentItems.lastIndex - LOAD_MORE_THRESHOLD) return
        fetchHistoryPage(reset = false)
    }

    @UiThread
    private fun fetchHistoryPage(reset: Boolean) = scope.launch {
        if (reset) {
            mIsLoading.value = true
            mIsLoadingMore.value = false
        } else {
            mIsLoadingMore.value = true
        }
        val merchantConfig = configManager.merchantConfig!!
        val offset = if (reset) null else nextOffset
        api.getOrderHistory(
            merchantConfig = merchantConfig,
            limit = -PAGE_SIZE,
            offset = offset,
        ).handle({ onError(R.string.error_history, it) }) { response ->
            assertUiThread()
            mIsLoading.value = false
            mIsLoadingMore.value = false
            if (reset) {
                loadedItems.clear()
                reachedEnd = false
            }
            val page = response.orders
            val existingOrderIds = loadedItems.mapTo(mutableSetOf()) { it.orderId }
            val newItems = page.filterNot { it.orderId in existingOrderIds }
            if (newItems.isNotEmpty()) {
                loadedItems += newItems
                nextOffset = newItems.lastOrNull()?.rowId ?: loadedItems.lastOrNull()?.rowId
            }
            if (page.size < PAGE_SIZE || newItems.isEmpty()) {
                reachedEnd = true
            }
            publishItems()
            enrichOrders(newItems)
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
        mItems.value = HistoryResult.Success(loadedItems.toList())
    }

    @UiThread
    internal fun debugSetHistory(items: List<OrderHistoryEntry>) {
        loadedItems.clear()
        loadedItems.addAll(items)
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
        val index = loadedItems.indexOfFirst { it.orderId == orderId }
        if (index == -1) return
        loadedItems[index] = transform(loadedItems[index])
        publishItems()
    }

    private fun onError(@StringRes mainResId: Int, msg: String) {
        assertUiThread()
        mIsLoading.value = false
        mIsLoadingMore.value = false
        mError.value = HistoryError(mainResId, msg)
    }
}
