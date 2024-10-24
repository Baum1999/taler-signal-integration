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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.Timestamp
import net.taler.common.toRelativeTime
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceItem
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.AmountType.Negative
import net.taler.wallet.transactions.AmountType.Neutral
import net.taler.wallet.transactions.AmountType.Positive
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.TransactionMajorState.Aborted
import net.taler.wallet.transactions.TransactionMajorState.Aborting
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionMajorState.Failed
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycInit
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycRequired
import net.taler.wallet.transactions.TransactionMinorState.BankConfirmTransfer
import net.taler.wallet.transactions.TransactionMinorState.KycRequired
import net.taler.wallet.transactions.TransactionsResult.Error
import net.taler.wallet.transactions.TransactionsResult.None
import net.taler.wallet.transactions.TransactionsResult.Success

@Composable
fun TransactionsComposable(
    balance: BalanceItem,
    currencySpec: CurrencySpecification?,
    txResult: TransactionsResult,
    onTransactionClick: (tx: Transaction) -> Unit,
    onShowBalancesClicked: () -> Unit,
) {
    when (txResult) {
        is None -> LoadingScreen()
        is Error -> {} // TODO: render error!
        is Success -> {
            LazyColumn(Modifier.fillMaxHeight()) {
                item {
                    TransactionsHeader(
                        balance = balance,
                        spec = currencySpec,
                        onShowBalancesClicked = onShowBalancesClicked,
                    )
                }

                items(txResult.transactions, key = { it.transactionId }) { tx ->
                    TransactionRow(tx, currencySpec) {
                        onTransactionClick(tx)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionsHeader(
    balance: BalanceItem,
    spec: CurrencySpecification?,
    onShowBalancesClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedCard(
            Modifier
                .weight(1f)
                .padding(8.dp)
                .clickable { onShowBalancesClicked() },
        ) {
            ListItem(
                modifier = Modifier.animateContentSize(),

                headlineContent = {
                    Text(
                        getHeaderCurrency(balance, spec),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },

                supportingContent = {
                    if (balance.scopeInfo is Exchange) {
                        Text(
                            cleanExchange(balance.scopeInfo.url),
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },

                trailingContent = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                stringResource(R.string.transactions_balance),
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                balance.available.withSpec(spec).toString(showSymbol = false),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun TransactionRow(
    tx: Transaction,
    spec: CurrencySpecification?,
    onTransactionClick: () -> Unit,
) {
    val context = LocalContext.current

    ListItem(
        modifier = Modifier
            .defaultMinSize(minHeight = 80.dp)
            .clickable { onTransactionClick() },

        trailingContent = {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TransactionAmountInfo(tx, spec)
            }
        },

        leadingContent = {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(tx.icon), contentDescription = null)
            }
        },

        headlineContent = {
            Text(
                tx.getTitle(context),
                modifier = Modifier.padding(vertical = 3.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        },

        supportingContent = {
            TransactionExtraInfo(tx)
        },

        overlineContent = { Text(tx.timestamp.ms.toRelativeTime(context).toString()) },
    )
}

@Composable
fun TransactionAmountInfo(
    tx: Transaction,
    spec: CurrencySpecification?,
) {
    Column(horizontalAlignment = Alignment.End) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
            val amountStr = tx.amountEffective.withSpec(spec).toString(showSymbol = false)
            when (tx.amountType) {
                Positive -> Text(
                    stringResource(R.string.amount_positive, amountStr),
                    color = if (tx.txState.major == Pending)
                        Color.Unspecified else colorResource(R.color.green),
                )
                Negative -> Text(
                    stringResource(R.string.amount_negative, amountStr),
                    color = if (tx.txState.major == Pending)
                        Color.Unspecified else MaterialTheme.colorScheme.error,
                )
                Neutral -> Text(amountStr)
            }
        }

        if (tx.txState.major == Pending) {
            Badge(Modifier.padding(top = 3.dp)) {
                Text(stringResource(R.string.transaction_pending))
            }
        }
    }
}

@Composable
fun TransactionExtraInfo(tx: Transaction) {
    when {
        tx.txState.major == Aborted -> Text(
            stringResource(R.string.payment_aborted),
            color = MaterialTheme.colorScheme.error,
        )

        tx.txState.major == Failed -> Text(
            stringResource(R.string.payment_failed),
            color = MaterialTheme.colorScheme.error,
        )

        tx.txState.major == Aborting -> Text(
            stringResource(R.string.payment_aborting),
            color = MaterialTheme.colorScheme.error,
        )

        tx.txState.major == Pending -> when(tx.txState.minor) {
            BankConfirmTransfer -> Text(stringResource(R.string.withdraw_waiting_confirm))
            BalanceKycInit -> Text(stringResource(R.string.transaction_preparing_kyc))
            KycRequired -> Text(stringResource(R.string.transaction_action_kyc_bank))
            BalanceKycRequired -> Text(stringResource(R.string.transaction_action_kyc_balance))
            else -> Text(stringResource(R.string.transaction_pending))
        }

        tx is TransactionWithdrawal && !tx.confirmed -> Text(stringResource(R.string.withdraw_waiting_confirm))
        tx is TransactionPeerPushCredit && tx.info.summary != null -> Text(tx.info.summary)
        tx is TransactionPeerPushDebit && tx.info.summary != null -> Text(tx.info.summary)
        tx is TransactionPeerPullCredit && tx.info.summary != null -> Text(tx.info.summary)
        tx is TransactionPeerPullDebit && tx.info.summary != null -> Text(tx.info.summary)
    }
}

@Composable
private fun getHeaderCurrency(
    balance: BalanceItem,
    spec: CurrencySpecification?,
) = if (spec != null) {
    if (spec.symbol != null && spec.name != spec.symbol) {
        // Name (symbol)
        stringResource(R.string.transactions_currency, spec.name, spec.symbol!!)
    } else if (spec.name != balance.currency) {
        // Name (currency string)
        stringResource(R.string.transactions_currency, spec.name, balance.currency)
    } else balance.currency
} else balance.currency

private val previewBalance = BalanceItem(
    scopeInfo = Exchange("MXN", "https://exchange.taler.banxico.org.mx"),
    available = Amount.fromJSONString("MXN:5.50"),
    pendingIncoming = Amount.fromJSONString("MXN:1.40"),
    pendingOutgoing = Amount.fromJSONString("MXN:0"),
)

@Preview
@Composable
fun TransactionsComposableDonePreview() {
    val t = TransactionWithdrawal(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Done),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.demo.taler.net/",
        withdrawalDetails = WithdrawalDetails.TalerBankIntegrationApi(false),
        amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
    )

    val transactions = listOf(t)

    TalerSurface {
        TransactionsComposable(
            balance = previewBalance,
            currencySpec = null,
            txResult = Success(transactions),
            onTransactionClick = {},
            onShowBalancesClicked = {},
        )
    }
}

@Preview
@Composable
fun TransactionsComposablePendingPreview() {
    val t = TransactionWithdrawal(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Pending),
        txActions = listOf(Retry, Suspend, Abort),
        exchangeBaseUrl = "https://exchange.demo.taler.net/",
        withdrawalDetails = WithdrawalDetails.TalerBankIntegrationApi(false),
        amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
    )

    val transactions = listOf(t)

    TalerSurface {
        TransactionsComposable(
            balance = previewBalance,
            currencySpec = null,
            txResult = Success(transactions),
            onTransactionClick = {},
            onShowBalancesClicked = {},
        )
    }
}