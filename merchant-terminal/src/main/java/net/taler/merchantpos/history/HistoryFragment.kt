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

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.flow.collectLatest
import net.taler.lib.android.toRelativeTime
import net.taler.merchantpos.MainActivity
import net.taler.merchantlib.OrderHistoryEntry
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.payment.Payment
import net.taler.merchantpos.showPosError

internal interface HistoryActionListener {
    fun onRefundClicked(item: OrderHistoryEntry)
    fun onDeleteClicked(item: OrderHistoryEntry)
    fun onShowPaymentClicked(item: OrderHistoryEntry)
    fun onShowRefundClicked(item: OrderHistoryEntry)
}

class HistoryFragment : Fragment(), HistoryActionListener {

    private val model: MainViewModel by activityViewModels()
    private val historyManager by lazy { model.historyManager }
    private val refundManager by lazy { model.refundManager }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val isLoading by historyManager.isLoading.observeAsState(false)
            val isLoadingMore by historyManager.isLoadingMore.observeAsState(false)
            val result by historyManager.items.observeAsState()
            val pendingRefundOrderId by refundManager.pendingRefundOrderId.observeAsState()
            val activePayment by model.paymentManager.payment.observeAsState()
            val forceDeleteOrderId by historyManager.forceDeleteOrderId.observeAsState()
            HistoryScreen(
                isLoading = isLoading,
                isLoadingMore = isLoadingMore,
                result = result,
                pendingRefundOrderId = pendingRefundOrderId,
                activePayment = activePayment,
                onRefresh = { historyManager.fetchHistory() },
                onLoadMore = historyManager::loadMoreHistoryIfNeeded,
                onRefundClicked = ::onRefundClicked,
                onDeleteClicked = ::onDeleteClicked,
                onShowPaymentClicked = ::onShowPaymentClicked,
                onShowRefundClicked = ::onShowRefundClicked,
            )
            forceDeleteOrderId?.let { orderId ->
                ForceDeleteOrderDialog(
                    onConfirm = { historyManager.forceDeleteOrder(orderId) },
                    onDismiss = { historyManager.clearForceDeletePrompt() },
                )
            }
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                requireActivity().showPosError(error.mainResId, error.msg)
                historyManager.clearError()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (model.configManager.merchantConfig?.baseUrl == null) {
            (requireActivity() as MainActivity).navigateTo(PosDestination.Config)
        } else {
            historyManager.fetchHistory()
        }
    }

    override fun onRefundClicked(item: OrderHistoryEntry) {
        refundManager.startRefund(item)
        (requireActivity() as MainActivity).navigateTo(PosDestination.Refund)
    }

    override fun onDeleteClicked(item: OrderHistoryEntry) {
        historyManager.deleteOrder(item.orderId)
    }

    override fun onShowPaymentClicked(item: OrderHistoryEntry) {
        model.paymentManager.resumePayment(item)
        (requireActivity() as MainActivity).navigateTo(PosDestination.ProcessPayment)
    }

    override fun onShowRefundClicked(item: OrderHistoryEntry) {
        if (refundManager.resumeRefund(item)) {
            (requireActivity() as MainActivity).navigateTo(PosDestination.RefundUri)
        } else {
            requireActivity().showPosError(R.string.refund_state_missing)
            historyManager.fetchHistory()
        }
    }
}

@Composable
private fun HistoryScreen(
    isLoading: Boolean,
    isLoadingMore: Boolean,
    result: HistoryResult?,
    pendingRefundOrderId: String?,
    activePayment: Payment?,
    onRefresh: () -> Unit,
    onLoadMore: (Int) -> Unit,
    onRefundClicked: (OrderHistoryEntry) -> Unit,
    onDeleteClicked: (OrderHistoryEntry) -> Unit,
    onShowPaymentClicked: (OrderHistoryEntry) -> Unit,
    onShowRefundClicked: (OrderHistoryEntry) -> Unit,
) {
    PosTheme {
        val listState = rememberLazyListState()
        val historyItemKeys = (result as? HistoryResult.Success)?.items?.map { it.orderId }.orEmpty()
        val firstHistoryItemKey = historyItemKeys.firstOrNull()

        LaunchedEffect(firstHistoryItemKey) {
            if (firstHistoryItemKey != null) {
                listState.scrollToItem(0)
            }
        }

        LaunchedEffect(listState, historyItemKeys.size) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collectLatest { index ->
                    if (index != null) onLoadMore(index)
                }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading && result !is HistoryResult.Success) {
                CircularProgressIndicator()
            }

            when (result) {
                is HistoryResult.Success -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(result.items, key = { it.orderId }) { item ->
                            HistoryItemCard(
                                item = item,
                                hasPendingRefund = pendingRefundOrderId == item.orderId,
                                activePayment = activePayment?.takeIf { it.orderId == item.orderId },
                                onRefundClicked = { onRefundClicked(item) },
                                onDeleteClicked = { onDeleteClicked(item) },
                                onShowPaymentClicked = { onShowPaymentClicked(item) },
                                onShowRefundClicked = { onShowRefundClicked(item) },
                            )
                        }
                        if (isLoadingMore) {
                            item(key = "loading-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                                    )
                                }
                            }
                        }
                    }
                }

                null -> Unit
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: OrderHistoryEntry,
    hasPendingRefund: Boolean,
    activePayment: Payment?,
    onRefundClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onShowPaymentClicked: () -> Unit,
    onShowRefundClicked: () -> Unit,
) {
    val status = when {
        item.hasPendingRefund -> HistoryStatus.RefundPending
        item.hasRefund -> HistoryStatus.Refunded
        hasPendingRefund -> HistoryStatus.RefundPending
        activePayment?.paid == true -> HistoryStatus.Paid
        activePayment?.claimed == true -> HistoryStatus.PaymentClaimed
        activePayment?.orderId == item.orderId && activePayment.error == null -> HistoryStatus.PaymentPending
        item.paid -> HistoryStatus.Paid
        else -> HistoryStatus.Unpaid
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = item.amount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HistoryStatusBadge(status = status)
                    if (item.hasRefund && item.refundAmount != null) {
                        HistoryAmountBadge(
                            amount = item.refundAmount.toString(),
                            status = status,
                        )
                    }
                }
            }
            Text(
                item.timestamp.ms.toRelativeTime(androidx.compose.ui.platform.LocalContext.current).toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.history_ref_no, item.orderId))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.hasPendingRefund || hasPendingRefund) {
                    Button(onClick = onShowRefundClicked) {
                        Text(stringResource(R.string.history_show_refund))
                    }
                } else if (item.refundable) {
                    Button(onClick = onRefundClicked) {
                        Text(stringResource(R.string.history_refund))
                    }
                } else if (!item.paid) {
                    Button(onClick = onShowPaymentClicked) {
                        Text(stringResource(R.string.history_show_payment))
                    }
                    OutlinedButton(onClick = onDeleteClicked) {
                        Text(stringResource(R.string.order_delete))
                    }
                }
            }
        }
    }
}

private enum class HistoryStatus(
    val labelResId: Int,
) {
    Unpaid(R.string.history_status_unpaid),
    Paid(R.string.history_status_paid),
    PaymentPending(R.string.history_status_payment_pending),
    PaymentClaimed(R.string.history_status_payment_claimed),
    RefundPending(R.string.history_status_refund_pending),
    Refunded(R.string.history_status_refunded),
}

@Composable
private fun HistoryStatusBadge(status: HistoryStatus) {
    val colors = when (status) {
        HistoryStatus.Unpaid -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        HistoryStatus.Paid -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        HistoryStatus.PaymentPending -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        HistoryStatus.PaymentClaimed -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        HistoryStatus.RefundPending -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        HistoryStatus.Refunded -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        color = colors.first,
        contentColor = colors.second,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(status.labelResId),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun HistoryAmountBadge(
    amount: String,
    status: HistoryStatus,
) {
    val colors = when (status) {
        HistoryStatus.RefundPending -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        HistoryStatus.Refunded -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = colors.first,
        contentColor = colors.second,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = amount,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ForceDeleteOrderDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.force_delete_dialog_title)) },
        text = { Text(stringResource(R.string.force_delete_dialog_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.force_delete_dialog_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.payment_cancel))
            }
        },
    )
}
