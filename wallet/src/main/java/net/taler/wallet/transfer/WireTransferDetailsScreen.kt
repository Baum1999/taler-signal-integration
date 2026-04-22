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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.taler.common.openUri
import net.taler.common.shareText
import net.taler.wallet.R
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.transactions.TransactionDeposit
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionWithdrawal
import net.taler.wallet.transactions.WithdrawalDetails
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails

@Composable
fun WireTransferDetailsScreen(
    model: MainViewModel,
    showQrCodes: Boolean,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val sharePaymentTitle = stringResource(R.string.share_payment)
    val transactionManager = model.transactionManager
    val withdrawManager = model.withdrawManager
    val exchangeManager = model.exchangeManager

    val selectedTx by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)

    LaunchedEffect(selectedTx) {
        if (selectedTx?.txState?.major == Done) {
            onNavigateBack()
        }
    }

    GlobalScaffold(
        model = model,
        title = { Text(stringResource(R.string.wire_transfer)) },
        onNavigateBack = onNavigateBack,
    ) { paddingValues ->
        val transfers = remember(selectedTx) {
            selectedTx?.let { tx ->
                when (tx) {
                    is TransactionWithdrawal -> when (tx.withdrawalDetails) {
                        is WithdrawalDetails.ManualTransfer -> {
                            tx.withdrawalDetails.exchangeCreditAccountDetails
                        }
                        else -> null
                    }
                    is TransactionDeposit -> tx.kycAuthTransferInfo?.let {
                        it.creditPaytoUris.map { paytoUri ->
                            WithdrawalExchangeAccountDetails(
                                paytoUri = paytoUri,
                                status = WithdrawalExchangeAccountDetails.Status.Ok,
                            )
                        }
                    }
                    else -> null
                }?.map {
                    it.getTransferDetails(
                        amountRaw = tx.amountRaw,
                        amountEffective = tx.amountEffective
                    )
                }
            }
        }?.filterNotNull()

        if (transfers != null) ScreenTransfer(
            modifier = Modifier.padding(paddingValues),
            transfers = transfers,
            getQrCodes = { withdrawManager.getQrCodesForPayto(it.withdrawalAccount.paytoUri) },
            spec = selectedTx?.amountRaw?.currency?.let {
                selectedTx?.scopes?.let { selectedScopes ->
                    exchangeManager.getSpecForCurrency(it, selectedScopes)
                } ?: run {
                    exchangeManager.getSpecForCurrency(it)
                }
            },
            bankAppClick = { transfer ->
                context.openUri(
                    uri = transfer.withdrawalAccount.paytoUri,
                    title = sharePaymentTitle
                )
            },
            shareClick = { transfer ->
                context.shareText(
                    text = transfer.withdrawalAccount.paytoUri,
                )
            },
            showQrCodes = showQrCodes,
            devMode = devMode,
            transferContext = when (val tx = selectedTx) {
                is TransactionWithdrawal -> TransferContext.ManualWithdrawal
                is TransactionDeposit -> TransferContext.DepositKycAuth(
                    tx.kycAuthTransferInfo?.debitPaytoUri ?: ""
                )
                else -> return@GlobalScaffold
            }
        )
    }
}
