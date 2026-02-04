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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.taler.wallet.R
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionMinorState.BalanceKycRequired
import net.taler.wallet.transactions.TransactionMinorState.BankConfirmTransfer
import net.taler.wallet.transactions.TransactionMinorState.ExchangeWaitReserve
import net.taler.wallet.transactions.TransactionMinorState.KycAuthRequired
import net.taler.wallet.transactions.TransactionMinorState.KycRequired
import net.taler.wallet.transactions.TransactionMinorState.MergeKycRequired

interface ActionListener {
    enum class Type {
        COMPLETE_KYC,
        CONFIRM_WITH_BANK,
        CONFIRM_MANUAL,
        SHOW_WIRE_QR,
    }

    fun onActionButtonClicked(tx: Transaction, type: Type)
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    tx: Transaction,
    listener: ActionListener,
) {
    if (tx.txState.major == Pending) {
        when (tx.txState.minor) {
            KycRequired, BalanceKycRequired, MergeKycRequired -> KycButton(modifier, tx, listener)
            BankConfirmTransfer -> ConfirmBankButton(modifier, tx, listener)
            ExchangeWaitReserve, KycAuthRequired -> ConfirmManualButton(modifier, tx, listener)
            else -> {}
        }
    }
}

@Composable
private fun KycButton(
    modifier: Modifier = Modifier,
    tx: Transaction,
    listener: ActionListener,
) {
    Button(
        onClick = { listener.onActionButtonClicked(tx, ActionListener.Type.COMPLETE_KYC) },
        modifier = modifier,
    ) {
        Icon(
            Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.transaction_action_kyc))
    }
}

@Composable
private fun ConfirmBankButton(
    modifier: Modifier = Modifier,
    tx: Transaction,
    listener: ActionListener,
) {
    // TODO: should check go here?
    if (tx is TransactionWithdrawal
        && tx.withdrawalDetails is WithdrawalDetails.TalerBankIntegrationApi
        && tx.withdrawalDetails.bankConfirmationUrl == null) return

    Button(
        onClick = { listener.onActionButtonClicked(tx, ActionListener.Type.CONFIRM_WITH_BANK) },
        modifier = modifier,
    ) {
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
private fun ConfirmManualButton(
    modifier: Modifier = Modifier,
    tx: Transaction,
    listener: ActionListener,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = { listener.onActionButtonClicked(tx, ActionListener.Type.CONFIRM_MANUAL) },
            modifier = modifier,
        ) {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.withdraw_manual_ready_details_intro))
        }

        Button(
            onClick = { listener.onActionButtonClicked(tx, ActionListener.Type.SHOW_WIRE_QR) },
            modifier = modifier,
        ) {
            Icon(
                Icons.Default.QrCode,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.withdraw_manual_ready_details_qr))
        }
    }
}
