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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.compose.WarningLabel
import net.taler.wallet.withdraw.TransferData

@Composable
fun TransferCyclos(
    transfer: TransferData.Cyclos,
    transactionAmountEffective: Amount,
    // TODO: transferContext?
) {
    Column(
        modifier = Modifier.padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                R.string.withdraw_manual_ready_intro,
                transfer.transferAmount,
                transactionAmountEffective,
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(vertical = 8.dp)
        )

        WarningLabel(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 16.dp,
            ),
            label = stringResource(R.string.send_deposit_kyc_auth_warning_account),
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp)
        )

        TransferStep(1, stringResource(R.string.withdraw_manual_step_subject))

        DetailRow(
            stringResource(R.string.withdraw_manual_ready_subject),
            transfer.subject,
            characterBreak = true,
        )

        WarningLabel(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 16.dp,
            ),
            label = stringResource(R.string.withdraw_manual_ready_warning),
        )

        TransferStep(2, stringResource(R.string.withdraw_manual_step_cyclos))

        DetailRow(
            stringResource(R.string.withdraw_manual_ready_receiver),
            transfer.receiverName,
        )

        WithdrawalAmountTransfer(
            conversionAmountRaw = transfer.transferAmount,
        )

        TransferStep(3,
            stringResource(
                R.string.withdraw_manual_step_finish,
                transfer.transferAmount,
            ),
        )
    }
}