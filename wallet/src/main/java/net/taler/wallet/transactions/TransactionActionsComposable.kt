/*
 * This file is part of GNU Taler
 * (C) 2023 Taler Systems S.A.
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

package net.taler.wallet.transactions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.transactions.TransactionMinorState.BankConfirmTransfer
import net.taler.wallet.transactions.TransactionMinorState.ExchangeWaitReserve
import net.taler.wallet.transactions.TransactionMinorState.KycAuthRequired
import net.taler.wallet.transactions.TransactionMinorState.KycRequired
import net.taler.wallet.transfer.PaytoQrCode
import net.taler.wallet.withdraw.QrCodeSpec

@Composable
fun ColumnScope.WithdrawalActions(
    tx: TransactionWithdrawal,
    mainQrCode: QrCodeSpec? = null,
    onConfirmBank: () -> Unit,
    onConfirmKyc: (url: String) -> Unit,
    onConfirmManual: () -> Unit,
    onShowQrCodes: () -> Unit,
) {
    when (tx.txState.minor) {
        BankConfirmTransfer -> {
            ConfirmBankButton(onConfirmBank)
        }

        KycRequired -> if (tx.kycUrl != null) {
            ConfirmKycButton {
                onConfirmKyc(tx.kycUrl)
            }
        }

        ExchangeWaitReserve -> {
            Text(
                text = stringResource(R.string.withdraw_manual_instruction_manual),
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )

            WireTransferStepsButton(onConfirmManual)

            if (mainQrCode != null) {
                Text(
                    text = stringResource(R.string.withdraw_manual_instruction_qr),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )

                PaytoQrCode(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    qrCode = mainQrCode,
                )
            } else {
                ShowQrCodesButton(onShowQrCodes)
            }
        }

        else -> {}
    }
}

@Composable
fun ColumnScope.DepositActions(
    tx: TransactionDeposit,
    onWireTransfer: () -> Unit,
    onShowQrCodes: () -> Unit,
) {
    if (tx.txState.minor == KycAuthRequired) {
        WireTransferStepsButton(onWireTransfer)
        ShowQrCodesButton(onShowQrCodes)
    }
}

@Composable
fun ColumnScope.PeerActions(
    tx: Transaction,
    onConfirmKyc: (url: String) -> Unit,
) {
    val kycUrl = when(tx) {
        is TransactionPeerPushCredit -> tx.kycUrl
        is TransactionPeerPullCredit -> tx.kycUrl
        else -> null
    } ?: return

    if (tx.txState.minor == KycRequired) {
        ConfirmKycButton { onConfirmKyc(kycUrl) }
    }
}

@Composable
private fun ConfirmBankButton(onClick: () -> Unit) {
    Button(onClick) {
        val label = stringResource(R.string.withdraw_button_confirm_bank)
        Icon(
            Icons.Default.Link,
            label,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@Composable
private fun WireTransferStepsButton(onClick: () -> Unit) {
    Button(onClick) {
        Icon(
            Icons.Default.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.withdraw_manual_ready_details_intro))
    }
}

@Composable
private fun ShowQrCodesButton(onClick: () -> Unit) {
    Button(onClick) {
        Icon(
            Icons.Default.QrCode,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.withdraw_manual_ready_details_qr))
    }
}

@Composable
private fun ConfirmKycButton(onClick: () -> Unit) {
    Button(onClick) {
        Icon(
            Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.transaction_action_kyc))
    }
}