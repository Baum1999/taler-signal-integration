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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import net.taler.common.Amount
import net.taler.common.showError
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.showError

class DepositFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val depositManager get() = model.depositManager
    private val balanceManager get() = model.balanceManager
    private val transactionManager get() = model.transactionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val presetAmount = arguments?.getString("amount")?.let { Amount.fromJSONString(it) }
        val scopeInfo = transactionManager.selectedScope.value
        val receiverName = arguments?.getString("receiverName")
        val iban = arguments?.getString("IBAN")

        if (presetAmount != null && receiverName != null && iban != null) {
            val paytoUri = getIbanPayto(receiverName, iban)
            depositManager.makeDeposit(presetAmount, paytoUri)
        }

        return ComposeView(requireContext()).apply {
            setContent {
                TalerSurface {
                    val state by depositManager.depositState.collectAsStateLifecycleAware()

                    BackHandler(state is DepositState.AccountSelected) {
                        depositManager.resetDepositState()
                    }

                    when (val s = state) {
                        is DepositState.MakingDeposit, is DepositState.Success -> {
                            LoadingScreen()
                        }

                        is DepositState.Error -> {
                            MakeDepositErrorComposable(s.error.userFacingMsg) {
                                findNavController().popBackStack()
                            }
                        }

                        is DepositState.Start -> {
                            // TODO: refactor Bitcoin as wire method
//                            if (presetAmount?.currency == CURRENCY_BTC) MakeBitcoinDepositComposable(
//                                state = state,
//                                amount = presetAmount.withSpec(spec),
//                                bitcoinAddress = null,
//                                onMakeDeposit = { amount, bitcoinAddress ->
//                                    val paytoUri = getBitcoinPayto(bitcoinAddress)
//                                    depositManager.makeDeposit(amount, paytoUri)
//                                },
                            MakeDepositComposable(
                                defaultCurrency = scopeInfo?.currency,
                                currencies = balanceManager.getCurrencies(),
                                getDepositWireTypes = depositManager::getDepositWireTypesForCurrency,
                                presetName = receiverName,
                                presetIban = iban,
                                validateIban = depositManager::validateIban,
                                onPaytoSelected = { paytoUri, currency ->
                                    depositManager.selectAccount(paytoUri, currency)
                                },
                                onClose = {
                                    findNavController().popBackStack()
                                },
                            )
                        }

                        is DepositState.AccountSelected -> {
                            DepositAmountComposable(
                                state = s,
                                currency = s.currency,
                                currencySpec = remember(s.currency) {
                                    balanceManager.getSpecForCurrency(s.currency)
                                },
                                checkDeposit = { a ->
                                    depositManager.checkDepositFees(s.paytoUri, a)
                                },
                                onMakeDeposit = { amount ->
                                    depositManager.makeDeposit(amount, s.paytoUri)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenStarted {
            depositManager.depositState.collect { state ->
                if (state is DepositState.Error) {
                    if (model.devMode.value == false) {
                        showError(state.error.userFacingMsg)
                    } else {
                        showError(state.error)
                    }
                } else if (state is DepositState.Success) {
                    findNavController().navigate(R.id.action_nav_deposit_to_nav_main)
                }
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
