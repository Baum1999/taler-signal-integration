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

package net.taler.wallet.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.accounts.ListBankAccountsResult.Error
import net.taler.wallet.accounts.ListBankAccountsResult.None
import net.taler.wallet.accounts.ListBankAccountsResult.Success
import net.taler.wallet.compose.Avatar
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.showError
import net.taler.wallet.withdraw.WithdrawalError

class BankAccountsFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()
    private var currency: String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        currency = arguments?.getString("currency")

        setContent {
            val accounts by model.accountManager.bankAccounts.collectAsState()

            TalerSurface {
                Scaffold(
                    floatingActionButton = {
                        val tooltipState = rememberTooltipState()
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.send_deposit_account_add)) } },
                            state = tooltipState,
                        ) {
                            FloatingActionButton(onClick = {
                                findNavController().navigate(R.id.action_nav_bank_accounts_to_add_bank_account)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ) { innerPadding ->
                    when (val acc = accounts) {
                        is None -> LoadingScreen()
                        is Success -> BankAccountsList(
                            innerPadding,
                            acc.accounts,
                            onEdit = { account ->
                                // TODO: navigate
                                val args = bundleOf("bankAccountId" to account.bankAccountId)
                                findNavController().navigate(R.id.action_nav_bank_accounts_to_add_bank_account, args)
                            },
                            onForget = { account ->
                                model.accountManager.forgetBankAccount(account.bankAccountId) {
                                    showError(it)
                                }
                            },
                        )
                        is Error -> WithdrawalError(acc.error)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.accountManager.listBankAccounts(currency)
            }
        }
    }
}

@Composable
fun BankAccountsList(
    innerPadding: PaddingValues,
    accounts: List<KnownBankAccountInfo>,
    onEdit: (account: KnownBankAccountInfo) -> Unit,
    onForget: (account: KnownBankAccountInfo) -> Unit,
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier.padding(
                    vertical = 32.dp,
                    horizontal = 16.dp,
                ),
                text = stringResource(R.string.send_deposit_known_bank_accounts_empty),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .consumeWindowInsets(innerPadding)
            .fillMaxSize()
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
                Icon(
                    when(paytoUri) {
                        is PaytoUriTalerBank -> Icons.Default.Dns
                        is PaytoUriBitcoin -> Icons.Default.CurrencyBitcoin
                        else -> Icons.Default.AccountBalance
                    },
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    contentDescription = null,
                )
            }
        },
        overlineContent = {
            when(paytoUri) {
                is PaytoUriIban -> Text(stringResource(R.string.send_deposit_iban))
                is PaytoUriTalerBank -> Text(stringResource(R.string.send_deposit_taler))
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
            innerPadding = PaddingValues(0.dp),
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
            innerPadding = PaddingValues(0.dp),
            accounts = listOf(),
            onEdit = {},
            onForget = {},
        )
    }
}