/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel

@Composable
fun TransactionsScreen(
    model: MainViewModel,
    filter: TransactionStateFilter? = null,
    onShowError: (TalerErrorInfo) -> Unit,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    var selectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val stateFilter by remember { mutableStateOf(filter) }
    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
    val txResult by remember(viewMode, stateFilter) {
        model.transactionManager.transactionsFlow(viewMode.selectedScope, stateFilter = stateFilter)
    }.collectAsStateLifecycleAware()

    val balance = remember(balanceState, viewMode) {
        (balanceState as? BalanceState.Success)?.balances?.find {
            it.scopeInfo == viewMode.selectedScope
        }
    }

    if (showDeleteDialog) AlertDialog(
        title = { Text(stringResource(R.string.transactions_delete_selected_dialog_title)) },
        text = { Text(stringResource(R.string.transactions_delete_selected_dialog_message)) },
        onDismissRequest = { showDeleteDialog = false },
        confirmButton = {
            TextButton(onClick = {
                model.transactionManager.deleteTransactions(selectedItems) { error ->
                    onShowError(error)
                }
                showDeleteDialog = false
                selectionMode = false
                selectedItems.clear()
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

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                text = if (selectionMode) {
                    stringResource(R.string.selection_count, selectedItems.size)
                } else stringResource(R.string.transactions_title),
            )
        },
        navigationIcon = if (selectionMode) {
            {
                IconButton(onClick = {
                    selectionMode = false
                    selectedItems.clear()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                    )
                }
            }
        } else null,
        actions = {
            if (selectionMode && txResult is TransactionsResult.Success) {
                IconButton(onClick = {
                    selectedItems.clear()
                    val successResult = txResult as TransactionsResult.Success
                    selectedItems.addAll(successResult.transactions.map { it.transactionId })
                }) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.transactions_select_all),
                    )
                }
                IconButton(onClick = {
                    showDeleteDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.transactions_delete),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        onNavigateBack = onNavigateBack,
    ) { innerPadding ->
        LaunchedEffect(Unit) {
            model.balanceManager.loadAssets(true)
        }

        if (balance != null) TransactionsComposable(
            innerPadding = innerPadding,
            balance = balance,
            txResult = txResult,
            stateFilter = stateFilter,
            selectionMode = selectionMode,
            selectedItems = selectedItems,
            onTransactionClick = { tx ->
                onTransactionClicked(tx, model, onNavigate)
            },
            onToggleSelection = { txId ->
                if (selectedItems.contains(txId)) {
                    selectedItems.remove(txId)
                    if (selectedItems.isEmpty()) selectionMode = false
                } else {
                    selectionMode = true
                    selectedItems.add(txId)
                }
            },
        )
    }
}

fun onTransactionClicked(
    tx: Transaction,
    model: MainViewModel,
    onNavigate: NavigateCallback,
) {
    val showTxDetails = {
        model.transactionManager.selectTransaction(tx).invokeOnCompletion {
            onNavigate(tx.detailPageNav, false)
        }
    }

    when (tx.txState) {
        // unfinished transactions (dialog)
        TransactionState(TransactionMajorState.Dialog) -> when (tx) {
            is TransactionPeerPushCredit -> {
                model.peerManager.preparePeerPushCredit(transactionId = tx.transactionId)
                onNavigate(WalletDestination.PromptPushPayment, true)
            }

            is TransactionPeerPullDebit -> {
                model.peerManager.preparePeerPullDebit(transactionId = tx.transactionId)
                onNavigate(WalletDestination.PromptPullPayment, true)
            }

            else -> showTxDetails()
        }

        else -> showTxDetails()
    }
}