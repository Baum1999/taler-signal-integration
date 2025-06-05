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

package net.taler.wallet.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.EventObserver
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.SelectExchangeDialogFragment
import net.taler.wallet.withdraw.WithdrawStatus.Status.AlreadyConfirmed
import net.taler.wallet.withdraw.WithdrawStatus.Status.Error
import net.taler.wallet.withdraw.WithdrawStatus.Status.InfoReceived
import net.taler.wallet.withdraw.WithdrawStatus.Status.Loading
import net.taler.wallet.withdraw.WithdrawStatus.Status.ManualTransferRequired
import net.taler.wallet.withdraw.WithdrawStatus.Status.None
import net.taler.wallet.withdraw.WithdrawStatus.Status.Success
import net.taler.wallet.withdraw.WithdrawStatus.Status.TosReviewRequired
import net.taler.wallet.withdraw.WithdrawStatus.Status.Updating

class PromptWithdrawFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val withdrawManager by lazy { model.withdrawManager }
    private val transactionManager by lazy { model.transactionManager }
    private val exchangeManager by lazy { model.exchangeManager }
    private val balanceManager by lazy { model.balanceManager }

    private val selectExchangeDialog = SelectExchangeDialogFragment()

    private var editableCurrency: Boolean = true
    private var navigating: Boolean = false
    private var acceptingTos: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        val withdrawUri = arguments?.getString("withdrawUri")
        val withdrawExchangeUri = arguments?.getString("withdrawExchangeUri")
        val exchangeBaseUrl = arguments?.getString("exchangeBaseUrl")
        val amount = arguments?.getString("amount")?.let { Amount.fromJSONString(it) }
        val scope: ScopeInfo? = arguments?.getString("scopeInfo")?.let { BackendManager.json.decodeFromString(it) }
        editableCurrency = arguments?.getBoolean("editableCurrency") ?: true
        val scopes = balanceManager.getScopes()

        setContent {
            val status by withdrawManager.withdrawStatus.collectAsStateLifecycleAware()
            val devMode by model.devMode.observeAsState()

            val exchange by remember(status.exchangeBaseUrl) {
                status.exchangeBaseUrl
                    ?.let { exchangeManager.findExchangeForBaseUrl(it) }
                    ?: MutableStateFlow(null)
            }.collectAsStateLifecycleAware(null)

            val defaultScope = scope
                ?: status.scopeInfo
                ?: transactionManager.selectedScope.value
                ?: scopes.firstOrNull()

            LaunchedEffect(status.status) {
                if (status.status == None) {
                    if (withdrawUri != null) {
                        // get withdrawal details for taler://withdraw URI
                        withdrawManager.prepareBankIntegratedWithdrawal(withdrawUri, loading = true)
                    } else if (withdrawExchangeUri != null) {
                        // get withdrawal details for taler://withdraw-exchange URI
                        withdrawManager.prepareManualWithdrawal(withdrawExchangeUri)
                    } else if (defaultScope != null && !status.isCashAcceptor) {
                        // get withdrawal details for available data
                        withdrawManager.getWithdrawalDetails(
                            amount = amount ?: Amount.zero(defaultScope.currency),
                            scopeInfo = scope ?: defaultScope,
                            exchangeBaseUrl = exchangeBaseUrl,
                            loading = true,
                        )
                    }
                }
            }

            val currencySpec = remember(exchange?.scopeInfo) {
                exchange?.scopeInfo?.let { scopeInfo ->
                    exchangeManager.getSpecForScopeInfo(scopeInfo)
                } ?: status.currency?.let {
                    exchangeManager.getSpecForCurrency(it)
                }
            }

            LaunchedEffect(currencySpec, amount) {
                (requireActivity() as AppCompatActivity).apply {
                    supportActionBar?.title = currencySpec?.symbol?.let { symbol ->
                        getString(R.string.nav_prompt_withdraw_currency, symbol)
                    } ?: amount?.currency?.let { currency ->
                        getString(R.string.nav_prompt_withdraw_currency, currency)
                    } ?: getString(R.string.nav_prompt_withdraw)
                }
            }

            TalerSurface {
                status.let { s ->
                    if (defaultScope == null) {
                        LoadingScreen()
                        return@let
                    }

                    when (s.status) {
                        Loading, AlreadyConfirmed -> LoadingScreen()

                        None, Error, InfoReceived, TosReviewRequired, Updating -> {
                            // TODO: use scopeInfo instead of currency!
                            WithdrawalShowInfo(
                                status = s,
                                devMode = devMode ?: false,
                                defaultScope = defaultScope,
                                editableScope = editableCurrency,
                                scopes = scopes,
                                spec = currencySpec,
                                onSelectExchange = {
                                    selectExchange()
                                },
                                onSelectAmount = { amount, scope ->
                                    withdrawManager.getWithdrawalDetails(
                                        amount = amount,
                                        scopeInfo = scope,
                                        // only show loading screen when switching currencies
                                        loading = scope != status.scopeInfo,
                                    )
                                },
                                onTosReview = {
                                    // TODO: rewrite ToS review screen in compose
                                    val args = bundleOf("exchangeBaseUrl" to s.exchangeBaseUrl)
                                    findNavController().navigate(R.id.action_global_reviewExchangeTos, args)
                                },
                                onConfirm = { age ->
                                    exchange?.scopeInfo?.let { model.transactionManager.selectScope(it) }
                                    withdrawManager.acceptWithdrawal(age)
                                },
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                withdrawManager.withdrawStatus.collect { status ->
                    if (status.exchangeBaseUrl == null
                        && selectExchangeDialog.dialog?.isShowing != true) {
                        selectExchange()
                    }

                    when (status.status) {
                        Success, ManualTransferRequired, AlreadyConfirmed -> lifecycleScope.launch {
                            Snackbar.make(
                                requireView(),
                                if (status.status == AlreadyConfirmed) {
                                    R.string.withdraw_error_already_confirmed
                                } else {
                                    R.string.withdraw_initiated
                                },
                                LENGTH_LONG,
                            ).show()

                            status.transactionId?.let {
                                if (!navigating) {
                                    navigating = true
                                } else return@let

                                if (transactionManager.selectTransaction(it)) {
                                    status.amountInfo?.scopeInfo?.let { s -> transactionManager.selectScope(s) }
                                    findNavController().navigate(R.id.action_promptWithdraw_to_nav_transactions_detail_withdrawal)
                                } else {
                                    findNavController().navigate(R.id.action_promptWithdraw_to_nav_main)
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                withdrawManager.withdrawStatus.collect { status ->
                    when (status.status) {
                        TosReviewRequired -> {
                            if (!acceptingTos && transactionManager.selectedScope.value != null) {
                                acceptingTos = true
                                val args = bundleOf("exchangeBaseUrl" to status.exchangeBaseUrl)
                                findNavController().navigate(R.id.action_global_reviewExchangeTos, args)
                            } else return@collect
                        }

                        else -> {}
                    }
                }
            }
        }

        selectExchangeDialog.exchangeSelection.observe(viewLifecycleOwner, EventObserver {
            onExchangeSelected(it)
        })

        exchangeManager.exchanges.observe(viewLifecycleOwner) { exchanges ->
            // detect ToS acceptation
            withdrawManager.refreshTosStatus(exchanges)
        }
    }

    private fun selectExchange() {
        val exchanges = withdrawManager.withdrawStatus.value.uriInfo?.possibleExchanges ?: return
        selectExchangeDialog.setExchanges(exchanges)
        selectExchangeDialog.show(parentFragmentManager, "SELECT_EXCHANGE")
    }

    private fun onExchangeSelected(exchange: ExchangeItem) {
        withdrawManager.getWithdrawalDetails(
            exchangeBaseUrl = exchange.exchangeBaseUrl,
        )
    }
}

@Composable
fun WithdrawalError(
    error: TalerErrorInfo,
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        contentAlignment = Center,
    ) {
        Text(
            text = error.userFacingMsg,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}