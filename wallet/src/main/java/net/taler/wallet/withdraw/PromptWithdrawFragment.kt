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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.EventObserver
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.main.ViewMode
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.AmountScope
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.SelectExchangeDialogFragment
import net.taler.wallet.withdraw.WithdrawStatus.Status.AlreadyConfirmed
import net.taler.wallet.withdraw.WithdrawStatus.Status.Confirming
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        val withdrawUri = arguments?.getString("withdrawUri")
        val withdrawExchangeUri = arguments?.getString("withdrawExchangeUri")
        val exchangeBaseUrl = arguments?.getString("exchangeBaseUrl")
        val amount = arguments?.getString("amount")?.let { Amount.fromJSONString(it) }
        val scope: ScopeInfo? = arguments?.getString("scopeInfo")?.let {
            BackendManager.json.decodeFromString(it)
        } ?: (model.viewMode.value as? ViewMode.Transactions)?.selectedScope
        editableCurrency = arguments?.getBoolean("editableCurrency") ?: true
        val scopes = balanceManager.getScopes()

        setContent {
            val status by withdrawManager.withdrawStatus.collectAsStateLifecycleAware()
            val devMode by model.devMode.observeAsState()

            if (scopes.isEmpty()) EmptyComposable()
            val initialScope = status.selectedScope ?: scope ?: scopes.first()
            val initialAmount = status.selectedAmount ?: Amount.zero(initialScope.currency)

            LaunchedEffect(status.status) {
                if (status.status == None) {
                    if (withdrawUri != null) {
                        // get withdrawal details for taler://withdraw URI
                        withdrawManager.prepareBankIntegratedWithdrawal(withdrawUri, loading = true)
                    } else if (withdrawExchangeUri != null) {
                        // get withdrawal details for taler://withdraw-exchange URI
                        withdrawManager.prepareManualWithdrawal(withdrawExchangeUri)
                    } else if (exchangeBaseUrl != null) {
                        withdrawManager.getWithdrawalDetailsForExchange(exchangeBaseUrl, loading = true)
                    } else {
                        withdrawManager.getWithdrawalDetailsForAmount(
                            amount = initialAmount,
                            scopeInfo = initialScope,
                        )
                    }
                }
            }

            LaunchedEffect(status.selectedSpec, amount) {
                (requireActivity() as AppCompatActivity).apply {
                    supportActionBar?.title = status.selectedSpec?.symbol?.let { symbol ->
                        getString(R.string.nav_prompt_withdraw_currency, symbol)
                    } ?: amount?.currency?.let { currency ->
                        getString(R.string.nav_prompt_withdraw_currency, currency)
                    } ?: getString(R.string.nav_prompt_withdraw)
                }
            }

            TalerSurface {
                status.let { s ->
                    when (s.status) {
                        Confirming, AlreadyConfirmed -> LoadingScreen()

                        None, Loading, Error, InfoReceived, TosReviewRequired, Updating -> {
                            WithdrawalShowInfo(
                                status = s,
                                devMode = devMode ?: false,
                                initialAmountScope = AmountScope(
                                    amount = initialAmount,
                                    scope = initialScope,
                                ),
                                editableScope = editableCurrency,
                                scopes = scopes,
                                onSelectExchange = { selectExchange() },
                                onSelectAmount = { amount, scope ->
                                    withdrawManager.getWithdrawalDetailsForAmount(
                                        amount = amount,
                                        scopeInfo = scope,
                                        // only show loading screen when switching currencies
                                        loading = scope != status.selectedScope,
                                    )
                                },
                                onTosReview = {
                                    // TODO: rewrite ToS review screen in compose
                                    if (s.exchangeBaseUrl != null) {
                                        val args = bundleOf("exchangeBaseUrl" to s.exchangeBaseUrl)
                                        findNavController().navigate(R.id.action_global_reviewExchangeTos, args)
                                    }
                                },
                                onConfirm = { age ->
                                    status.selectedScope?.let { model.selectScope(it) }
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
                                    status.amountInfo?.scopeInfo?.let { s -> model.selectScope(s) }
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
        if (selectExchangeDialog.isAdded) {
            selectExchangeDialog.show(parentFragmentManager, "SELECT_EXCHANGE")
        }
    }

    private fun onExchangeSelected(exchange: ExchangeItem) {
        withdrawManager.getWithdrawalDetailsForExchange(
            exchangeBaseUrl = exchange.exchangeBaseUrl,
        )
    }
}