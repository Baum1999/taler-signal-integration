/*
 * This file is part of GNU Taler
 * (C) 2023 Taler Systems S.A.
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

package net.taler.wallet.payment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asFlow
import androidx.navigation.fragment.findNavController
import net.taler.common.showError
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.showError
import androidx.core.net.toUri
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen

class PayTemplateFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private lateinit var uriString: String
    private lateinit var uri: Uri

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        uriString = arguments?.getString("uri") ?: error("no amount passed")
        uri = uriString.toUri()

        val payStatusFlow = model.paymentManager.payStatus.asFlow()

        return ComposeView(requireContext()).apply {
            setContent {
                val payStatus = payStatusFlow.collectAsStateLifecycleAware(PayStatus.None)
                val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
                val devMode by model.devMode.observeAsState(false)
                TalerSurface {
                    when (val state = balanceState) {
                        is BalanceState.None, is BalanceState.Loading -> LoadingScreen()
                        is BalanceState.Error -> ErrorComposable(state.error, devMode = devMode)
                        is BalanceState.Success -> PayTemplateComposable(
                            currencies = state.balances.map { it.currency },
                            payStatus = payStatus.value,
                            onCreateAmount = model::createAmount,
                            onSubmit = this@PayTemplateFragment::createOrder,
                            onError = { this@PayTemplateFragment.showError(it) },
                            getCurrencySpec = model.exchangeManager::getSpecForCurrency,
                        )
                    }

                    LaunchedEffect(balanceState) {
                        balanceState
                    }

                    LaunchedEffect(Unit) {
                        model.balanceManager.loadAssets()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkTemplate()

        model.paymentManager.payStatus.observe(viewLifecycleOwner) { payStatus ->
            when (payStatus) {
                is PayStatus.Prepared -> {
                    model.paymentManager.preparePay(payStatus.transactionId) {
                        findNavController().navigate(R.id.action_global_promptPayment)
                    }
                }

                is PayStatus.Pending -> if (payStatus.error != null && model.devMode.value == true) {
                    showError(payStatus.error)
                }

                is PayStatus.Checked -> {
                    val usableCurrencies = model.balanceManager.getCurrencies()
                        .intersect(payStatus.supportedCurrencies.toSet())
                        .toList()
                    if (!payStatus.details.isTemplateEditable(usableCurrencies)) {
                        createOrder(payStatus.details.toTemplateParams())
                    }
                }

                else -> {}
            }
        }
    }

    private fun checkTemplate() {
        model.paymentManager.checkPayForTemplate(uriString)
    }

    private fun createOrder(params: TemplateParams) {
        model.paymentManager.preparePayForTemplate(uriString, params)
    }
}
