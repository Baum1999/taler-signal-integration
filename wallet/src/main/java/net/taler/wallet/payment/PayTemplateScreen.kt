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

package net.taler.wallet.payment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.asFlow
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel

@Composable
fun PayTemplateScreen(
    model: MainViewModel,
    uri: String,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val paymentManager = model.paymentManager
    val balanceManager = model.balanceManager
    val exchangeManager = model.exchangeManager
    val transactionManager = model.transactionManager

    val payStatus by paymentManager.payStatus.asFlow().collectAsStateLifecycleAware(PayStatus.None)
    val balanceState by balanceManager.state.observeAsState(BalanceState.None)
    val devMode by model.devMode.observeAsState(false)

    LaunchedEffect(Unit) {
        balanceManager.loadAssets()
        paymentManager.checkPayForTemplate(uri)
    }

    LaunchedEffect(payStatus) {
        when (val s = payStatus) {
            is PayStatus.Prepared -> {
                if (transactionManager.selectTransaction(s.transactionId)) {
                    onNavigate(WalletDestination.TransactionPayment, true)
                }
            }

            is PayStatus.Pending -> if (s.error != null) {
                onShowError(s.error)
            }

            is PayStatus.Checked -> {
                val usableCurrencies = balanceManager.getCurrencies()
                    .intersect(s.supportedCurrencies.toSet())
                    .toList()
                if (!s.details.isTemplateEditable(usableCurrencies)) {
                    paymentManager.preparePayForTemplate(uri, s.details.toTemplateParams())
                }
            }

            else -> {}
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.payment_pay_template_title)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            Box (Modifier.padding(paddingValues)) {
                when (val state = balanceState) {
                    is BalanceState.None, is BalanceState.Loading -> LoadingScreen()
                    is BalanceState.Error -> ErrorComposable(state.error, devMode = devMode)
                    is BalanceState.Success -> PayTemplateComposable(
                        currencies = state.balances.map { it.currency },
                        payStatus = payStatus,
                        onCreateAmount = model::createAmount,
                        onSubmit = { params ->
                            paymentManager.preparePayForTemplate(uri, params)
                        },
                        onError = { errorMsg ->
                            onShowError(TalerErrorInfo.makeCustomError(errorMsg))
                        },
                        getCurrencySpec = exchangeManager::getSpecForCurrency,
                    )
                }
            }
        }
    }
}
