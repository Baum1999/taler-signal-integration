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
import net.taler.wallet.transactions.TransactionPeerPullDebit
import net.taler.wallet.transactions.TransactionState

@Composable
fun TransactionPeerPullDebitComposable(t: TransactionPeerPullDebit, spec: CurrencySpecification?) {
    TransactionAmountComposable(
        label = stringResource(id = R.string.transaction_order_total),
        amount = t.amountRaw.withSpec(spec),
        amountType = AmountType.Neutral,
    )

    if (t.amountEffective > t.amountRaw) {
        val fee = t.amountEffective - t.amountRaw
        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_fee),
            amount = fee.withSpec(spec),
            amountType = AmountType.Negative,
        )
    }

    TransactionAmountComposable(
        label = stringResource(id = R.string.transaction_paid),
        amount = t.amountEffective.withSpec(spec),
        amountType = AmountType.Negative,
    )

    TransactionInfoComposable(
        label = stringResource(id = R.string.send_peer_purpose),
        info = t.info.summary ?: "",
    )
}

@Preview
@Composable
fun TransactionPeerPullDebitPreview() {
    val t = TransactionPeerPullDebit(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Pending),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.example.org/",
        amountRaw = Amount.fromString("TESTKUDOS", "42.1337"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.23"),
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
        TransactionPeerComposable(t, true, null, object: ActionListener {
            override fun onActionButtonClicked(tx: Transaction, type: ActionListener.Type) {}
        }) {}
    }
}
