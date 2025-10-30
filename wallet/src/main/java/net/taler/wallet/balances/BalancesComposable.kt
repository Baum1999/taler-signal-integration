/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo.Auditor
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.balances.ScopeInfo.Global
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.cardPaddings
import net.taler.wallet.donau.DonauSummaryItem
import net.taler.wallet.withdraw.WithdrawalError

// TODO: rename to AssetsComposable
@Composable
fun BalancesComposable(
    innerPadding: PaddingValues,
    state: BalanceState,
    onGetDemoMoneyClicked: () -> Unit,
    onBalanceClicked: (balance: BalanceItem) -> Unit,
    onPendingClicked: (balance: BalanceItem) -> Unit,
    onStatementClicked: (host: String) -> Unit,
) {
    when (state) {
        is BalanceState.None -> {}
        is BalanceState.Loading -> LoadingScreen()
        is BalanceState.Error -> WithdrawalError(state.error)
        is BalanceState.Success -> if (
            state.balances.isNotEmpty()
            || state.donauSummary.isNotEmpty()) {
            LazyColumn(
                Modifier
                    .consumeWindowInsets(innerPadding)
                    .fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                if (state.balances.isNotEmpty()) stickyHeader {
                    SectionHeader { Text(stringResource(R.string.assets_section_balances)) }
                }

                items(state.balances, key = { it.scopeInfo.hashCode() }) { balance ->
                    BalanceRow(
                        balance,
                        onClick = { onBalanceClicked(balance) },
                        onPendingClick = { onPendingClicked(balance) },
                    )
                }

                if (state.donauSummary.isNotEmpty()) stickyHeader {
                    SectionHeader { Text(stringResource(R.string.assets_section_statements)) }
                }

                items(state.donauSummary) { statement ->
                    StatementRow(
                        statement,
                        onClick = { onStatementClicked(statement.donauBaseUrl) },
                    )
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
fun SectionHeader(
    label: @Composable () -> Unit,
) {
    Box(Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.cardPaddings()) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium
                .copy(color = MaterialTheme.colorScheme.onBackground)) {
                label()
            }
        }
    }
}

@Composable
fun BalanceRow(
    balance: BalanceItem,
    onClick: () -> Unit,
    onPendingClick: () -> Unit,
) {
    OutlinedCard(Modifier.cardPaddings()) {
        Column {
            ListItem(
                modifier = Modifier
                    .animateContentSize()
                    .clickable { onClick() }
                    .padding(vertical = 6.dp),
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
            )

            if (!balance.pendingIncoming.isZero() || !balance.pendingOutgoing.isZero()) {
                HorizontalDivider()
                PendingComposable(balance, onPendingClick)
            }
        }
    }
}

@Composable
fun StatementRow(
    summaryItem: DonauSummaryItem,
    onClick: () -> Unit,
) {
    OutlinedCard(Modifier.cardPaddings()) {
        Column {
            ListItem(
                modifier = Modifier
                    .animateContentSize()
                    .clickable { onClick() }
                    .padding(vertical = 6.dp),
                headlineContent = {
                    Text(
                        summaryItem.amountReceiptsAvailable.toString(),
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                overlineContent = {
                    ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                        // FIXME: security risk, faking donauBaseUrl!
                        Text(stringResource(R.string.balance_scope_exchange,
                            summaryItem.legalDomain
                                ?: cleanExchange(summaryItem.donauBaseUrl)))
                    }
                },
                trailingContent = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            "${summaryItem.year}",
                            modifier = Modifier.padding(6.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun PendingComposable(
    balance: BalanceItem,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .animateContentSize()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        headlineContent = {
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
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
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
    )
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

    val donauSummary = listOf(
        DonauSummaryItem(
            donauBaseUrl = "https://donau.test.taler.net/",
            legalDomain = "Gnuland",
            year = 2025,
            amountReceiptsSubmitted = Amount.fromJSONString("KUDOS:10"),
            amountReceiptsAvailable = Amount.fromJSONString("KUDOS:10"),

        )
    )

    TalerSurface {
        BalancesComposable(
            innerPadding = PaddingValues(0.dp),
            state = BalanceState.Success(balances, donauSummary),
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onPendingClicked = {},
            onStatementClicked = {},
        )
    }
}

@Preview
@Composable
fun BalancesComposableEmptyPreview() {
    TalerSurface {
        BalancesComposable (
            innerPadding = PaddingValues(0.dp),
            state = BalanceState.Success(listOf(), listOf()),
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onPendingClicked = {},
            onStatementClicked = {},
        )
    }
}