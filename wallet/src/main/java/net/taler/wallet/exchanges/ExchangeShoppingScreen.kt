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

package net.taler.wallet.exchanges

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.Material3MenuGroup
import net.taler.wallet.compose.Material3MenuItemData
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.getAttrColor
import net.taler.wallet.launchInAppBrowser
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.main.ViewMode

@Composable
fun ExchangeShoppingScreen(
    model: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val viewMode by model.viewMode.collectAsStateLifecycleAware()
    val selectedScope = (viewMode as? ViewMode.Transactions)?.selectedScope
    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)

    val selectedBalance = remember(balanceState, selectedScope) {
        val balances = (balanceState as? BalanceState.Success)?.balances
        selectedScope?.let {
            balances?.find { it.scopeInfo == selectedScope }
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            Box(Modifier.padding(paddingValues)) {
                if (selectedScope == null) {
                    EmptyComposable(message = stringResource(R.string.exchange_unselected))
                } else if (selectedBalance == null) {
                    LoadingScreen()
                } else {
                    ExchangeShoppingComposable(
                        currency = selectedBalance.currency,
                        shoppingUrls = selectedBalance.shoppingUrls,
                    )
                }
            }
        }
    }
}

@Composable
fun ExchangeShoppingComposable(
    currency: String,
    shoppingUrls: List<String>,
) {
    val context = LocalContext.current
    val linkColor = Color(context.getAttrColor(android.R.attr.textColorLink))

    Column (
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(
                R.string.exchange_shopping_message,
                currency,
            ),
        )

        Box(Modifier.padding(horizontal = 16.dp)) {
            Material3MenuGroup(items = buildList {
                shoppingUrls.forEach { url ->
                    add(
                        Material3MenuItemData(
                            icon = {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null
                                )
                            },
                            title = {
                                Text(
                                    modifier = Modifier.basicMarquee(),
                                    text = url,
                                    color = linkColor,
                                )
                            },
                            onClick = { launchInAppBrowser(context, url) },
                        )
                    )
                }
            })
        }

        BottomInsetsSpacer()
    }
}

@Preview
@Composable
fun ExchangeShoppingComposablePreview() {
    TalerSurface {
        ExchangeShoppingComposable(
            currency = "CHF",
            shoppingUrls = listOf(
                "https://shopping.taler.net/",
                "https://shopping.taler.ar/",
                "https://shopping.taler-ops.ch/",
            )
        )
    }
}