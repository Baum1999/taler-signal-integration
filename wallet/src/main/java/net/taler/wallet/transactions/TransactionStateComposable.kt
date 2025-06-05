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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.RelativeTime
import net.taler.common.Timestamp
import net.taler.common.toAbsoluteTime
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.Banner
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.TransactionMajorState.Aborted
import net.taler.wallet.transactions.TransactionMajorState.Aborting
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionMajorState.Expired
import net.taler.wallet.transactions.TransactionMajorState.Failed
import net.taler.wallet.transactions.TransactionMajorState.Finalizing
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionMajorState.Suspended
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycInit
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycRequired
import net.taler.wallet.transactions.TransactionMinorState.BankConfirmTransfer
import net.taler.wallet.transactions.TransactionMinorState.KycAuthRequired
import net.taler.wallet.transactions.TransactionMinorState.KycInit
import net.taler.wallet.transactions.TransactionMinorState.KycRequired
import net.taler.wallet.transactions.TransactionMinorState.MergeKycRequired
import net.taler.wallet.transactions.TransactionMinorState.Repurchase
import net.taler.wallet.transactions.WithdrawalDetails.ManualTransfer

@Composable
fun TransactionStateComposable(
    modifier: Modifier = Modifier,
    state: TransactionState,
    tx: Transaction? = null,
) {
    val context = LocalContext.current
    val message = when (state) {
        TransactionState(Done) -> stringResource(R.string.transaction_state_done)
        TransactionState(Pending, BankConfirmTransfer) -> stringResource(R.string.transaction_state_pending_bank)
        TransactionState(Pending, KycInit) -> stringResource(R.string.transaction_preparing_kyc)
        TransactionState(Pending, BalanceKycInit) -> stringResource(R.string.transaction_preparing_kyc)
        TransactionState(Pending, KycRequired) -> stringResource(R.string.transaction_state_pending_kyc_bank)
        TransactionState(Pending, BalanceKycRequired) -> stringResource(R.string.transaction_state_pending_kyc_bank)
        TransactionState(Pending, MergeKycRequired) -> stringResource(R.string.transaction_state_pending_kyc_bank)
        TransactionState(Pending, KycAuthRequired) -> stringResource(R.string.transaction_state_pending_kyc_auth)
        TransactionState(Pending) -> stringResource(R.string.transaction_state_pending)
        TransactionState(Aborted) -> if (tx is TransactionWithdrawal && tx.withdrawalDetails is ManualTransfer) {
            stringResource(
                R.string.transaction_state_aborted_manual,
                (tx.timestamp + tx.withdrawalDetails.reserveClosingDelay).ms.toAbsoluteTime(context).toString(),
            )
        } else stringResource(R.string.transaction_state_aborted)
        TransactionState(Aborting) -> stringResource(R.string.transaction_state_aborting)
        TransactionState(Suspended) -> stringResource(R.string.transaction_state_suspended)
        TransactionState(Failed, Repurchase) -> stringResource(R.string.payment_already_paid)
        TransactionState(Failed) -> stringResource(R.string.transaction_state_failed)
        TransactionState(Finalizing) -> stringResource(R.string.transaction_state_finalizing)
        TransactionState(Expired) -> stringResource(R.string.transaction_state_expired)
        else -> return
    }

    val cardColor = when (state) {
        TransactionState(Done) -> colorResource(R.color.green)
        TransactionState(Pending),
        TransactionState(Suspended),
        TransactionState(Failed, Repurchase),
        TransactionState(Finalizing) -> MaterialTheme.colorScheme.surfaceVariant
        TransactionState(Aborted),
        TransactionState(Aborting),
        TransactionState(Failed),
        TransactionState(Expired) -> MaterialTheme.colorScheme.errorContainer
        else -> return
    }

    val textColor = when (state) {
        TransactionState(Done) -> Color.White
        TransactionState(Pending),
        TransactionState(Suspended),
        TransactionState(Failed, Repurchase),
        TransactionState(Finalizing) -> MaterialTheme.colorScheme.onSurfaceVariant
        TransactionState(Aborted),
        TransactionState(Aborting),
        TransactionState(Failed),
        TransactionState(Expired) -> MaterialTheme.colorScheme.onErrorContainer
        else -> return
    }

    Banner(
        modifier = Modifier.padding(horizontal = 9.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = message,
            color = textColor,
        )
    }
}

@Preview
@Composable
fun TransactionStateComposablePreview() {
    TalerSurface {
        Column {

            val modifier = Modifier.padding(vertical = 6.dp)
            TransactionStateComposable(modifier, state = TransactionState(Pending, BankConfirmTransfer))
            TransactionStateComposable(modifier, state = TransactionState(Pending, KycInit))
            TransactionStateComposable(modifier, state = TransactionState(Pending, BalanceKycInit))
            TransactionStateComposable(modifier, state = TransactionState(Pending, KycRequired))
            TransactionStateComposable(modifier, state = TransactionState(Pending, BalanceKycRequired))
            TransactionStateComposable(modifier, state = TransactionState(Pending, MergeKycRequired))
            TransactionStateComposable(modifier, state = TransactionState(Pending, KycAuthRequired))
            TransactionStateComposable(modifier, state = TransactionState(Pending))
            TransactionStateComposable(modifier, state = TransactionState(Aborted))
            TransactionStateComposable(modifier, state = TransactionState(Aborting))
            TransactionStateComposable(modifier, state = TransactionState(Suspended))
            TransactionStateComposable(modifier, state = TransactionState(Failed, Repurchase))
            TransactionStateComposable(modifier, state = TransactionState(Failed))
            TransactionStateComposable(modifier, state = TransactionState(Finalizing))
            TransactionStateComposable(modifier, state = TransactionState(Expired))
            TransactionStateComposable(modifier, state = TransactionState(Done))

            TransactionStateComposable(modifier, state = TransactionState(Aborted), tx = TransactionWithdrawal(
                transactionId = "1234",
                timestamp = Timestamp.fromMillis(1722629432000L),
                txState = TransactionState(Aborted),
                txActions = emptyList(),
                exchangeBaseUrl = "exchange.demo.taler.net",
                withdrawalDetails = ManualTransfer(
                    exchangeCreditAccountDetails = emptyList(),
                    reserveClosingDelay = RelativeTime(10_000_000_000_000),
                ),
                amountRaw = Amount.zero("KUDOS"),
                amountEffective = Amount.zero("KUDOS"),
                scopes = listOf(ScopeInfo.Exchange(
                    currency = "KUDOS",
                    url = "exchange.demo.taler.net",
                ))
            ))
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TransactionStateComposableNightPreview() {
    TransactionStateComposablePreview()
}