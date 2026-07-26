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

package net.taler.wallet.deposit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.map
import net.taler.common.Amount
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.accounts.ListBankAccountsResult
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel

@Composable
fun DepositScreen(
    model: MainViewModel,
    dest: WalletDestination.Deposit,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val depositManager = model.depositManager
    val accountManager = model.accountManager
    val exchangeManager = model.exchangeManager

    val presetAmount = dest.amount?.let { Amount.fromJSONString(it) }
    val receiverName = dest.receiverName
    val receiverPostalCode = dest.receiverPostalCode
    val receiverTown = dest.receiverTown
    val iban = dest.IBAN

    LaunchedEffect(Unit) {
        if (presetAmount != null && receiverName != null && iban != null) {
            val paytoUri = getIbanPayto(receiverName, receiverPostalCode, receiverTown, iban)
            depositManager.makeDeposit(presetAmount, paytoUri)
        }
        accountManager.listBankAccounts()
    }

    val state by depositManager.depositState.collectAsStateLifecycleAware()

    LaunchedEffect(state) {
        (state as? DepositState.Success)?.let {
            if (model.transactionManager.selectTransaction(it.transactionId)) {
                onNavigate(WalletDestination.TransactionDeposit, true)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            model.depositManager.resetDepositState()
        }
    }

    GlobalScaffold(
        model = model,
        onNavigateBack = onNavigateBack,
        title = { Text(stringResource(R.string.send_deposit_title)) },
    ) { paddingValues ->
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
                LoadingScreen(Modifier.padding(paddingValues))
            }

            is DepositState.Error -> ErrorComposable(
                error = s.error,
                devMode = devMode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                scrollable = true,
                onClose = onNavigateBack,
            )

            is DepositState.Start -> {
                MakeDepositComposable(
                    modifier = Modifier.padding(paddingValues),
                    knownBankAccounts = (knownBankAccounts as? ListBankAccountsResult.Success)
                        ?.accounts
                        ?: emptyList(),
                    onAccountSelected = { account ->
                        depositManager.selectAccount(account)
                    },
                    onManageBankAccounts = {
                        onNavigate(WalletDestination.BankAccounts(), false)
                    }
                )
            }

            is DepositState.AccountSelected -> {
                DepositAmountComposable(
                    modifier = Modifier.padding(paddingValues),
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
