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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.balances.BalanceManager
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeListScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val exchangeManager = model.exchangeManager
    val balanceManager = model.balanceManager
    val exchanges by exchangeManager.exchanges.observeAsState(emptyList())
    val devMode by model.devMode.observeAsState(false)
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    GlobalScaffold(
        model = model,
        onNavigateBack = onNavigateBack,
        title = { Text(stringResource(R.string.exchange_list_title)) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.navigationBarsPadding(),
                onClick = { showAddDialog = true },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.exchange_list_add))
            }
        }
    ) { padding ->
        if (exchanges.isEmpty()) {
            EmptyComposable(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = stringResource(R.string.exchange_list_empty),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(exchanges) { exchange ->
                    ExchangeItemComposable(
                        exchange = exchange,
                        devMode = devMode,
                        onAction = { action ->
                            handleExchangeAction(
                                scope = scope,
                                exchangeManager = exchangeManager,
                                balanceManager = balanceManager,
                                exchange = exchange,
                                action = action,
                                onNavigate = onNavigate,
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddExchangeDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                exchangeManager.add(url)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ExchangeItemComposable(
    exchange: ExchangeItem,
    devMode: Boolean,
    onAction: (ExchangeAction) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = exchange.name, style = MaterialTheme.typography.titleMedium)
            val currencyText = exchange.currency?.let {
                stringResource(R.string.exchange_list_currency, it)
            } ?: stringResource(R.string.exchange_not_contacted)
            Text(text = currencyText, style = MaterialTheme.typography.bodyMedium)
        }
        if (exchange.currency != null) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                ExchangeDropDownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    exchange = exchange,
                    devMode = devMode,
                    onAction = {
                        showMenu = false
                        onAction(it)
                    }
                )
            }
        }
    }
}

@Composable
fun ExchangeDropDownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    exchange: ExchangeItem,
    devMode: Boolean,
    onAction: (ExchangeAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.exchange_reload)) },
            onClick = { onAction(ExchangeAction.Reload) }
        )

        if (exchange.tosStatus.isAccepted()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exchange_tos_view)) },
                onClick = { onAction(ExchangeAction.ViewTos) }
            )
            if (devMode) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exchange_tos_forget)) },
                    onClick = { onAction(ExchangeAction.ForgetTos) }
                )
            }
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exchange_tos_accept)) },
                onClick = { onAction(ExchangeAction.AcceptTos) }
            )
        }

        if (devMode) {
            if (exchange.scopeInfo is ScopeInfo.Exchange) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exchange_global_add)) },
                    onClick = { onAction(ExchangeAction.GlobalAdd) }
                )
            } else if (exchange.scopeInfo is ScopeInfo.Global) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exchange_global_delete)) },
                    onClick = { onAction(ExchangeAction.GlobalDelete) }
                )
            }
        }

        DropdownMenuItem(
            text = { Text(stringResource(R.string.exchange_delete)) },
            onClick = { onAction(ExchangeAction.Delete) }
        )
    }
}

@Composable
fun AddExchangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.focusRequester(focusRequester),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exchange_list_add)) },
        icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.exchange_add_url)) },
                placeholder = { Text("https://") },
                maxLines = 1,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

enum class ExchangeAction {
    Reload,
    ViewTos,
    AcceptTos,
    ForgetTos,
    GlobalAdd,
    GlobalDelete,
    Delete
}

fun handleExchangeAction(
    scope: CoroutineScope,
    exchangeManager: ExchangeManager,
    balanceManager: BalanceManager,
    exchange: ExchangeItem,
    action: ExchangeAction,
    onNavigate: NavigateCallback,
) {
    when (action) {
        ExchangeAction.Reload -> exchangeManager.reload(exchange.exchangeBaseUrl)
        ExchangeAction.ViewTos -> {
            onNavigate(WalletDestination.ReviewExchangeTOS(exchange.exchangeBaseUrl, true), false)
        }
        ExchangeAction.AcceptTos -> {
            onNavigate(WalletDestination.ReviewExchangeTOS(exchange.exchangeBaseUrl, false), false)
        }
        ExchangeAction.ForgetTos -> {
            scope.launch {
                exchangeManager.getExchangeTos(exchange.exchangeBaseUrl)?.let { tos ->
                    exchangeManager.forgetCurrentTos(exchange.exchangeBaseUrl, tos.currentEtag)
                }
            }
        }
        ExchangeAction.GlobalAdd -> {
            balanceManager.addGlobalCurrencyExchange(
                currency = exchange.currency ?: return,
                exchange = exchange,
                onSuccess = {},
                onError = {}
            )
        }
        ExchangeAction.GlobalDelete -> {
            balanceManager.removeGlobalCurrencyExchange(
                currency = exchange.currency ?: return,
                exchange = exchange,
                onSuccess = {},
                onError = {}
            )
        }
        ExchangeAction.Delete -> exchangeManager.delete(exchange.exchangeBaseUrl)
    }
}
