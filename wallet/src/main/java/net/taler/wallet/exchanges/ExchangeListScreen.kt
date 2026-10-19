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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletResponse
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
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val exchangeManager = model.exchangeManager
    val balanceManager = model.balanceManager
    val exchanges by exchangeManager.exchanges.observeAsState(emptyList())
    val devMode by model.devMode.observeAsState(false)
    val addError by exchangeManager.addError.observeAsState()
    val listError by exchangeManager.listError.observeAsState()
    val deleteError by exchangeManager.deleteError.observeAsState()
    val reloadError by exchangeManager.reloadError.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var addInProgress by remember { mutableStateOf(false) }
    var addErrorMessage by remember { mutableStateOf<String?>(null) }
    var exchangeToDelete by remember { mutableStateOf<ExchangeItem?>(null) }

    val addErrorText = stringResource(R.string.exchange_add_error)

    LaunchedEffect(addError) {
        addError?.getIfNotConsumed()?.let {
            addInProgress = false
            if (showAddDialog) {
                // Keep the dialog open so the user can correct the address.
                addErrorMessage = addErrorText
            } else {
                snackbarHostState.showSnackbar(addErrorText, duration = SnackbarDuration.Long)
            }
        }
    }

    LaunchedEffect(listError) {
        listError?.getIfNotConsumed()?.let { onShowError(it) }
    }

    LaunchedEffect(deleteError) {
        deleteError?.getIfNotConsumed()?.let { onShowError(it) }
    }

    LaunchedEffect(reloadError) {
        reloadError?.getIfNotConsumed()?.let { onShowError(it) }
    }

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        onNavigateBack = onNavigateBack,
        title = { Text(stringResource(R.string.exchange_list_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.navigationBarsPadding(),
                onClick = {
                    addErrorMessage = null
                    showAddDialog = true
                },
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
                            if (action == ExchangeAction.Delete) {
                                exchangeToDelete = exchange
                            } else {
                                handleExchangeAction(
                                    scope = scope,
                                    exchangeManager = exchangeManager,
                                    balanceManager = balanceManager,
                                    exchange = exchange,
                                    action = action,
                                    onNavigate = onNavigate,
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddExchangeDialog(
            onDismiss = { if (!addInProgress) showAddDialog = false },
            onConfirm = { url ->
                addErrorMessage = null
                addInProgress = true
                exchangeManager.add(url) {
                    addInProgress = false
                    showAddDialog = false
                }
            },
            loading = addInProgress,
            errorMessage = addErrorMessage,
        )
    }

    if (exchangeToDelete != null) {
        DeleteExchangeDialog(
            onDismiss = {
                exchangeToDelete = null
            },
            onConfirm = { purge ->
                exchangeManager.delete(exchangeToDelete!!.exchangeBaseUrl, purge)
                exchangeToDelete = null
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
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "${stringResource(R.string.menu)}: ${exchange.name}",
                    )
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
                    onClick = { onAction(ExchangeAction.GlobalAdd) },
                    enabled = exchange.masterPub != null,
                )
            } else if (exchange.scopeInfo is ScopeInfo.Global) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exchange_global_delete)) },
                    onClick = { onAction(ExchangeAction.GlobalDelete) },
                    enabled = exchange.masterPub != null,
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
    loading: Boolean = false,
    errorMessage: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.focusRequester(focusRequester),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exchange_list_add)) },
        icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.exchange_add_url)) },
                    placeholder = { Text("https://") },
                    maxLines = 1,
                    enabled = !loading,
                    isError = errorMessage != null,
                )
                errorMessage?.let { message ->
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.ok))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !loading,
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun DeleteExchangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (purge: Boolean) -> Unit,
) {
    var purge by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exchange_delete)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = purge, onCheckedChange = { purge = it })
                Text(stringResource(R.string.exchange_delete_force))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(purge) }) {
                Text(stringResource(R.string.transactions_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
                val response = exchangeManager.getExchangeTos(exchange.exchangeBaseUrl)
                if (response is WalletResponse.Success) {
                    exchangeManager.forgetCurrentTos(
                        exchangeBaseUrl = exchange.exchangeBaseUrl,
                        currentEtag = response.result.currentEtag,
                    )
                }
            }
        }
        ExchangeAction.GlobalAdd -> if (exchange.masterPub != null) {
            balanceManager.addGlobalCurrencyExchange(
                currency = exchange.currency ?: return,
                exchange = exchange,
                exchangeMasterPub = exchange.masterPub,
                onSuccess = {},
                onError = {}
            )
        }
        ExchangeAction.GlobalDelete -> if (exchange.masterPub != null) {
            balanceManager.removeGlobalCurrencyExchange(
                currency = exchange.currency ?: return,
                exchange = exchange,
                exchangeMasterPub = exchange.masterPub,
                onSuccess = {},
                onError = {}
            )
        }
        ExchangeAction.Delete -> exchangeManager.delete(exchange.exchangeBaseUrl)
    }
}
