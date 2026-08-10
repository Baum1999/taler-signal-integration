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

package net.taler.wallet.transfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.taler.lib.android.copyToClipBoard
import net.taler.lib.android.openUri
import net.taler.lib.android.shareText
import net.taler.wallet.R
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.transactions.TransactionDeposit
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionMinorState.ExchangeWaitReserve
import net.taler.wallet.transactions.TransactionMinorState.KycAuthRequired
import net.taler.wallet.transactions.TransactionWithdrawal
import net.taler.wallet.transactions.TransferOption
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.withdraw.TransferData

@Composable
fun WireTransferDetailsScreen(
    model: MainViewModel,
    showQrCodes: Boolean,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val sharePaymentTitle = stringResource(R.string.share_payment)
    val transactionManager = model.transactionManager
    val exchangeManager = model.exchangeManager

    val selectedTx by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
    val selectedOption by transactionManager.selectedTransferOption.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)
    val paytoUri = when (val option = selectedOption) {
        is TransferOption.Payto -> option.paytoUri
        is TransferOption.SwissQrBill -> option.paytoUri
        else -> null
    }
    var showMenu by remember(paytoUri) { mutableStateOf(false) }
    val copyPaytoUri = stringResource(R.string.copy_payto_uri)

    LaunchedEffect(selectedTx) {
        if (selectedTx?.txState?.major == Done ||
            (selectedTx?.txState?.minor != KycAuthRequired &&
            selectedTx?.txState?.minor != ExchangeWaitReserve)
        ) {
            onNavigateBack()
        }
    }

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = { Text(stringResource(R.string.wire_transfer)) },
        onNavigateBack = onNavigateBack,
        actions = {
            if (paytoUri != null) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.menu),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(copyPaytoUri) },
                            onClick = {
                                showMenu = false
                                copyToClipBoard(context, copyPaytoUri, paytoUri)
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        val tx = selectedTx ?: return@GlobalScaffold

        val spec = tx.amountRaw.currency.let { currency ->
            exchangeManager.getSpecForCurrency(currency, tx.scopes)
        }

        val bankAppClick: (TransferData) -> Unit = { transfer ->
            context.openUri(uri = transfer.withdrawalAccount.paytoUri, title = sharePaymentTitle)
        }

        val shareClick: (TransferData) -> Unit = { transfer ->
            context.shareText(text = transfer.withdrawalAccount.paytoUri)
        }

        val option = selectedOption
        if (paytoUri == null || option == null) return@GlobalScaffold

        when (tx) {
            is TransactionWithdrawal -> {
                val transferData = remember(option, tx) {
                    WithdrawalExchangeAccountDetails(
                        paytoUri = paytoUri,
                        status = WithdrawalExchangeAccountDetails.Status.Ok,
                    ).getTransferDetails(
                        amountRaw = tx.amountRaw,
                        amountEffective = tx.amountEffective,
                        transferOption = option,
                    )
                }

                if (transferData != null) {
                    ScreenTransfer(
                        modifier = Modifier.padding(paddingValues),
                        transfer = transferData,
                        spec = spec,
                        bankAppClick = bankAppClick,
                        shareClick = shareClick,
                        showQrCodes = showQrCodes,
                        devMode = devMode,
                        transferContext = TransferContext.ManualWithdrawal,
                    )
                } else {
                    EmptyComposable(modifier = Modifier.padding(paddingValues))
                }
            }

            is TransactionDeposit -> {
                val transferData = remember(option, tx) {
                    WithdrawalExchangeAccountDetails(
                        paytoUri = paytoUri,
                        status = WithdrawalExchangeAccountDetails.Status.Ok,
                    ).getTransferDetails(
                        amountRaw = tx.amountRaw,
                        amountEffective = tx.amountEffective,
                        transferOption = option,
                    )
                }

                if (transferData != null) {
                    ScreenTransfer(
                        modifier = Modifier.padding(paddingValues),
                        transfer = transferData,
                        spec = spec,
                        bankAppClick = bankAppClick,
                        shareClick = shareClick,
                        showQrCodes = showQrCodes,
                        devMode = devMode,
                        transferContext = TransferContext.DepositKycAuth(
                            tx.kycAuthTransferInfo?.debitPaytoUri ?: "",
                        ),
                    )
                } else {
                    EmptyComposable(modifier = Modifier.padding(paddingValues))
                }
            }

            else -> return@GlobalScaffold
        }
    }
}
