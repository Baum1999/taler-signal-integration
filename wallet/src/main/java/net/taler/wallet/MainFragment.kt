/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.FragmentState
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.DemandAttention
import net.taler.wallet.compose.GridMenu
import net.taler.wallet.compose.GridMenuItem
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainComposable
import net.taler.wallet.main.ViewMode
import net.taler.wallet.settings.SettingsFragment
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionPayment
import net.taler.wallet.transactions.TransactionState
import net.taler.wallet.transactions.TransactionStateFilter.Nonfinal
import kotlin.math.roundToInt

class MainFragment: Fragment() {

    enum class Tab { ASSETS, SETTINGS }

    private val model: MainViewModel by activityViewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                var tab by rememberSaveable { mutableStateOf(Tab.ASSETS) }
                var showSheet by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState()

                val settingsFragmentState = rememberFragmentState()

                val context = LocalContext.current
                val online by model.networkManager.networkStatus.observeAsState(false)
                val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
                val viewMode by model.viewMode.collectAsStateLifecycleAware()
                val devMode by model.devMode.observeAsState(false)
                val txResult by remember(viewMode) {
                    val v = viewMode as? ViewMode.Transactions
                    model.transactionManager.transactionsFlow(v?.selectedScope, stateFilter = v?.stateFilter)
                }.collectAsStateLifecycleAware()
                val actionButtonUsed by remember { model.settingsManager.getActionButtonUsed(context) }.collectAsStateLifecycleAware(true)

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                label = { Text(stringResource(R.string.assets_title)) },
                                selected = tab == Tab.ASSETS,
                                onClick = {
                                    tab = Tab.ASSETS
                                    if (viewMode !is ViewMode.Assets)
                                        model.showAssets()
                                }
                            )

                            TalerActionButton(
                                demandAttention = !actionButtonUsed,
                                onShowSheet = {
                                    showSheet = true
                                    model.settingsManager.saveActionButtonUsed(context)
                                },
                                onScanQr = {
                                    onScanQr()
                                    model.settingsManager.saveActionButtonUsed(context)
                                },
                            )

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.menu_settings)) },
                                selected = tab == Tab.SETTINGS,
                                onClick = { tab = Tab.SETTINGS },
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ) { innerPadding ->
                    LaunchedEffect(Unit) {
                        val viewMode = model.settingsManager.getViewMode(context).first()
                        model.setViewMode(viewMode)
                    }

                    LaunchedEffect(tab, viewMode) {
                        setTitle(tab, viewMode)
                    }

                    BackHandler(viewMode !is ViewMode.Assets) {
                        model.showAssets()
                    }

                    when (tab) {
                        Tab.ASSETS -> MainComposable(
                            innerPadding = innerPadding,
                            state = balanceState,
                            txResult = txResult,
                            viewMode = viewMode,
                            devMode = devMode,
                            onGetDemoMoneyClicked = {
                                model.withdrawManager.withdrawTestBalance()
                                Snackbar.make(
                                    requireView(),
                                    getString(R.string.settings_test_withdrawal),
                                    LENGTH_LONG
                                ).show()
                            },
                            onBalanceClicked = {
                                model.showTransactions(it.scopeInfo)
                            },
                            onPendingClicked = {
                                model.showTransactions(it.scopeInfo, Nonfinal)
                            },
                            onTransactionClicked = { tx ->
                                onTransactionClicked(tx)
                            },
                            onTransactionsDelete = { txIds ->
                                model.transactionManager.deleteTransactions(txIds) { error ->
                                    Toast.makeText(context, error.userFacingMsg, Toast.LENGTH_LONG)
                                        .show()
                                }
                            },
                            onShowBalancesClicked = {
                                model.showAssets()
                            },
                            onStatementClicked = {
                                findNavController().navigate(
                                    R.id.nav_donau_statement,
                                    bundleOf("host" to it),
                                )
                            }
                        )
                        Tab.SETTINGS -> SettingsView(
                            innerPadding = innerPadding,
                            settingsFragmentState = settingsFragmentState,
                        )
                    }
                }

                val disableActions = remember(balanceState, online) {
                    !online || (balanceState as? BalanceState.Success)?.balances?.isEmpty() ?: true
                }

                val disablePeer = remember(balanceState, viewMode) {
                    val selectedScope = (viewMode as? ViewMode.Transactions)?.selectedScope
                    val balances = (balanceState as? BalanceState.Success)?.balances
                        ?.filter { !it.disablePeerPayments }
                        ?: emptyList()
                    selectedScope?.let {
                        balances.find { it.scopeInfo == selectedScope } == null
                    } ?: balances.isEmpty()
                }

                TalerActionsModal(
                    showSheet = showSheet,
                    sheetState = sheetState,
                    onDismiss = { showSheet = false },
                    disableActions = disableActions,
                    disablePeer = disablePeer,
                    onSend = this@MainFragment::onSend,
                    onReceive = this@MainFragment::onReceive,
                    onScanQr = this@MainFragment::onScanQr,
                    onDeposit = this@MainFragment::onDeposit,
                    onWithdraw = this@MainFragment::onWithdraw,
                    onEnterUri = this@MainFragment::onEnterUri,
                )
            }
        }
    }

    private fun onTransactionClicked(tx: Transaction) {
        val showTxDetails = {
            if (tx.detailPageNav != 0) {
                model.transactionManager.selectTransaction(tx)
                findNavController().navigate(tx.detailPageNav)
            }
        }

        when (tx.txState) {
            // unfinished transactions (dialog)
            TransactionState(TransactionMajorState.Dialog) -> when (tx) {
                is TransactionPayment -> {
                    model.paymentManager.preparePay(tx.transactionId) {
                        findNavController().navigate(R.id.action_global_promptPayment)
                    }
                }

                else -> showTxDetails()
            }

            else -> showTxDetails()
        }
    }

    override fun onStart() {
        super.onStart()
        model.balanceManager.loadAssets(model.viewMode.value is ViewMode.Assets)
    }

    private fun setTitle(tab: Tab, viewMode: ViewMode?) {
        (requireActivity() as AppCompatActivity).apply {
            supportActionBar?.title = when (tab) {
                Tab.ASSETS -> when(viewMode) {
                    is ViewMode.Assets -> getString(R.string.assets_title)
                    is ViewMode.Transactions -> getString(R.string.transactions_title)
                    null -> getString(R.string.loading)
                }

                Tab.SETTINGS -> getString(R.string.menu_settings)
            }
        }
    }

    private fun onSend() {
        findNavController().navigate(R.id.nav_peer_push)
    }

    private fun onReceive() {
        findNavController().navigate(R.id.nav_peer_pull)
    }

    private fun onDeposit() {
        findNavController().navigate(R.id.nav_deposit)
    }

    private fun onWithdraw() {
        model.withdrawManager.resetWithdrawal()
        findNavController().navigate(R.id.promptWithdraw)
    }

    private fun onScanQr() {
        model.scanCode()
    }

    private fun onEnterUri() {
        findNavController().navigate(R.id.nav_uri_input)
    }
}

@Composable
fun SettingsView(
    innerPadding: PaddingValues,
    settingsFragmentState: FragmentState,
) {
    AndroidFragment(
        SettingsFragment::class.java,
        modifier = Modifier.padding(innerPadding),
        fragmentState = settingsFragmentState,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TalerActionButton(
    demandAttention: Boolean,
    onShowSheet: () -> Unit,
    onScanQr: () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.actions)) } },
        state = tooltipState,
    ) {
        val offsetY = remember { Animatable(0f) }
        var cancelled by remember { mutableStateOf(false) }

        DemandAttention(demandAttention = demandAttention) {
            LargeFloatingActionButton(
                modifier = Modifier
                    .requiredSize(86.dp)
                    .padding(8.dp)
                    .offset { IntOffset(0, offsetY.value.roundToInt() / 6) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            runBlocking { offsetY.snapTo(offsetY.value + delta) }
                            if (delta > 0) {
                                cancelled = true
                            }
                        },
                        onDragStopped = {
                            offsetY.animateTo(0.0f)
                            if (!cancelled) {
                                onScanQr()
                            }
                            cancelled = false
                        },
                    ),
                shape = CircleShape,
                onClick = { onShowSheet() },
            ) {
                if (offsetY.value == 0.0f) {
                    Icon(
                        painterResource(R.drawable.ic_actions),
                        modifier = Modifier.size(38.dp),
                        contentDescription = stringResource(R.string.actions),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_scan_qr),
                        contentDescription = stringResource(R.string.actions),
                    )
                }
            }
        }
    }

    LaunchedEffect(demandAttention) {
        if (demandAttention) {
            tooltipState.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalerActionsModal(
    showSheet: Boolean,
    sheetState: SheetState,
    disableActions: Boolean,
    disablePeer: Boolean,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onScanQr: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEnterUri: () -> Unit,
) {
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            GridMenu(
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 16.dp + WindowInsets
                        .systemBars
                        .asPaddingValues()
                        .calculateBottomPadding(),
                ),
            ) {
                GridMenuItem(
                    icon = R.drawable.ic_link,
                    title = R.string.enter_uri,
                    onClick = { onEnterUri(); onDismiss() },
                )

                GridMenuItem(
                    icon = R.drawable.transaction_deposit,
                    title = R.string.send_deposit_button_label,
                    onClick = { onDeposit(); onDismiss() },
                    enabled = !disableActions
                )

                GridMenuItem(
                    icon = R.drawable.ic_scan_qr,
                    title = R.string.button_scan_qr_code_label,
                    onClick = { onScanQr(); onDismiss() },
                )

                GridMenuItem(
                    icon = R.drawable.transaction_p2p_incoming,
                    title = R.string.transactions_receive_funds,
                    onClick = { onReceive(); onDismiss() },
                    enabled = !disableActions && !disablePeer,
                )

                GridMenuItem(
                    icon = R.drawable.transaction_withdrawal,
                    title = R.string.withdraw_button_label,
                    onClick = { onWithdraw(); onDismiss() },
                    enabled = !disableActions,
                )

                GridMenuItem(
                    icon = R.drawable.transaction_p2p_outgoing,
                    title = R.string.transactions_send_funds,
                    onClick = { onSend(); onDismiss() },
                    enabled = !disableActions && !disablePeer,
                )
            }
        }
    }
}