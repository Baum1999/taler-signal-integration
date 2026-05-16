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

package net.taler.wallet.peer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.main.ViewMode

@Composable
fun OutgoingPullScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val peerManager = model.peerManager
    val transactionManager = model.transactionManager
    val balanceManager = model.balanceManager
    val exchangeManager = model.exchangeManager

    val state by peerManager.pullState.collectAsStateLifecycleAware()
    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)
    val exchanges by exchangeManager.exchanges.observeAsState()

    DisposableEffect(Unit) {
        onDispose {
            peerManager.resetPullPayment()
        }
    }

    LaunchedEffect(exchanges) {
        exchanges?.let {
            peerManager.refreshPeerPullCreditTos(it)
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is OutgoingResponse) {
            if (transactionManager.selectTransaction(s.transactionId)) {
                onNavigate(WalletDestination.TransactionPeer, true)
            } else {
                onNavigateBack()
            }
        }

        if (s is OutgoingError) {
            onShowError(s.info)
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.receive_peer_title)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            OutgoingPullComposable(
                modifier = Modifier.padding(paddingValues),
                state = state,
                onCreateInvoice = { amount, summary, hours, exchangeBaseUrl ->
                    peerManager.initiatePeerPullCredit(amount.amount, summary, hours, exchangeBaseUrl)
                },
                onTosAccept = { exchangeBaseUrl ->
                    onNavigate(WalletDestination.ReviewExchangeTOS(exchangeBaseUrl), false)
                },
                defaultScope = remember { (viewMode as? ViewMode.Transactions)?.selectedScope },
                scopes = balanceManager.getScopes(true),
                devMode = devMode,
                getCurrencySpec = exchangeManager::getSpecForScopeInfo,
                checkPeerPullCredit = { amount, loading ->
                    model.selectScope(amount.scope)
                    peerManager.checkPeerPullCredit(
                        amount.amount,
                        scopeInfo = amount.scope,
                        loading = loading,
                    )
                },
            )
        }
    }
}
