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

package net.taler.wallet.balances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo.Auditor
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.balances.ScopeInfo.Global
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionsComposable
import net.taler.wallet.transactions.TransactionsResult
import net.taler.wallet.withdraw.WithdrawalError

@Composable
fun BalancesComposable(
    innerPadding: PaddingValues,
    state: BalanceState,
    txResult: TransactionsResult,
    selectedScope: ScopeInfo?,
    selectedCurrencySpec: CurrencySpecification?,
    onGetDemoMoneyClicked: () -> Unit,
    onBalanceClicked: (balance: BalanceItem) -> Unit,
    onTransactionClicked: (tx: Transaction) -> Unit,
    onTransactionsDelete: (txIds: List<String>) -> Unit,
    onShowBalancesClicked: () -> Unit,
) {
    when (state) {
        is BalanceState.None -> {}
        is BalanceState.Loading -> LoadingScreen()
        is BalanceState.Error -> WithdrawalError(state.error)
        is BalanceState.Success -> if (state.balances.isNotEmpty()) {
            if (selectedScope == null) {
                LazyColumn(
                    Modifier
                        .consumeWindowInsets(innerPadding)
                        .fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    items(state.balances, key = { it.scopeInfo.hashCode() }) { balance ->
                        BalanceRow(balance) {
                            onBalanceClicked(balance)
                        }
                    }
                }
            } else {
                val balance = remember(state.balances, selectedScope) {
                    state.balances.find { it.scopeInfo == selectedScope }
                }

                balance?.let {
                    TransactionsComposable(
                        innerPadding = innerPadding,
                        balance = it,
                        currencySpec = selectedCurrencySpec,
                        txResult = txResult,
                        onTransactionClick = onTransactionClicked,
                        onTransactionsDelete = onTransactionsDelete,
                        onShowBalancesClicked = onShowBalancesClicked,
                    )
                } ?: run {
                    onShowBalancesClicked()
                }
            }
        } else {
            EmptyBalancesComposable(
                innerPadding = innerPadding,
                onGetDemoMoneyClicked,
            )
        }
    }
}

@Composable
fun BalanceRow(
    balance: BalanceItem,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp,
            ).clickable { onClick() },
    ) {
        ListItem(
            modifier = Modifier
                .animateContentSize()
                .padding(6.dp),
            headlineContent = {
                Text(
                    balance.available.toString(),
                    style = MaterialTheme.typography.displaySmall,
                )
            },
            overlineContent = {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    when (balance.scopeInfo) {
                        is Exchange -> Text(
                            stringResource(
                                R.string.balance_scope_exchange,
                                cleanExchange(balance.scopeInfo.url)
                            ),
                        )

                        is Auditor -> Text(
                            stringResource(
                                R.string.balance_scope_auditor,
                                cleanExchange(balance.scopeInfo.url)
                            ),
                        )

                        else -> {}
                    }
                }
            },
            supportingContent = {
                Column {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                        AnimatedVisibility(!balance.pendingIncoming.isZero()) {
                            Text(
                                stringResource(
                                    R.string.balances_inbound_amount,
                                    balance.pendingIncoming.toString(showSymbol = false),
                                ),
                                color = colorResource(R.color.green),
                            )
                        }

                        AnimatedVisibility(!balance.pendingOutgoing.isZero()) {
                            Text(
                                stringResource(
                                    R.string.balances_outbound_amount,
                                    balance.pendingOutgoing.toString(showSymbol = false)
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun EmptyBalancesComposable(
    innerPadding: PaddingValues,
    onGetDemoMoneyClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.balances_empty_state),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(32.dp))

        Button(onGetDemoMoneyClicked) {
            Text(stringResource(R.string.balances_empty_get_money))
        }
    }
}

@Preview
@Composable
fun BalancesComposablePreview() {
    val balances = listOf(
        BalanceItem(
            scopeInfo = Global("CHF"),
            available = Amount.fromJSONString("CHF:10.20"),
            pendingIncoming = Amount.fromJSONString("CHF:1.20"),
            pendingOutgoing = Amount.fromJSONString("CHF:0.40"),
        ),
        BalanceItem(
            scopeInfo = Exchange("KUDOS", "https://exchange.demo.taler.net"),
            available = Amount.fromJSONString("KUDOS:1407.37"),
            pendingIncoming = Amount.fromJSONString("KUDOS:0"),
            pendingOutgoing = Amount.fromJSONString("KUDOS:2.15"),
        ),
        BalanceItem(
            scopeInfo = Auditor("MXN", "https://auditor.taler.banxico.org.mx"),
            available = Amount.fromJSONString("MXN:5.50"),
            pendingIncoming = Amount.fromJSONString("MXN:1.40"),
            pendingOutgoing = Amount.fromJSONString("MXN:0"),
        ),
    )

    TalerSurface {
        BalancesComposable(
            innerPadding = PaddingValues(0.dp),
            state = BalanceState.Success(balances),
            txResult = TransactionsResult.Success(listOf()),
            selectedScope = null,
            selectedCurrencySpec = null,
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onTransactionClicked = {},
            onTransactionsDelete = {},
            onShowBalancesClicked = {},
        )
    }
}

@Preview
@Composable
fun BalancesComposableEmptyPreview() {
    TalerSurface {
        BalancesComposable(
            innerPadding = PaddingValues(0.dp),
            state = BalanceState.Success(listOf()),
            txResult = TransactionsResult.Success(listOf()),
            selectedScope = null,
            selectedCurrencySpec = null,
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onTransactionClicked = {},
            onTransactionsDelete = {},
            onShowBalancesClicked = {},
        )
    }
}