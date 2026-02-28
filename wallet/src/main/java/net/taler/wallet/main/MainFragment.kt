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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
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
import net.taler.wallet.R
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.settings.SettingsFragment
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionPayment
import net.taler.wallet.transactions.TransactionState
import net.taler.wallet.transactions.TransactionStateFilter.Nonfinal

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
                val networkStatus by model.networkManager.networkStatus.observeAsState(false)
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
                                },
                                onScanQr = {
                                    onScanQr()
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

                    LaunchedEffect(tab, balanceState, viewMode) {
                        (requireActivity() as AppCompatActivity).apply {
                            if (tab == Tab.ASSETS && viewMode is ViewMode.Assets && balanceState.showWelcome()) {
                                supportActionBar?.hide()
                            } else {
                                supportActionBar?.show()
                            }
                        }
                    }

                    when (tab) {
                        Tab.ASSETS -> MainComposable(
                            innerPadding = innerPadding,
                            state = balanceState,
                            txResult = txResult,
                            viewMode = viewMode,
                            devMode = devMode,
                            networkStatus = networkStatus,
                            onWithdrawMoneyClicked = {
                                // FIXME: remove exchange when whitelisted in wallet-core
                                model.exchangeManager.add("https://exchange.taler-ops.ch/") {
                                    val args = bundleOf("exchangeBaseUrl" to "https://exchange.taler-ops.ch/")
                                    findNavController().navigate(R.id.promptWithdraw, args)
                                }
                            },
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
                    onDismiss = { showSheet = false },
                    disableActions = disableActions,
                    disablePeer = selectedBalance?.disablePeerPayments == true,
                    onSend = this@MainFragment::onSend,
                    onReceive = this@MainFragment::onReceive,
                    onScanQr = this@MainFragment::onScanQr,
                    onDeposit = this@MainFragment::onDeposit,
                    onWithdraw = this@MainFragment::onWithdraw,
                    onEnterUri = this@MainFragment::onEnterUri,
                    onShoppingDiscovery = this@MainFragment::onShoppingDiscovery,
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

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as AppCompatActivity).apply {
            supportActionBar?.show()
        }
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
        model.settingsManager.saveActionButtonUsed(requireContext())
        findNavController().navigate(R.id.nav_peer_push)
    }

    private fun onReceive() {
        model.settingsManager.saveActionButtonUsed(requireContext())
        findNavController().navigate(R.id.nav_peer_pull)
    }

    private fun onDeposit() {
        model.settingsManager.saveActionButtonUsed(requireContext())
        findNavController().navigate(R.id.nav_deposit)
    }

    private fun onWithdraw() {
        model.settingsManager.saveActionButtonUsed(requireContext())
        model.withdrawManager.resetWithdrawal()
        findNavController().navigate(R.id.promptWithdraw)
    }

    private fun onScanQr() {
        model.settingsManager.saveActionButtonUsed(requireContext())
        model.scanCode()
    }

    private fun onEnterUri() {
        model.settingsManager.saveActionButtonUsed(requireContext())
        findNavController().navigate(R.id.nav_uri_input)
    }

    private fun onShoppingDiscovery() {
        model.settingsManager.saveActionButtonUsed(requireContext())
        findNavController().navigate(R.id.nav_shopping)
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