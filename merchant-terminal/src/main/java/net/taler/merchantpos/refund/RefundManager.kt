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

package net.taler.merchantpos.refund

import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.lib.android.assertUiThread
import net.taler.merchantlib.OrderHistoryEntry
import net.taler.merchantlib.MerchantApi
import net.taler.merchantlib.RefundRequest
import net.taler.merchantpos.config.ConfigManager

sealed class RefundResult {
    class Error(val msg: String) : RefundResult()
    object PastDeadline : RefundResult()
    object AlreadyRefunded : RefundResult()
    class Success(
        val refundUri: String,
        val item: OrderHistoryEntry,
        val amount: Amount,
        val reason: String
    ) : RefundResult()
}

class RefundManager(
    private val configManager: ConfigManager,
    private val scope: CoroutineScope,
    private val api: MerchantApi
) {
    private var refundStatusJob: Job? = null

    var toBeRefunded: OrderHistoryEntry? = null
        private set

    private val mRefundResult = MutableLiveData<RefundResult?>()
    internal val refundResult: LiveData<RefundResult?> = mRefundResult
    private val mPendingRefundOrderId = MutableLiveData<String?>(null)
    internal val pendingRefundOrderId: LiveData<String?> = mPendingRefundOrderId
    private val mRefundReceived = MutableLiveData(false)
    internal val refundReceived: LiveData<Boolean> = mRefundReceived

    @UiThread
    internal fun startRefund(item: OrderHistoryEntry) {
        refundStatusJob?.cancel()
        toBeRefunded = item
        mRefundResult.value = null
        mPendingRefundOrderId.value = null
        mRefundReceived.value = false
    }

    @UiThread
    internal fun abortRefund() {
        refundStatusJob?.cancel()
        toBeRefunded = null
        mRefundResult.value = null
        mPendingRefundOrderId.value = null
        mRefundReceived.value = false
    }

    @UiThread
    internal fun completeRefund() {
        refundStatusJob?.cancel()
        toBeRefunded = null
        mRefundResult.value = null
        mPendingRefundOrderId.value = null
        mRefundReceived.value = false
    }

    @UiThread
    internal fun debugSetRefundResult(result: RefundResult) {
        mRefundResult.value = result
    }

    @UiThread
    internal fun resumeRefund(item: OrderHistoryEntry): Boolean {
        val current = mRefundResult.value as? RefundResult.Success ?: return false
        if (current.item.orderId != item.orderId) return false
        toBeRefunded = item
        mPendingRefundOrderId.value = item.orderId
        if (mRefundReceived.value != true) {
            observeRefundStatus(item.orderId)
        }
        return true
    }

    @UiThread
    internal fun refund(item: OrderHistoryEntry, amount: Amount, reason: String) = scope.launch {
        val merchantConfig = configManager.merchantConfig!!
        val request = RefundRequest(amount, reason)
        api.giveRefund(merchantConfig, item.orderId, request).handle(::onRefundError) {
            assertUiThread()
            mRefundResult.value = RefundResult.Success(
                refundUri = it.talerRefundUri,
                item = item,
                amount = amount,
                reason = reason
            )
            mPendingRefundOrderId.value = item.orderId
            mRefundReceived.value = false
            observeRefundStatus(item.orderId)
        }
    }

    @UiThread
    private fun onRefundError(msg: String) {
        assertUiThread()
        refundStatusJob?.cancel()
        mPendingRefundOrderId.postValue(null)
        mRefundReceived.postValue(false)
        if (msg.contains("2602")) {
            mRefundResult.postValue(RefundResult.AlreadyRefunded)
        } else mRefundResult.postValue(RefundResult.Error(msg))
    }

    @UiThread
    private fun observeRefundStatus(orderId: String) {
        refundStatusJob?.cancel()
        refundStatusJob = scope.launch {
            val merchantConfig = configManager.merchantConfig ?: return@launch
            while (true) {
                var wasRefunded = false
                api.checkOrder(merchantConfig, orderId).handle(null) { response ->
                    assertUiThread()
                    val paidResponse = response as? net.taler.merchantlib.CheckPaymentResponse.Paid
                    if (
                        paidResponse != null &&
                        paidResponse.refunded &&
                        !paidResponse.refundPending &&
                        paidResponse.refundAmount?.isZero() == false
                    ) {
                        mRefundReceived.value = true
                        wasRefunded = true
                    }
                }
                if (wasRefunded || mRefundReceived.value == true) break
                delay(2_000)
            }
        }
    }
}
