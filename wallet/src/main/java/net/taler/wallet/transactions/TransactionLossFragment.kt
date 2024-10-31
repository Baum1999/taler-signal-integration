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

package net.taler.wallet.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.Timestamp
import net.taler.common.toAbsoluteTime
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.transactions.LossEventType.DenomExpired
import net.taler.wallet.transactions.LossEventType.DenomUnoffered
import net.taler.wallet.transactions.LossEventType.DenomVanished
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.TransactionMajorState.Pending

class TransactionLossFragment: TransactionDetailFragment() {
    val scope get() = transactionManager.selectedScope.value

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            val t by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
            val spec = scope?.let { balanceManager.getSpecForScopeInfo(it) }

            TalerSurface {
                (t as? TransactionDenomLoss)?.let { tx ->
                    TransitionLossComposable(tx, devMode, spec) {
                        onTransitionButtonClicked(tx, it)
                    }
                }
            }
        }
    }
}

@Composable
fun TransitionLossComposable(
    t: TransactionDenomLoss,
    devMode: Boolean,
    spec: CurrencySpecification?,
    onTransition: (t: TransactionAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransactionStateComposable(state = t.txState)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(context).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_lost),
            amount = t.amountEffective.withSpec(spec),
            amountType = AmountType.Negative,
        )

        TransactionInfoComposable(
            label = stringResource(id = R.string.loss_reason),
            info = stringResource(
                when(t.lossEventType) {
                    DenomExpired -> R.string.loss_reason_expired
                    DenomVanished -> R.string.loss_reason_vanished
                    DenomUnoffered -> R.string.loss_reason_unoffered
                }
            )
        )

        TransitionsComposable(t, devMode, onTransition)

        if (devMode && t.error != null) {
            ErrorTransactionButton(error = t.error)
        }

        BottomInsetsSpacer()
    }
}

fun previewLossTransaction(lossEventType: LossEventType) =
    TransactionDenomLoss(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Pending),
        txActions = listOf(Retry, Suspend, Abort),
        amountRaw = Amount.fromString("TESTKUDOS", "0.3"),
        amountEffective = Amount.fromString("TESTKUDOS", "0.3"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
        lossEventType = lossEventType,
    )

@Composable
@Preview
fun TransitionLossComposableExpiredPreview() {
    val t = previewLossTransaction(DenomExpired)
    Surface {
        TransitionLossComposable(t, true, null) {}
    }
}

@Composable
@Preview
fun TransitionLossComposableVanishedPreview() {
    val t = previewLossTransaction(DenomVanished)
    Surface {
        TransitionLossComposable(t, true, null) {}
    }
}

@Composable
@Preview
fun TransactionLossComposableUnofferedPreview() {
    val t = previewLossTransaction(DenomUnoffered)
    Surface {
        TransitionLossComposable(t, true, null) {}
    }
}
