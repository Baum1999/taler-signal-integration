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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.balances.LegacyBalancesComposable
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.scan.ScanTab
import net.taler.wallet.settings.SettingsContent
import net.taler.wallet.transactions.LegacyTransactionsComposable
import net.taler.wallet.transactions.TransactionStateFilter
import net.taler.wallet.transactions.TransactionsResult
import net.taler.wallet.transactions.onTransactionClicked

private enum class LegacyMainTab { ASSETS, SETTINGS }

/**
 * Main screen as it looked before the paginated balances redesign.
 *
 * This is intentionally self-contained so it can be deleted together with the
 * developer switch once [NewMainScreen] is ready to become the only main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyMainScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onFulfillPayment: (url: String) -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(LegacyMainTab.ASSETS) }
    var showTransactions by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var stateFilter by remember { mutableStateOf<TransactionStateFilter?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val testWithdrawalMessage = stringResource(R.string.settings_test_withdrawal)
    val networkStatus by model.networkManager.networkStatus.observeAsState(false)
    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)
    val actionButtonUsed by remember {
        model.settingsManager.getActionButtonUsed(context)
    }.collectAsStateLifecycleAware(true)
    val txResult by remember(viewMode.selectedScope, stateFilter) {
        model.transactionManager.transactionsFlow(viewMode.selectedScope, stateFilter = stateFilter)
    }.collectAsStateLifecycleAware()
    val selectedBalance = remember(balanceState, viewMode.selectedScope) {
        (balanceState as? BalanceState.Success)?.balances?.find {
            it.scopeInfo == viewMode.selectedScope
        }
    }
    val disableActions = remember(balanceState, networkStatus) {
        val hasSpendableBalance = (balanceState as? BalanceState.Success)?.balances
            ?.any { it.scopeInfo !is ScopeInfo.ExchangeLegacyKeys } ?: false
        !networkStatus || !hasSpendableBalance
    }

    val showMessageEvent by model.showMessageEvent.observeAsState()
    LaunchedEffect(showMessageEvent) {
        showMessageEvent?.getIfNotConsumed()?.let { notify ->
            val result = snackbarHostState.showSnackbar(
                message = notify.content,
                actionLabel = notify.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (notify.actionLabel == "View") {
                    onNavigate(WalletDestination.Mailbox, false)
                }
                notify.onAction?.invoke()
            }
        }
    }

    val title = when (tab) {
        LegacyMainTab.ASSETS -> when {
            showTransactions -> if (selectionMode) {
                stringResource(R.string.selection_count, selectedItems.size)
            } else stringResource(R.string.transactions_title)

            !balanceState.showWelcome() -> stringResource(R.string.balances_title)
            else -> null
        }

        LegacyMainTab.SETTINGS -> stringResource(R.string.menu_settings)
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

    BackHandler(selectionMode || (tab == LegacyMainTab.ASSETS && showTransactions)) {
        if (selectionMode) {
            selectionMode = false
            selectedItems.clear()
        } else {
            showTransactions = false
            model.showAssets()
        }
    }

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = title?.let { { Text(it) } },
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text(stringResource(R.string.balances_title)) },
                    selected = tab == LegacyMainTab.ASSETS,
                    onClick = {
                        tab = LegacyMainTab.ASSETS
                        showTransactions = false
                        selectionMode = false
                        selectedItems.clear()
                        model.showAssets()
                    },
                )

                TalerActionButton(
                    demandAttention = !actionButtonUsed,
                    onShowSheet = { showSheet = true },
                    onScanQr = {
                        model.settingsManager.saveActionButtonUsed(context)
                        onNavigate(WalletDestination.ScanQr(ScanTab.SCAN_QR), true)
                    },
                )

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = tab == LegacyMainTab.SETTINGS,
                    onClick = {
                        tab = LegacyMainTab.SETTINGS
                        showTransactions = false
                        selectionMode = false
                        selectedItems.clear()
                    },
                )
            }
        },
    ) { innerPadding ->
        LaunchedEffect(Unit) {
            val initialLoad = model.balanceManager.state.value is BalanceState.None
            model.balanceManager.loadAssets(initialLoad)
        }

        when (tab) {
            LegacyMainTab.ASSETS -> if (showTransactions && selectedBalance != null) {
                LegacyTransactionsComposable(
                    innerPadding = innerPadding,
                    balance = selectedBalance,
                    txResult = txResult,
                    stateFilter = stateFilter,
                    onTransactionClick = { tx ->
                        onTransactionClicked(tx, model, onNavigate)
                    },
                    onShowBalancesClicked = {
                        showTransactions = false
                        model.showAssets()
                    },
                    selectionMode = selectionMode,
                    selectedItems = selectedItems,
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
            } else {
                LegacyBalancesComposable(
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
                    onBalanceClicked = { balance ->
                        model.displayScope(balance.scopeInfo)
                        stateFilter = null
                        showTransactions = true
                    },
                    onPendingClicked = { balance ->
                        model.displayScope(balance.scopeInfo)
                        stateFilter = TransactionStateFilter.Nonfinal
                        showTransactions = true
                    },
                    onStatementClicked = { host ->
                        onNavigate(WalletDestination.DonauStatement(host), false)
                    },
                    onShowDiscounts = {
                        onNavigate(WalletDestination.DiscountList, false)
                    },
                    onShowPasses = {
                        onNavigate(WalletDestination.PassList, false)
                    },
                )
            }

            LegacyMainTab.SETTINGS -> SettingsContent(
                model = model,
                innerPadding = innerPadding,
                snackbarHostState = snackbarHostState,
                onNavigate = onNavigate,
                onShowError = onShowError,
            )
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
