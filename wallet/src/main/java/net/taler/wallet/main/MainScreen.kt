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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.balances.BalancesPagerComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.scan.ScanTab
import net.taler.wallet.transactions.TransactionStateFilter
import net.taler.wallet.transactions.onTransactionClicked
import net.taler.wallet.ui.theme.onPrimaryLight
import net.taler.wallet.ui.theme.primaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onFulfillPayment: (url: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val testWithdrawalMessage = stringResource(R.string.settings_test_withdrawal)
    val networkStatus by model.networkManager.networkStatus.observeAsState(false)
    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)
    val txResults = remember(balanceState) {
        (balanceState as? BalanceState.Success)?.let { state ->
            state.balances.associate { balance ->
                balance.scopeInfo to model.transactionManager.recentTransactionsFlow(balance.scopeInfo)
            }
        } ?: emptyMap()
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

    val tooltipState = rememberTooltipState()
    var fabHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = {},
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = {
                onNavigate(WalletDestination.Settings, false)
            }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.menu_settings))
            }
        },
        floatingActionButton = {
            TooltipBox(
                positionProvider = TooltipDefaults
                    .rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip { Text(stringResource(R.string.button_scan_qr_code_label)) }
                },
                state = tooltipState,
            ) {
                MediumFloatingActionButton(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .onGloballyPositioned { coordinates ->
                            fabHeight = with(   density) { coordinates.size.height.toDp() }
                        },
                    shape = CircleShape,
                    containerColor = primaryLight,
                    contentColor = onPrimaryLight,
                    onClick = {
                        onNavigate(WalletDestination.ScanQr(ScanTab.SCAN_QR), true)
                    },
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        modifier = Modifier.size(34.dp),
                        contentDescription = stringResource(R.string.button_scan_qr_code_label),
                    )
                }
            }
        },
    ) { innerPadding ->
        LaunchedEffect(Unit) {
            val initialLoad = model.balanceManager.state.value is BalanceState.None
            model.balanceManager.loadAssets(initialLoad)
        }

        BalancesPagerComposable(
            innerPadding = innerPadding,
            bottomFabSkip = fabHeight + 24.dp,
            state = balanceState,
            selectedBalance = viewMode.selectedScope,
            recentTxs = txResults,
            devMode = devMode,
            networkStatus = networkStatus,
            onBalanceSelected = {
                model.displayScope(it?.scopeInfo)
            },
            onWithdrawMoney = {
                onNavigate(
                    WalletDestination.HandleUri("taler://withdraw-exchange/exchange.taler-ops.ch/"),
                    true,
                )
            },
            onGetDemoMoney = {
                model.withdrawManager.withdrawTestBalance()
                scope.launch {
                    snackbarHostState.showSnackbar(testWithdrawalMessage)
                }
            },
            onShopping = { balance ->
                model.settingsManager.saveActionButtonUsed(context)
                if (balance.shoppingUrls.size == 1) {
                    onFulfillPayment(balance.shoppingUrls[0])
                } else onNavigate(WalletDestination.ExchangeShopping, false)
            },
            onSend = { onNavigate(WalletDestination.OutgoingPush, true) },
            onRequest = { onNavigate(WalletDestination.OutgoingPull, true) },
            onWithdraw = {
                model.withdrawManager.resetWithdrawal()
                onNavigate(WalletDestination.PromptWithdraw(), true)
            },
            onDeposit = { onNavigate(WalletDestination.Deposit(), true) },
            onTransactionClicked = { tx ->
                onTransactionClicked(tx, model, onNavigate)
            },
            onShowAllTransactions = { scope ->
                onNavigate(WalletDestination.Transactions(), false)
            },
            onShowPendingIncoming = {
                onNavigate(WalletDestination.Transactions(TransactionStateFilter.Nonfinal), false)
            },
            onShowPendingOutgoing = {
                onNavigate(WalletDestination.Transactions(TransactionStateFilter.Nonfinal), false)
            },
            onShowDiscounts = {
                onNavigate(WalletDestination.DiscountList, false)
            },
            onShowPasses = {
                onNavigate(WalletDestination.PassList, false)
            },
        )
    }
}
