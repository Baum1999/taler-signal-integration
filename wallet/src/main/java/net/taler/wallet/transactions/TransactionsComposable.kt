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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.Timestamp
import net.taler.common.toRelativeTime
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceItem
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.SelectionModeTopAppBar
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.AmountType.Negative
import net.taler.wallet.transactions.AmountType.Neutral
import net.taler.wallet.transactions.AmountType.Positive
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.TransactionMajorState.Aborted
import net.taler.wallet.transactions.TransactionMajorState.Aborting
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionMajorState.Failed
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycInit
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycRequired
import net.taler.wallet.transactions.TransactionMinorState.BankConfirmTransfer
import net.taler.wallet.transactions.TransactionMinorState.KycRequired
import net.taler.wallet.transactions.TransactionsResult.Error
import net.taler.wallet.transactions.TransactionsResult.None
import net.taler.wallet.transactions.TransactionsResult.Success

@Composable
fun TransactionsComposable(
    innerPadding: PaddingValues,
    balance: BalanceItem,
    currencySpec: CurrencySpecification?,
    txResult: TransactionsResult,
    onTransactionClick: (tx: Transaction) -> Unit,
    onTransactionsDelete: (txIds: List<String>) -> Unit,
    onShowBalancesClicked: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        var showDeleteDialog by remember { mutableStateOf(false) }
        var selectionMode by remember { mutableStateOf(false) }
        val selectedItems = remember { mutableStateListOf<String>() }

        if (selectionMode && txResult is Success) SelectionModeTopAppBar(
            selectedItems = selectedItems,
            resetSelectionMode = {
                selectionMode = false
                selectedItems.clear()
            },
            onSelectAllClicked = {
                selectedItems.clear()
                selectedItems += txResult.transactions.map { it.transactionId }
            },
            onDeleteClicked = {
                showDeleteDialog = true
            },
        )

        if (showDeleteDialog) AlertDialog(
            title = { Text(stringResource(R.string.transactions_delete_selected_dialog_title)) },
            text = { Text(stringResource(R.string.transactions_delete_selected_dialog_message)) },
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onTransactionsDelete(selectedItems)
                    selectedItems.clear()
                    selectionMode = false
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.transactions_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )

        BackHandler(selectionMode) {
            selectionMode = false
            selectedItems.clear()
        }

        LaunchedEffect(selectionMode, selectedItems.size) {
            if (selectionMode && selectedItems.isEmpty()) {
                selectionMode = false
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .consumeWindowInsets(innerPadding),
            contentPadding = innerPadding,
        ) {
            item {
                TransactionsHeader(
                    balance = balance,
                    spec = currencySpec,
                    onShowBalancesClicked = onShowBalancesClicked,
                )
            }

            val placeholderPadding = PaddingValues(vertical = 50.dp)
            when (txResult) {
                is Success -> if (txResult.transactions.isEmpty()) item {
                    Box(Modifier.padding(placeholderPadding)) {
                        EmptyTransactionsComposable()
                    }
                } else {
                    items(txResult.transactions, key = { it.transactionId }) { tx ->
                        val isSelected = selectedItems.contains(tx.transactionId)

                        TransactionRow(
                            tx, currencySpec,
                            isSelected = isSelected,
                            selectionMode = selectionMode,
                            onTransactionClick = {
                                if (selectionMode) {
                                    if (isSelected) {
                                        selectedItems.remove(tx.transactionId)
                                    } else {
                                        selectedItems.add(tx.transactionId)
                                    }
                                } else {
                                    onTransactionClick(tx)
                                }
                            },
                            onTransactionSelect = {
                                if (selectionMode) {
                                    if (isSelected) {
                                        selectedItems.remove(tx.transactionId)
                                    } else {
                                        selectedItems.add(tx.transactionId)
                                    }
                                } else {
                                    selectionMode = true
                                    selectedItems.add(tx.transactionId)
                                }
                            },
                        )
                    }
                }

                is None -> item {
                    Box(Modifier.padding(placeholderPadding)) {
                        LoadingScreen()
                    }
                }
                is Error -> item {
                    Box(Modifier.padding(placeholderPadding)) {
                        ErrorTransactionsComposable(txResult.error)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTransactionsComposable() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Center,
    ) {
        Text(
            stringResource(R.string.transactions_empty),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ErrorTransactionsComposable(error: TalerErrorInfo) {
    Text(
        text = stringResource(R.string.transactions_error, error.userFacingMsg),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun TransactionsHeader(
    balance: BalanceItem,
    spec: CurrencySpecification?,
    onShowBalancesClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedCard(
            Modifier
                .weight(1f)
                .padding(8.dp)
                .clickable { onShowBalancesClicked() },
        ) {
            ListItem(
                modifier = Modifier.animateContentSize(),
                headlineContent = {
                    Text(
                        getHeaderCurrency(balance, spec),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                supportingContent = {
                    if (balance.scopeInfo is Exchange) {
                        Text(
                            cleanExchange(balance.scopeInfo.url),
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                trailingContent = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                stringResource(R.string.transactions_balance),
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                balance.available.withSpec(spec).toString(showSymbol = false),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    tx: Transaction,
    spec: CurrencySpecification?,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTransactionClick: () -> Unit,
    onTransactionSelect: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column {
        ListItem(
            modifier = Modifier
                .defaultMinSize(minHeight = 80.dp)
                .combinedClickable(
                    onClick = onTransactionClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTransactionSelect()
                    },
                ),
            trailingContent = {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Center,
                ) {
                    TransactionAmountInfo(tx, spec)
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Center,
                ) {
                    if (!selectionMode) {
                        Icon(painterResource(tx.icon), contentDescription = null)
                    } else if (isSelected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    tx.getTitle(context),
                    modifier = Modifier.padding(vertical = 3.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                TransactionExtraInfo(tx)
            },
            overlineContent = { Text(tx.timestamp.ms.toRelativeTime(context).toString()) },
            colors = ListItemDefaults.colors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    ListItemDefaults.containerColor
                }
            )
        )

        HorizontalDivider()
    }
}

@Composable
fun TransactionAmountInfo(
    tx: Transaction,
    spec: CurrencySpecification?,
) {
    Column(horizontalAlignment = Alignment.End) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
            val amountStr = tx.amountEffective.withSpec(spec).toString(showSymbol = false)
            when (tx.amountType) {
                Positive -> Text(
                    stringResource(R.string.amount_positive, amountStr),
                    color = if (tx.txState.major == Pending)
                        Color.Unspecified else colorResource(R.color.green),
                )
                Negative -> Text(
                    stringResource(R.string.amount_negative, amountStr),
                    color = if (tx.txState.major == Pending)
                        Color.Unspecified else MaterialTheme.colorScheme.error,
                )
                Neutral -> Text(amountStr)
            }
        }

        if (tx.txState.major == Pending) {
            Badge(Modifier.padding(top = 3.dp)) {
                Text(stringResource(R.string.transaction_pending))
            }
        }
    }
}

@Composable
fun TransactionExtraInfo(tx: Transaction) {
    when {
        tx.txState.major == Aborted -> Text(
            stringResource(R.string.payment_aborted),
            color = MaterialTheme.colorScheme.error,
        )

        tx.txState.major == Failed -> Text(
            stringResource(R.string.payment_failed),
            color = MaterialTheme.colorScheme.error,
        )

        tx.txState.major == Aborting -> Text(
            stringResource(R.string.payment_aborting),
            color = MaterialTheme.colorScheme.error,
        )

        tx.txState.major == Pending -> when(tx.txState.minor) {
            BankConfirmTransfer -> Text(stringResource(R.string.withdraw_waiting_confirm))
            BalanceKycInit -> Text(stringResource(R.string.transaction_preparing_kyc))
            KycRequired -> Text(stringResource(R.string.transaction_action_kyc_bank))
            BalanceKycRequired -> Text(stringResource(R.string.transaction_action_kyc_balance))
            else -> Text(stringResource(R.string.transaction_pending))
        }

        tx is TransactionWithdrawal && !tx.confirmed -> Text(stringResource(R.string.withdraw_waiting_confirm))
        tx is TransactionPeerPushCredit && tx.info.summary != null -> Text(tx.info.summary)
        tx is TransactionPeerPushDebit && tx.info.summary != null -> Text(tx.info.summary)
        tx is TransactionPeerPullCredit && tx.info.summary != null -> Text(tx.info.summary)
        tx is TransactionPeerPullDebit && tx.info.summary != null -> Text(tx.info.summary)
    }
}

@Composable
private fun getHeaderCurrency(
    balance: BalanceItem,
    spec: CurrencySpecification?,
) = if (spec != null) {
    if (spec.symbol != null && spec.name != spec.symbol) {
        // Name (symbol)
        stringResource(R.string.transactions_currency, spec.name, spec.symbol!!)
    } else if (spec.name != balance.currency) {
        // Name (currency string)
        stringResource(R.string.transactions_currency, spec.name, balance.currency)
    } else balance.currency
} else balance.currency

private val previewBalance = BalanceItem(
    scopeInfo = Exchange("MXN", "https://exchange.taler.banxico.org.mx"),
    available = Amount.fromJSONString("MXN:5.50"),
    pendingIncoming = Amount.fromJSONString("MXN:1.40"),
    pendingOutgoing = Amount.fromJSONString("MXN:0"),
)

@Preview
@Composable
fun TransactionsComposableDonePreview() {
    val t = TransactionWithdrawal(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Done),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.demo.taler.net/",
        withdrawalDetails = WithdrawalDetails.TalerBankIntegrationApi(false),
        amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
    )

    val transactions = listOf(t)

    TalerSurface {
        TransactionsComposable(
            innerPadding = PaddingValues(0.dp),
            balance = previewBalance,
            currencySpec = null,
            txResult = Success(transactions),
            onTransactionClick = {},
            onTransactionsDelete = {},
            onShowBalancesClicked = {},
        )
    }
}

@Preview
@Composable
fun TransactionsComposablePendingPreview() {
    val t = TransactionWithdrawal(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Pending),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.demo.taler.net/",
        withdrawalDetails = WithdrawalDetails.TalerBankIntegrationApi(false),
        amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
    )

    val transactions = listOf(t)

    TalerSurface {
        TransactionsComposable(
            innerPadding = PaddingValues(0.dp),
            balance = previewBalance,
            currencySpec = null,
            txResult = Success(transactions),
            onTransactionClick = {},
            onTransactionsDelete = {},
            onShowBalancesClicked = {},
        )
    }
}

@Preview
@Composable
fun TransactionsComposableEmptyPreview() {
    TalerSurface {
        TransactionsComposable(
            innerPadding = PaddingValues(0.dp),
            balance = previewBalance,
            currencySpec = null,
            txResult = Success(listOf()),
            onTransactionClick = {},
            onTransactionsDelete = {},
            onShowBalancesClicked = {},
        )
    }
}

@Preview
@Composable
fun TransactionsComposableLoadingPreview() {
    TalerSurface {
        TransactionsComposable(
            innerPadding = PaddingValues(0.dp),
            balance = previewBalance,
            currencySpec = null,
            txResult = None,
            onTransactionClick = {},
            onTransactionsDelete = {},
            onShowBalancesClicked = {},
        )
    }
}