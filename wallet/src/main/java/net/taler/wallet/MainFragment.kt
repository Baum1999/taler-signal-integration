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

package net.taler.wallet

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.FragmentState
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation.fragment.findNavController
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.balances.BalancesComposable
import net.taler.wallet.compose.DemandAttention
import net.taler.wallet.compose.GridMenu
import net.taler.wallet.compose.GridMenuItem
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.settings.SettingsFragment
import net.taler.wallet.transactions.TransactionsResult

class MainFragment: Fragment() {

    enum class Tab { BALANCES, SETTINGS }

    private val model: MainViewModel by activityViewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                var selectedTab by rememberSaveable { mutableStateOf(Tab.BALANCES) }
                var showSheet by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState()

                val settingsFragmentState = rememberFragmentState()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                label = { Text(stringResource(R.string.balances_title)) },
                                selected = selectedTab == Tab.BALANCES,
                                onClick = { selectedTab = Tab.BALANCES },
                            )

                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.actions)) } },
                                state = rememberTooltipState(),
                            ) {
                                DemandAttention {
                                    LargeFloatingActionButton(
                                        modifier = Modifier
                                            .requiredSize(86.dp)
                                            .padding(8.dp)
                                            .draggable(
                                                orientation = Orientation.Vertical,
                                                state = rememberDraggableState { },
                                                onDragStopped = { onScanQr() },
                                            ),
                                        shape = CircleShape,
                                        onClick = { showSheet = true },
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_actions),
                                            modifier = Modifier.size(38.dp),
                                            contentDescription = stringResource(R.string.actions),
                                        )
                                    }
                                }
                            }

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.menu_settings)) },
                                selected = selectedTab == Tab.SETTINGS,
                                onClick = { selectedTab = Tab.SETTINGS },
                            )
                        }
                    }
                ) { innerPadding ->
                    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
                    val txResult by model.transactionManager.transactions.observeAsState(TransactionsResult.None)
                    val selectedScope by model.transactionManager.selectedScope.observeAsState()
                    val selectedSpec = remember(selectedScope) { selectedScope?.let { model.balanceManager.getSpecForScopeInfo(it) } }
                    Box(Modifier.padding(innerPadding).fillMaxSize()) {
                        when (selectedTab) {
                            Tab.BALANCES -> BalancesComposable(
                                state = balanceState,
                                txResult = txResult,
                                selectedScope = selectedScope,
                                selectedCurrencySpec = selectedSpec,
                                onBalanceClicked = {
                                    model.showTransactions(it.scopeInfo)
                                },
                                onTransactionClicked = { tx ->
                                    if (tx.detailPageNav != 0) {
                                        model.transactionManager.selectTransaction(tx)
                                        findNavController().navigate(tx.detailPageNav)
                                    }
                                },
                                onShowBalancesClicked = {
                                    if (model.transactionManager.selectedScope.value != null) {
                                        model.transactionManager.selectedScope.value = null
                                    }
                                },
                            )
                            Tab.SETTINGS -> SettingsView(
                                settingsFragmentState = settingsFragmentState,
                            )
                        }
                    }
                }

                TalerActionsModal(
                    showSheet = showSheet,
                    sheetState = sheetState,
                    onDismiss = { showSheet = false },
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

    override fun onStart() {
        super.onStart()
        model.balanceManager.loadBalances()
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
    settingsFragmentState: FragmentState,
) {
    AndroidFragment(
        SettingsFragment::class.java,
        modifier = Modifier.fillMaxSize(),
        fragmentState = settingsFragmentState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalerActionsModal(
    showSheet: Boolean,
    sheetState: SheetState,
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
                    icon = R.drawable.transaction_p2p_outgoing,
                    title = R.string.transactions_send_funds,
                    onClick = onSend,
                )

                GridMenuItem(
                    icon = R.drawable.transaction_p2p_incoming,
                    title = R.string.transactions_receive_funds,
                    onClick = onReceive,
                )

                GridMenuItem(
                    icon = R.drawable.ic_scan_qr,
                    title = R.string.button_scan_qr_code_label,
                    onClick = onScanQr,
                )

                GridMenuItem(
                    icon = R.drawable.transaction_deposit,
                    title = R.string.send_deposit_button_label,
                    onClick = onDeposit,
                )

                GridMenuItem(
                    icon = R.drawable.transaction_withdrawal,
                    title = R.string.withdraw_button_label,
                    onClick = onWithdraw,
                )

                GridMenuItem(
                    icon = R.drawable.ic_link,
                    title = R.string.enter_uri_label,
                    onClick = onEnterUri,
                )
            }
        }
    }
}

