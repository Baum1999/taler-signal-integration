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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
fun IncomingPullPaymentScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val peerManager = model.peerManager
    val transactionManager = model.transactionManager

    val state by peerManager.incomingPullState.collectAsStateLifecycleAware()

    LaunchedEffect(state) {
        val s = state
        if (s is IncomingAccepted) {
            if (transactionManager.selectTransaction(s.transactionId)) {
                onNavigate(WalletDestination.TransactionPeer, true)
            } else {
                onNavigateBack()
            }
        } else if (s is IncomingError) {
            onShowError(s.info)
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            title = { Text(stringResource(R.string.pay_peer_title)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            IncomingComposable(
                modifier = Modifier.padding(paddingValues),
                state = peerManager.incomingPullState.collectAsStateLifecycleAware(),
                data = incomingPull
            ) { terms ->
                peerManager.confirmPeerPullDebit(terms)
            }
        }
    }
}
