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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.link.ReturnIntentSender
import net.taler.wallet.link.ReturnStatus
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
    val exchangeManager = model.exchangeManager
    val context = LocalContext.current

    // Gleiches Muster wie IncomingPushPaymentScreen (siehe dortiger
    // ausfuehrlicher Kommentar): das gemeinsame
    // peerManager.pendingReturnCallback-Feld wird einmalig bei der ersten
    // Komposition dieser Instanz lokal eingefangen und NICHT sofort geleert -
    // die TOS-Review-Zwischenseite (DisposableEffect unten) disposed diese
    // Instanz, eine frische Instanz liest das Feld nach der Rueckkehr erneut.
    val myCallback = remember { peerManager.pendingReturnCallback }
    fun consumeCallbackSlot() {
        if (peerManager.pendingReturnCallback === myCallback) {
            peerManager.pendingReturnCallback = null
        }
    }

    val state = peerManager.incomingPullState.collectAsStateLifecycleAware()
    val exchanges by exchangeManager.exchanges.observeAsState()

    LaunchedEffect(exchanges) {
        exchanges?.let {
            peerManager.refreshPeerPullDebitTos(it)
        }
    }

    var leavingForSubScreen by remember { mutableStateOf(false) }

    LaunchedEffect(state.value) {
        val s = state.value
        if (s is IncomingAccepted) {
            // docs/API.md 2.10: Ruecksprung feuern, BEVOR der Zustand geloescht
            // wird - der Cancel-Fallback unten (DisposableEffect.onDispose)
            // prueft auf einen noch gesetzten pendingReturnCallback und wuerde
            // sonst faelschlich CANCELLED nachschicken.
            myCallback?.let { callback ->
                ReturnIntentSender.fire(context, callback.returnUri, callback.correlationId, ReturnStatus.READY)
                consumeCallbackSlot()
            }
            if (transactionManager.selectTransaction(s.transactionId)) {
                onNavigate(WalletDestination.TransactionPeer, true)
            } else {
                onNavigateBack()
            }
        } else if (s is IncomingError) {
            onShowError(s.info)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Nicht feuern, wenn wir gerade nur zur TOS-Review-Zwischenseite
            // navigieren (WalletDestination.ReviewExchangeTOS) - das ist kein
            // Verlassen des Annehmen-Vorgangs, nur ein Zwischenschritt, nach
            // dem eine frische Instanz dieses Composables denselben Vorgang
            // fortsetzt. Ohne diese Absicherung wuerde jeder Exchange, der
            // eine aktualisierte TOS-Bestaetigung braucht, faelschlich ein
            // CANCELLED ausloesen und den spaeteren echten READY unterdruecken.
            if (!leavingForSubScreen) {
                myCallback?.let { callback ->
                    ReturnIntentSender.fire(context, callback.returnUri, callback.correlationId, ReturnStatus.CANCELLED)
                    consumeCallbackSlot()
                }
            }
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.pay_peer_title)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            IncomingComposable(
                modifier = Modifier.padding(paddingValues),
                state = state,
                data = incomingPull
            ) { terms ->
                if (terms is IncomingTosReview) {
                    leavingForSubScreen = true
                    onNavigate(WalletDestination.ReviewExchangeTOS(terms.exchangeBaseUrl), false)
                } else {
                    peerManager.confirmPeerPullDebit(terms)
                }
            }
        }
    }
}
