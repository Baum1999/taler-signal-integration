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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo.Auditor
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.balances.ScopeInfo.Global
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.cardPaddings
import net.taler.wallet.donau.DonauSummaryItem

@Composable
fun BalancesListComposable(
    innerPadding: PaddingValues,
    bottomFabSkip: Dp,
    state: BalanceState,
    devMode: Boolean,
    networkStatus: Boolean,
    onWithdrawMoneyClicked: () -> Unit,
    onGetDemoMoneyClicked: () -> Unit,
    onBalanceClicked: (balance: BalanceItem) -> Unit,
    onStatementClicked: (host: String) -> Unit,
) {
    if (state.showWelcome()) {
        EmptyBalancesComposable(
            innerPadding = innerPadding,
            networkStatus = networkStatus,
            onWithdrawMoneyClicked,
            onGetDemoMoneyClicked,
        )

        return
    }

    when (state) {
        is BalanceState.Loading -> LoadingScreen()
        is BalanceState.Error -> ErrorComposable(state.error,
            devMode = devMode,
            modifier = Modifier.fillMaxSize())
        is BalanceState.Success -> LazyColumn(
            Modifier
                .consumeWindowInsets(innerPadding)
                .fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            items(state.balances, key = { it.scopeInfo.hashCode() }) { balance ->
                BalanceRow(
                    balance,
                    onClick = { onBalanceClicked(balance) },
                )
            }

            if (state.donauSummary.isNotEmpty()) stickyHeader {
                SectionHeader { Text(stringResource(R.string.balances_section_statements)) }
            }

            items(state.donauSummary) { statement ->
                StatementRow(
                    statement,
                    onClick = { onStatementClicked(statement.donauBaseUrl) },
                )
            }

            item {
                Spacer(Modifier.height(bottomFabSkip))
            }
        }

        else -> {}
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
) {
    ElevatedCard(Modifier.cardPaddings()) {
        Column {
            ListItem(
                modifier = Modifier
                    .animateContentSize()
                    .clickable { onClick() }
                    .padding(vertical = 6.dp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        }
    }
}

@Composable
fun StatementRow(
    summaryItem: DonauSummaryItem,
    onClick: () -> Unit,
) {
    ElevatedCard(Modifier.cardPaddings()) {
        Column {
            ListItem(
                modifier = Modifier
                    .animateContentSize()
                    .clickable { onClick() }
                    .padding(vertical = 6.dp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

@Preview
@Composable
fun BalancesListComposablePreview() {
    val balances = listOf(
        BalanceItem(
            scopeInfo = Global("CHF"),
            available = Amount.fromJSONString("CHF:10.20"),
            pendingIncoming = Amount.fromJSONString("CHF:1.20"),
            pendingOutgoing = Amount.fromJSONString("CHF:0.40"),
            disablePeerPayments = false,
        ),
        BalanceItem(
            scopeInfo = Exchange("KUDOS", "https://exchange.demo.taler.net"),
            available = Amount.fromJSONString("KUDOS:1407.37"),
            pendingIncoming = Amount.fromJSONString("KUDOS:0"),
            pendingOutgoing = Amount.fromJSONString("KUDOS:2.15"),
            disablePeerPayments = false,
        ),
        BalanceItem(
            scopeInfo = Auditor("MXN", "https://auditor.taler.banxico.org.mx"),
            available = Amount.fromJSONString("MXN:5.50"),
            pendingIncoming = Amount.fromJSONString("MXN:1.40"),
            pendingOutgoing = Amount.fromJSONString("MXN:0"),
            disablePeerPayments = false,
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
        BalancesListComposable(
            innerPadding = PaddingValues(0.dp),
            bottomFabSkip = 0.dp,
            state = BalanceState.Success(balances, donauSummary),
            devMode = false,
            networkStatus = true,
            onWithdrawMoneyClicked = {},
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onStatementClicked = {},
        )
    }
}

@Preview
@Composable
fun BalancesListComposableErrorPreview() {
    TalerSurface {
        BalancesListComposable (
            innerPadding = PaddingValues(0.dp),
            bottomFabSkip = 0.dp,
            state = BalanceState.Error(TalerErrorInfo
                .makeCustomError("Balances could not be loaded")),
            devMode = false,
            networkStatus = false,
            onWithdrawMoneyClicked = {},
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onStatementClicked = {},
        )
    }
}

@Preview
@Composable
fun BalancesListComposableEmptyPreview() {
    TalerSurface {
        BalancesListComposable (
            innerPadding = PaddingValues(0.dp),
            bottomFabSkip = 0.dp,
            state = BalanceState.Success(listOf(), listOf()),
            devMode = false,
            networkStatus = false,
            onWithdrawMoneyClicked = {},
            onGetDemoMoneyClicked = {},
            onBalanceClicked = {},
            onStatementClicked = {},
        )
    }
}