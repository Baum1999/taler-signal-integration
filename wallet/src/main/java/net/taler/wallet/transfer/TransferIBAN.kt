/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import net.taler.common.TalerUtils
import net.taler.wallet.R
import net.taler.wallet.accounts.PaytoUri
import net.taler.wallet.accounts.PaytoUriIban
import net.taler.wallet.compose.WarningLabel
import net.taler.wallet.transactions.AccountRestriction
import net.taler.wallet.transfer.TransferContext.DepositKycAuth
import net.taler.wallet.transfer.TransferContext.ManualWithdrawal
import net.taler.wallet.withdraw.TransferData

@Composable
fun TransferIBAN(
    transfer: TransferData.IBAN,
    transactionAmountEffective: Amount,
    transferContext: TransferContext,
) {
    Column(
        modifier = Modifier.padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when(transferContext) {
                ManualWithdrawal -> stringResource(
                    R.string.withdraw_manual_ready_intro,
                    transfer.transferAmount,
                    transactionAmountEffective,
                )

                is DepositKycAuth -> {
                    val paytoIban = PaytoUri.parse(transferContext.debitPaytoUri)
                    if (paytoIban !is PaytoUriIban) return@Column // TODO: render error
                    stringResource(
                        R.string.send_deposit_kyc_auth_intro_bank,
                        transfer.transferAmount,
                        paytoIban.iban,
                    )
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(vertical = 8.dp)
        )

        Spacer(Modifier.padding(4.dp))

        transfer.withdrawalAccount.creditRestrictions?.forEach { restriction ->
            when (restriction) {
                is AccountRestriction.RegexAccount -> {
                    WarningLabel (
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        label = TalerUtils.getLocalizedString(
                            restriction.humanHintI18n,
                            restriction.humanHint,
                        )
                    )
                }

                else -> {}
            }
        }

        if (transferContext is DepositKycAuth) {
            WarningLabel(
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 16.dp,
                ),
                label = stringResource(R.string.send_deposit_kyc_auth_warning_account),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp)
        )

        TransferStep(1, stringResource(R.string.withdraw_manual_step_iban))

        DetailRow(stringResource(R.string.withdraw_manual_ready_iban), transfer.iban)

        transfer.receiverName?.let {
            DetailRow(stringResource(R.string.withdraw_manual_ready_receiver), it)
        }

        transfer.receiverPostalCode?.let {
            DetailRow(stringResource(R.string.withdraw_manual_ready_postal_code), it)
        }

        transfer.receiverTown?.let {
            DetailRow(stringResource(R.string.withdraw_manual_ready_town), it)
        }

        WithdrawalAmountTransfer(
            conversionAmountRaw = transfer.transferAmount,
        )

        TransferStep(2, stringResource(R.string.withdraw_manual_step_subject))

        WarningLabel(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 16.dp,
            ),
            label = when (transferContext) {
                ManualWithdrawal -> stringResource(R.string.withdraw_manual_ready_warning)
                is DepositKycAuth -> stringResource(R.string.send_deposit_kyc_auth_warning_subject)
            },
        )

        DetailRow(
            stringResource(transfer.transferOption.subjectLabelResId()),
            transfer.subject,
            characterBreak = true,
        )

        TransferStep(3,
            when (transferContext) {
                ManualWithdrawal -> stringResource(
                    R.string.withdraw_manual_step_finish,
                    transfer.transferAmount,
                )

                is DepositKycAuth -> stringResource(
                    R.string.send_deposit_kyc_auth_step_finish,
                    transfer.transferAmount,
                    transfer.iban,
                )
            }
        )
    }
}