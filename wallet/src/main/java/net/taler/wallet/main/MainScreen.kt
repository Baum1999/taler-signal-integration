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

package net.taler.wallet.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.balances.BalancesComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.scan.ScanTab
import net.taler.wallet.settings.SettingsScreen
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionPeerPullDebit
import net.taler.wallet.transactions.TransactionPeerPushCredit
import net.taler.wallet.transactions.TransactionState
import net.taler.wallet.transactions.TransactionStateFilter.Nonfinal
import net.taler.wallet.transactions.TransactionsComposable
import net.taler.wallet.transactions.TransactionsResult

enum class MainTab { ASSETS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onFulfillPayment: (url: String) -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.ASSETS) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val testWithdrawalMessage = stringResource(R.string.settings_test_withdrawal)
    val online by model.networkManager.networkStatus.observeAsState(false)
    val networkStatus by model.networkManager.networkStatus.observeAsState(false)
    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)
    val txResult by remember(viewMode) {
        val v = viewMode as? ViewMode.Transactions
        model.transactionManager.transactionsFlow(v?.selectedScope, stateFilter = v?.stateFilter)
    }.collectAsStateLifecycleAware()
    val actionButtonUsed by remember {
        model.settingsManager.getActionButtonUsed(context)
    }.collectAsStateLifecycleAware(true)

    val tabTitle = when (tab) {
        MainTab.ASSETS -> when (viewMode) {
            is ViewMode.Transactions -> if (selectionMode) {
                stringResource(R.string.selection_count, selectedItems.size)
            } else stringResource(R.string.transactions_title)

            ViewMode.Assets -> if (!balanceState.showWelcome()) {
                stringResource(R.string.balances_title)
            } else null
        }

        MainTab.SETTINGS -> stringResource(R.string.menu_settings)
    }

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = tabTitle?.let { { Text(it) } },
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text(stringResource(R.string.balances_title)) },
                    selected = tab == MainTab.ASSETS,
                    onClick = {
                        tab = MainTab.ASSETS
                        if (viewMode !is ViewMode.Assets)
                            model.showAssets()
                    }
                )

                TalerActionButton(
                    demandAttention = !actionButtonUsed,
                    onShowSheet = {
                        showSheet = true
                    },
                    onScanQr = {
                        model.settingsManager.saveActionButtonUsed(context)
                        onNavigate(WalletDestination.ScanQr(ScanTab.SCAN_QR), true)
                    },
                )

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = tab == MainTab.SETTINGS,
                    onClick = { tab = MainTab.SETTINGS },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LaunchedEffect(Unit) {
            model.balanceManager.loadAssets(model.viewMode.value is ViewMode.Assets)
        }

        LaunchedEffect(Unit) {
            val viewMode = model.settingsManager.getViewMode(context).first()
            model.setViewMode(viewMode)
        }

        LaunchedEffect(viewMode) {
            model.settingsManager.saveViewMode(context, viewMode)
        }

        BackHandler(selectionMode || (tab == MainTab.ASSETS && viewMode !is ViewMode.Assets)) {
            if (selectionMode) {
                selectionMode = false
                selectedItems.clear()
            } else {
                model.showAssets()
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

        when (tab) {
            MainTab.ASSETS -> AnimatedContent(
                targetState = viewMode,
                modifier = Modifier.fillMaxSize(),
                label = "MainViewMode",
            ) { targetViewMode ->
                when (targetViewMode) {
                    is ViewMode.Assets -> BalancesComposable(
                        innerPadding = innerPadding,
                        state = balanceState,
                        devMode = devMode,
                        networkStatus = networkStatus,
                        onWithdrawMoneyClicked = {
                            onNavigate(
                                WalletDestination.HandleUri("taler://withdraw-exchange/exchange.taler-ops.ch/"),
                                true,
                            )
                        },
                        onGetDemoMoneyClicked = {
                            model.withdrawManager.withdrawTestBalance()
                            scope.launch {
                                snackbarHostState.showSnackbar(testWithdrawalMessage)
                            }
                        },
                        onBalanceClicked = {
                            model.showTransactions(it.scopeInfo)
                        },
                        onPendingClicked = {
                            model.showTransactions(it.scopeInfo, Nonfinal)
                        },
                        onStatementClicked = {
                            onNavigate(WalletDestination.DonauStatement(it), false)
                        },
                        onShowDiscounts = {
                            onNavigate(WalletDestination.DiscountList, false)
                        },
                        onShowPasses = {
                            onNavigate(WalletDestination.PassList, false)
                        },
                    )

                    is ViewMode.Transactions -> {
                        val balance = remember(balanceState, targetViewMode) {
                            (balanceState as? BalanceState.Success)?.balances?.find {
                                it.scopeInfo == targetViewMode.selectedScope
                            }
                        }

                        if (balance != null) TransactionsComposable(
                            innerPadding = innerPadding,
                            viewMode = targetViewMode,
                            balance = balance,
                            txResult = txResult,
                            selectionMode = selectionMode,
                            selectedItems = selectedItems,
                            onTransactionClick = { tx ->
                                onTransactionClicked(tx, model, onNavigate)
                            },
                            onShowBalancesClicked = {
                                model.showAssets()
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
            }

            MainTab.SETTINGS -> SettingsScreen(
                model = model,
                innerPadding = innerPadding,
                snackbarHostState = snackbarHostState,
                onNavigate = onNavigate,
                onShowError = onShowError,
            )
        }

        val disableActions = remember(balanceState, online) {
            !online || (balanceState as? BalanceState.Success)?.balances?.isEmpty() ?: true
        }

        val selectedScope = (viewMode as? ViewMode.Transactions)?.selectedScope

        val selectedBalance = remember(balanceState, selectedScope) {
            val balances = (balanceState as? BalanceState.Success)?.balances
            selectedScope?.let {
                balances?.find { it.scopeInfo == selectedScope }
            }
        }

        TalerActionsModal(
            showSheet = showSheet,
            sheetState = sheetState,
            selectedCurrency = selectedBalance?.currency,
            showShopping = selectedBalance?.shoppingUrls?.isNotEmpty() == true,
            onDismiss = {
                showSheet = false
            },
            disableActions = disableActions,
            disablePeer = selectedBalance?.disablePeerPayments == true,
            onSend = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                onNavigate(WalletDestination.OutgoingPush, true)
            },
            onReceive = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                onNavigate(WalletDestination.OutgoingPull, true)
            },
            onScanQr = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                onNavigate(WalletDestination.ScanQr(ScanTab.SCAN_QR), true)
            },
            onDeposit = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                onNavigate(WalletDestination.Deposit(), true)
            },
            onWithdraw = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                model.withdrawManager.resetWithdrawal()
                onNavigate(WalletDestination.PromptWithdraw(), true)
            },
            onEnterUri = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                onNavigate(WalletDestination.ScanQr(ScanTab.ENTER_LINK), true)
            },
            onShoppingDiscovery = {
                showSheet = false
                model.settingsManager.saveActionButtonUsed(context)
                val shoppingUrls = selectedBalance?.shoppingUrls ?: emptyList()
                if (shoppingUrls.size == 1) {
                    onFulfillPayment(shoppingUrls[0])
                } else onNavigate(WalletDestination.ExchangeShopping, false)
            }
        )
    }
}

private fun onTransactionClicked(
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
//            is TransactionPayment -> {
//                model.paymentManager.preparePay(tx.transactionId) {}
//                onNavigate(WalletDestination.PromptPayment, true)
//            }

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