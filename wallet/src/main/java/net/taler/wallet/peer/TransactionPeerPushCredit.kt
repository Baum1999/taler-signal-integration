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

package net.taler.wallet.peer

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.Timestamp
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode.EXCHANGE_GENERIC_KYC_REQUIRED
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.transactions.ActionButton
import net.taler.wallet.transactions.ActionListener
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.PeerInfoShort
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionPeerComposable
import net.taler.wallet.transactions.TransactionPeerPushCredit
import net.taler.wallet.transactions.TransactionState

@Composable
fun TransactionPeerPushCreditComposable(
    t: TransactionPeerPushCredit,
    spec: CurrencySpecification?,
    actionListener: ActionListener,
) {
    ActionButton(tx = t, listener = actionListener)

    TransactionAmountComposable(
        label = stringResource(id = R.string.amount_sent),
        amount = t.amountRaw.withSpec(spec),
        amountType = AmountType.Neutral,
    )

    if (t.amountRaw > t.amountEffective) {
        val fee = t.amountRaw - t.amountEffective
        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_fee),
            amount = fee.withSpec(spec),
            amountType = AmountType.Negative,
        )
    }

    TransactionAmountComposable(
        label = stringResource(id = R.string.amount_received),
        amount = t.amountEffective.withSpec(spec),
        amountType = AmountType.Positive,
    )

    TransactionInfoComposable(
        label = stringResource(id = R.string.send_peer_purpose),
        info = t.info.summary ?: "",
    )
}

@Preview
@Composable
fun TransactionPeerPushCreditPreview() {
    val t = TransactionPeerPushCredit(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Pending),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.example.org/",
        amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
        info = PeerInfoShort(
            expiration = Timestamp.fromMillis(System.currentTimeMillis() + 60 * 60 * 1000),
            summary = "test invoice",
        ),
        error = TalerErrorInfo(code = EXCHANGE_GENERIC_KYC_REQUIRED),
        scopes = listOf(ScopeInfo.Exchange(
            currency = "TESTKUDOS",
            url = "exchange.test.taler.net",
        ))
    )
    Surface {
        TransactionPeerComposable(t, true, null, object : ActionListener {
            override fun onActionButtonClicked(tx: Transaction, type: ActionListener.Type) {}
        }) {}
    }
}
