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
import net.taler.wallet.balances.BalancesComposable
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.DemandAttention
import net.taler.wallet.compose.GridMenu
import net.taler.wallet.compose.GridMenuItem
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.settings.SettingsFragment
import kotlin.math.roundToInt

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
                var tab by rememberSaveable { mutableStateOf(Tab.BALANCES) }
                var showSheet by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState()

                val settingsFragmentState = rememberFragmentState()

                val context = LocalContext.current
                val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
                val selectedScope by model.transactionManager.selectedScope.collectAsStateLifecycleAware()
                val txResult by remember(selectedScope) { model.transactionManager.transactionsFlow(selectedScope) }.collectAsStateLifecycleAware()
                val selectedSpec = remember(selectedScope) { selectedScope?.let { model.balanceManager.getSpecForScopeInfo(it) } }
                val actionButtonUsed by remember { model.getActionButtonUsed(context) }.collectAsStateLifecycleAware(true)

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                label = { Text(stringResource(R.string.balances_title)) },
                                selected = tab == Tab.BALANCES,
                                onClick = { tab = Tab.BALANCES },
                            )

                            TalerActionButton(
                                demandAttention = !actionButtonUsed,
                                onShowSheet = {
                                    showSheet = true
                                    model.saveActionButtonUsed(context)
                                },
                                onScanQr = {
                                    onScanQr()
                                    model.saveActionButtonUsed(context)
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
                        if (selectedScope == null) {
                            model.transactionManager.selectScope(
                                model.getSelectedScope(context).first()
                            )
                        }
                    }

                    LaunchedEffect(tab, selectedScope) {
                        setTitle(tab, selectedScope)
                    }

                    BackHandler(selectedScope != null) {
                        model.transactionManager.selectScope(null)
                    }

                    when (tab) {
                        Tab.BALANCES -> BalancesComposable(
                            innerPadding = innerPadding,
                            state = balanceState,
                            txResult = txResult,
                            selectedScope = selectedScope,
                            selectedCurrencySpec = selectedSpec,
                            onGetDemoMoneyClicked = {
                                model.withdrawManager.withdrawTestkudos()
                                Snackbar.make(requireView(), getString(R.string.settings_test_withdrawal), LENGTH_LONG).show()
                            },
                            onBalanceClicked = {
                                model.showTransactions(it.scopeInfo)
                            },
                            onTransactionClicked = { tx ->
                                if (tx.detailPageNav != 0) {
                                    model.transactionManager.selectTransaction(tx)
                                    findNavController().navigate(tx.detailPageNav)
                                }
                            },
                            onTransactionsDelete = { txIds ->
                                model.transactionManager.deleteTransactions(txIds) { error ->
                                    Toast.makeText(context, error.userFacingMsg, Toast.LENGTH_LONG).show()
                                }
                            },
                            onShowBalancesClicked = {
                                if (model.transactionManager.selectedScope.value != null) {
                                    model.transactionManager.selectScope(null)
                                }
                            },
                        )
                        Tab.SETTINGS -> SettingsView(
                            innerPadding = innerPadding,
                            settingsFragmentState = settingsFragmentState,
                        )
                    }
                }

                val disableActions = remember(balanceState) {
                    (balanceState as? BalanceState.Success)?.balances?.isEmpty() ?: true
                }

                TalerActionsModal(
                    showSheet = showSheet,
                    sheetState = sheetState,
                    onDismiss = { showSheet = false },
                    disableActions = disableActions,
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
        model.balanceManager.state.observe(viewLifecycleOwner) { res ->
            if (res is BalanceState.Success) {
                if (res.balances.size == 1) {
                    // pre-select on startup if it's the only one
                    model.transactionManager.selectScope(res.balances.first().scopeInfo)
                }
            }
        }
    }

    private fun setTitle(tab: Tab, scope: ScopeInfo?) {
        (requireActivity() as AppCompatActivity).apply {
            supportActionBar?.title = when (tab) {
                Tab.BALANCES -> if (scope != null) {
                    getString(R.string.transactions_title)
                } else {
                    getString(R.string.balances_title)
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
                    title = R.string.enter_uri_label,
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
                    enabled = !disableActions,
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
                    enabled = !disableActions,
                )
            }
        }
    }
}

