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

package net.taler.wallet.withdraw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.map
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.compose.AmountScope
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.exchanges.SelectExchangeComposable
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.main.ViewMode
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import net.taler.wallet.NavigateCallback

@Composable
fun PromptWithdrawScreen(
    model: MainViewModel,
    dest: WalletDestination.PromptWithdraw,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val withdrawManager = model.withdrawManager
    val balanceManager = model.balanceManager
    val transactionManager = model.transactionManager
    val exchangeManager = model.exchangeManager

    val status by withdrawManager.withdrawStatus.collectAsStateLifecycleAware()
    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)
    
    var showExchangeSelect by remember { mutableStateOf(false) }

    val selectedScope = remember(viewMode) {
        (viewMode as? ViewMode.Transactions)?.selectedScope
    }

    val scopes by balanceManager.balances
        .map { bl -> bl.map { it.scopeInfo } }
        .map { bl ->
            val scope = status.amountInfo?.scopeInfo
            if (scope != null && !bl.contains(scope)) {
                bl + scope
            } else bl
        }.observeAsState(emptyList())

    val initialAmount = status.selectedAmount
        ?: selectedScope?.let { Amount.zero(it.currency) }
        ?: scopes.firstOrNull()?.let { Amount.zero(it.currency) }

    val initialScope = status.selectedScope
        ?: selectedScope
        ?: scopes.firstOrNull()

    LaunchedEffect(status.status) {
        if (status.status == WithdrawStatus.Status.None) {
            if (dest.withdrawUri != null) {
                withdrawManager.prepareBankIntegratedWithdrawal(dest.withdrawUri, context, loading = true)
            } else if (dest.withdrawExchangeUri != null) {
                withdrawManager.prepareManualWithdrawal(dest.withdrawExchangeUri)
            } else if (dest.exchangeBaseUrl != null) {
                withdrawManager.getWithdrawalDetailsForExchange(dest.exchangeBaseUrl, loading = true)
            } else if (initialAmount != null) {
                withdrawManager.getWithdrawalDetailsForAmount(
                    amount = initialAmount,
                    scopeInfo = initialScope,
                )
            }
        }
    }

    LaunchedEffect(status.status) {
        when (status.status) {
            WithdrawStatus.Status.Success,
            WithdrawStatus.Status.ManualTransferRequired,
            WithdrawStatus.Status.AlreadyConfirmed -> {
                status.transactionId?.let { txId ->
                    if (transactionManager.selectTransaction(txId)) {
                        status.amountInfo?.scopeInfo?.let { s -> model.selectScope(s) }
                        onNavigate(WalletDestination.TransactionWithdrawal, true)
                    } else {
                        onNavigateBack()
                    }
                }
            }
            else -> {}
        }
    }
    
    // Detect ToS acceptance
    val exchanges by exchangeManager.exchanges.observeAsState()
    LaunchedEffect(exchanges) {
        exchanges?.let { withdrawManager.refreshTosStatus(it) }
    }

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                status.selectedSpec?.symbol?.let { symbol ->
                    stringResource(R.string.nav_prompt_withdraw_currency, symbol)
                } ?: dest.amount?.let { Amount.fromJSONString(it) }?.currency?.let { currency ->
                    stringResource(R.string.nav_prompt_withdraw_currency, currency)
                } ?: stringResource(R.string.nav_prompt_withdraw),
            )
        },
        onNavigateBack = onNavigateBack,
    ) { paddingValues ->
        if (status.selectedAmount == null && status.selectedScope == null) {
            LoadingScreen(Modifier.padding(paddingValues))
        } else {
            WithdrawalShowInfo(
                modifier = Modifier.padding(paddingValues),
                status = status,
                devMode = devMode,
                initialAmountScope = initialAmount?.let { amount ->
                    initialScope?.let { scope ->
                        AmountScope(amount, scope)
                    }
                },
                editableScope = dest.editableCurrency,
                scopes = scopes,
                onSelectExchange = {
                    showExchangeSelect = true
                },
                onSelectAmount = { amount, scope ->
                    withdrawManager.getWithdrawalDetailsForAmount(
                        amount = amount,
                        scopeInfo = scope,
                        loading = scope != status.selectedScope,
                    )
                },
                onTosReview = {
                    status.exchangeBaseUrl?.let {
                        onNavigate(WalletDestination.ReviewExchangeTOS(it), false)
                    }
                },
                onConfirm = { age ->
                    status.selectedScope?.let { model.selectScope(it) }
                    withdrawManager.acceptWithdrawal(age)
                },
                onReset = {
                    withdrawManager.resetWithdrawal()
                }
            )
        }
    }

    if (showExchangeSelect) {
        val possibleExchanges = status.uriInfo?.possibleExchanges ?: emptyList()
        SelectExchangeComposable(
            exchanges = possibleExchanges,
            onExchangeSelected = {
                showExchangeSelect = false
                withdrawManager.getWithdrawalDetailsForExchange(it.exchangeBaseUrl)
            }
        )
    }
}
