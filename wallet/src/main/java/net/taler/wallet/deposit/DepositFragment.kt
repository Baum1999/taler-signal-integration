/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.deposit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.showError
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.showError
import net.taler.wallet.accounts.ListBankAccountsResult.Success
import net.taler.wallet.compose.ErrorComposable

class DepositFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val depositManager get() = model.depositManager
    private val accountManager get() = model.accountManager
    private val exchangeManager get() = model.exchangeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val presetAmount = arguments?.getString("amount")?.let { Amount.fromJSONString(it) }
        val receiverName = arguments?.getString("receiverName")
        val receiverPostalCode = arguments?.getString("receiverPostalCode")
        val receiverTown = arguments?.getString("receiverTown")
        val iban = arguments?.getString("IBAN")

        if (presetAmount != null && receiverName != null && iban != null) {
            val paytoUri = getIbanPayto(receiverName, receiverPostalCode, receiverTown, iban)
            depositManager.makeDeposit(presetAmount, paytoUri)
        }

        return ComposeView(requireContext()).apply {
            setContent {
                TalerSurface {
                    val state by depositManager.depositState.collectAsStateLifecycleAware()
                    val knownBankAccounts by accountManager.bankAccounts.collectAsStateLifecycleAware()
                    val knownCurrencies by model.balanceManager.balances.map {
                        it.map { bl -> bl.currency }
                    }.observeAsState(emptyList())
                    val devMode by model.devMode.observeAsState(false)

                    BackHandler(state is DepositState.AccountSelected) {
                        depositManager.resetDepositState()
                    }

                    when (val s = state) {
                        is DepositState.MakingDeposit, is DepositState.Success -> {
                            LoadingScreen()
                        }

                        is DepositState.Error -> ErrorComposable(s.error,
                            devMode = devMode,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            onClose = {
                                findNavController().popBackStack()
                            },
                        )

                        is DepositState.Start -> {
                            MakeDepositComposable(
                                knownBankAccounts = (knownBankAccounts as? Success)
                                    ?.accounts
                                    ?: emptyList(),
                                onAccountSelected = { account ->
                                    depositManager.selectAccount(account)
                                },
                                onManageBankAccounts = {
                                    findNavController().navigate(R.id.action_global_bankAccounts)
                                }
                            )
                        }

                        is DepositState.AccountSelected -> {
                            DepositAmountComposable(
                                state = s,
                                knownCurrencies = knownCurrencies,
                                getCurrencySpec = exchangeManager::getSpecForCurrency,
                                checkDeposit = { a ->
                                    depositManager.checkDepositFees(s.account.paytoUri, a)
                                },
                                onMakeDeposit = { amount ->
                                    depositManager.makeDeposit(amount, s.account.paytoUri)
                                },
                                onClose = {
                                    depositManager.resetDepositState()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val supportActionBar = (requireActivity() as? AppCompatActivity)?.supportActionBar
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                depositManager.depositState.collect { state ->
                    when (state) {
                        is DepositState.Start -> {
                            supportActionBar?.setTitle(R.string.send_deposit_select_account_title)
                        }

                        is DepositState.AccountSelected -> {
                            supportActionBar?.setTitle(R.string.send_deposit_select_amount_title)
                        }

                        is DepositState.Error -> {
                            if (model.devMode.value == false) {
                                showError(state.error.userFacingMsg)
                            } else {
                                showError(state.error)
                            }
                        }

                        is DepositState.Success -> {
                            findNavController().navigate(R.id.action_global_main)
                        }

                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountManager.listBankAccounts()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.send_deposit_title)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!requireActivity().isChangingConfigurations) {
            depositManager.resetDepositState()
        }
    }
}
