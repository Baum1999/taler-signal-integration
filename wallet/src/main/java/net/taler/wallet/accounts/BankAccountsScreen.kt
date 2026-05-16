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

package net.taler.wallet.accounts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.accounts.ListBankAccountsResult.Error
import net.taler.wallet.accounts.ListBankAccountsResult.None
import net.taler.wallet.accounts.ListBankAccountsResult.Success
import net.taler.wallet.compose.Avatar
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountsScreen(
    model: MainViewModel,
    currency: String?,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val accounts by model.accountManager.bankAccounts.collectAsState()
    val devMode by model.devMode.observeAsState(false)

    LaunchedEffect(currency) {
        model.accountManager.listBankAccounts(currency)
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.settings_bank_accounts)) },
            onNavigateBack = onNavigateBack,
            floatingActionButton = {
                val tooltipState = rememberTooltipState()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.send_deposit_account_add)) } },
                    state = tooltipState,
                ) {
                    FloatingActionButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = { onNavigate(WalletDestination.AddBankAccount(), false) },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            },
        ) { paddingValues ->
            when (val acc = accounts) {
                is None -> LoadingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
                is Success -> BankAccountsList(
                    modifier = Modifier.padding(paddingValues),
                    accounts = acc.accounts,
                    onEdit = { account ->
                        onNavigate(WalletDestination.AddBankAccount(account.bankAccountId), false)
                    },
                    onForget = { account ->
                        model.accountManager.forgetBankAccount(account.bankAccountId) {
                            // TODO: show error
                        }
                    },
                )
                is Error -> ErrorComposable(
                    acc.error,
                    devMode = devMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun BankAccountsList(
    accounts: List<KnownBankAccountInfo>,
    onEdit: (account: KnownBankAccountInfo) -> Unit,
    onForget: (account: KnownBankAccountInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<KnownBankAccountInfo?>(null) }

    if (showDeleteDialog) AlertDialog(
        title = { Text(stringResource(R.string.send_deposit_account_forget_dialog_title)) },
        text = { Text(stringResource(R.string.send_deposit_account_forget_dialog_message)) },
        onDismissRequest = { showDeleteDialog = false },
        confirmButton = {
            TextButton(onClick = {
                accountToDelete?.let(onForget)
                accountToDelete = null
                showDeleteDialog = false
            }) {
                Text(stringResource(R.string.transactions_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                showDeleteDialog = false
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (accounts.isEmpty()) {
        EmptyComposable(
            modifier = modifier,
            message = stringResource(R.string.send_deposit_known_bank_accounts_empty)
        )
        return
    }

    LazyColumn(
        modifier = modifier
    ) {
        items(accounts, key = { it.paytoUri }) { account ->
            BankAccountRow(account,
                onForget = {
                    accountToDelete = account
                    showDeleteDialog = true
                },
                onClick = { onEdit(account) },
            )

        }
    }
}

@Composable
fun BankAccountRow(
    account: KnownBankAccountInfo,
    showMenu: Boolean = true,
    onClick: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
) {
    val paytoUri = remember(account.paytoUri) {
        PaytoUri.parse(account.paytoUri)
    }

    ListItem(
        modifier = Modifier.then(
            onClick?.let {
                Modifier.clickable { onClick() }
            } ?: Modifier
        ),
        leadingContent = {
            Avatar {
                when(paytoUri) {
                    is PaytoUriTalerBank -> Image(
                        painterResource(R.drawable.ic_actions),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    )

                    is PaytoUriBitcoin -> Icon(
                        Icons.Default.CurrencyBitcoin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    is PaytoUriCyclos -> Text("Cy",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    else -> Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        overlineContent = {
            when(paytoUri) {
                is PaytoUriIban -> Text(stringResource(R.string.send_deposit_iban))
                is PaytoUriTalerBank -> Text(stringResource(R.string.send_deposit_taler))
                is PaytoUriCyclos -> Text(stringResource(R.string.send_deposit_cyclos))
                is PaytoUriBitcoin -> Text(stringResource(R.string.send_deposit_bitcoin))
                else -> {}
            }
        },
        headlineContent = {
            Text(account.label
                ?: stringResource(R.string.send_deposit_no_alias))
        },
        supportingContent = {
            when(paytoUri) {
                is PaytoUriIban -> Text(paytoUri.iban)
                is PaytoUriTalerBank -> Text(paytoUri.account)
                is PaytoUriCyclos -> Text(paytoUri.receiverName)
                is PaytoUriBitcoin -> {
                    Text(remember(paytoUri.segwitAddresses) {
                        paytoUri.segwitAddresses.joinToString(" ")
                    })
                }
                else -> {}
            }
        },
        trailingContent = {
            // TODO: turn into dropdown menu if more options are added
            if (showMenu) IconButton(onClick = { onForget?.let { it() } }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.send_deposit_known_bank_account_delete),
                )
            }
        }
    )
}

val previewKnownAccounts = listOf(
    KnownBankAccountInfo(
        bankAccountId = "acct:EHHRQMZNDNAW3KZMBW0ATTNHCT3WH3TNX3HNMS4MKGK10E1W0YNG",
        paytoUri = PaytoUriIban(
            iban = "DE7489694250801",
            targetPath = "",
            params = emptyMap(),
            receiverName = "John Doe",
            receiverPostalCode = "1234",
            receiverTown = "Texas",
        ).paytoUri,
        kycCompleted = true,
        currencies = listOf("KUDOS"),
        label = "GLS",
    ),

    KnownBankAccountInfo(
        bankAccountId = "acct:EHHRQMZNDNAW3KZMBW0ATTNHCT3WH3TNX3HNMS4MKGK10E1W0YNG",
        paytoUri = PaytoUriTalerBank(
            host = "bank.test.taler.net",
            account = "john123",
            targetPath = "",
            params = emptyMap(),
            receiverName = "John Doe",
        ).paytoUri,
        kycCompleted = true,
        currencies = listOf("TESTKUDOS"),
        label = "Main on test",
    ),

    KnownBankAccountInfo(
        bankAccountId = "acct:EHHRQMZNDNAW3KZMBW0ATTNHCT3WH3TNX3HNMS4MKGK10E1W0YNG",
        paytoUri = PaytoUriCyclos(
            host = "demo.cyclos.org",
            account = "john123",
            targetPath = "",
            params = emptyMap(),
            receiverName = "John Doe",
        ).paytoUri,
        kycCompleted = true,
        currencies = listOf("UI"),
        label = "Cyclos demo",
    ),

    KnownBankAccountInfo(
        bankAccountId = "acct:EHHRQMZNDNAW3KZMBW0ATTNHCT3WH3TNX3HNMS4MKGK10E1W0YNG",
        paytoUri = PaytoUriBitcoin(
            segwitAddresses = listOf("bc1qkrnmwd8t4yxzpha8gk3w8h8lyecfp2ra9yvgf9"),
            targetPath = "",
            params = emptyMap(),
            receiverName = "John Doe",
        ).paytoUri,
        kycCompleted = true,
        currencies = listOf("BTC"),
        label = "Android wallet",
    ),
)

@Preview
@Composable
fun KnownAccountsListPreview() {
    TalerSurface {
        BankAccountsList(
            accounts = previewKnownAccounts,
            onEdit = {},
            onForget = {},
        )
    }
}

@Preview
@Composable
fun KnownAccountsListEmptyPreview() {
    TalerSurface {
        BankAccountsList(
            accounts = listOf(),
            onEdit = {},
            onForget = {},
        )
    }
}