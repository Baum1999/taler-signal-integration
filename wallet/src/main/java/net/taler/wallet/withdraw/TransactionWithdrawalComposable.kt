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

package net.taler.wallet.withdraw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.RelativeTime
import net.taler.common.Timestamp
import net.taler.common.toAbsoluteTime
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.ErrorTransactionButton
import net.taler.wallet.transactions.TransactionAction
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionMinorState
import net.taler.wallet.transactions.TransactionState
import net.taler.wallet.transactions.TransactionStateComposable
import net.taler.wallet.transactions.TransactionWithdrawal
import net.taler.wallet.transactions.TransitionsComposable
import net.taler.wallet.transactions.WithdrawalDetails.ManualTransfer
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.transfer.PaytoQrCode

@Composable
fun TransactionWithdrawalComposable(
    t: TransactionWithdrawal,
    devMode: Boolean,
    qrCode: QrCodeSpec?,
    spec: CurrencySpecification?,
    onConfirmBank: () -> Unit,
    onConfirmManual: () -> Unit,
    onShowQrCodes: () -> Unit,
    onTransition: (t: TransactionAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val context = LocalContext.current

        TransactionStateComposable(state = t.txState, tx = t)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(context).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (t.amountRaw != t.amountEffective) {
            TransactionAmountComposable(
                label = stringResource(R.string.amount_chosen),
                amount = t.amountRaw.withSpec(spec),
                amountType = AmountType.Neutral,
            )
        }

        if (t.amountRaw > t.amountEffective) {
            val fee = t.amountRaw - t.amountEffective
            TransactionAmountComposable(
                label = stringResource(id = R.string.amount_fee),
                amount = fee.withSpec(spec),
                amountType = AmountType.Negative,
            )
        }

        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_total),
            amount = t.amountEffective.withSpec(spec),
            amountType = AmountType.Positive,
        )

        if (t.txState.minor == TransactionMinorState.BankConfirmTransfer) {
            Button(onClick = onConfirmBank) {
                val label = stringResource(R.string.withdraw_button_confirm_bank)
                Icon(
                    Icons.Default.Link,
                    label,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(label)
            }
        } else if (t.txState.minor == TransactionMinorState.ExchangeWaitReserve) {
            Text(
                text = stringResource(R.string.withdraw_manual_instruction_manual),
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )

            Button(onClick = onConfirmManual) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.withdraw_manual_ready_details_intro))
            }

            if (qrCode != null) {
                Text(
                    text = stringResource(R.string.withdraw_manual_instruction_qr),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )

                PaytoQrCode(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    qrCode = qrCode,
                )
            } else {
                Button(onClick = onShowQrCodes) {
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

        if (t.exchangeBaseUrl != null) {
            TransactionInfoComposable(
                label = stringResource(id = R.string.withdraw_exchange),
                info = cleanExchange(t.exchangeBaseUrl),
                marquee = true,
            )
        }
        
        TransitionsComposable(t, devMode, onTransition)

        if (devMode && t.error != null) {
            ErrorTransactionButton(error = t.error)
        }

        BottomInsetsSpacer()
    }
}

private val previewWithdrawalTx = TransactionWithdrawal(
    transactionId = "transactionId",
    timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
    txState = TransactionState(Pending, TransactionMinorState.ExchangeWaitReserve),
    txActions = listOf(Retry, Suspend, Abort),
    exchangeBaseUrl = "https://exchange.demo.taler.net/",
    withdrawalDetails = ManualTransfer(
        exchangeCreditAccountDetails = listOf(
            WithdrawalExchangeAccountDetails(
                paytoUri = "payto://IBAN/1231231231",
                transferAmount = Amount.fromJSONString("NETZBON:42.23"),
                status = WithdrawalExchangeAccountDetails.Status.Ok,
                currencySpecification = CurrencySpecification(
                    name = "NETZBON",
                    numFractionalInputDigits = 2,
                    numFractionalNormalDigits = 2,
                    numFractionalTrailingZeroDigits = 2,
                    altUnitNames = mapOf(0 to "NETZBON"),
                ),
            ),
        ),
        reserveClosingDelay = RelativeTime.fromMillis(1000),
    ),
    amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
    amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
    error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
    scopes = listOf(ScopeInfo.Exchange(
        currency = "TESTKUDOS",
        url = "exchange.test.taler.net",
    ))
)

@Preview
@Composable
fun TransactionWithdrawalComposableSingleQrPreview() {
    Surface {
        TransactionWithdrawalComposable(previewWithdrawalTx, true,
            QrCodeSpec(QrCodeSpec.Type.SPC, "something"),
            null,
            {}, {}, {}, {})
    }
}

@Preview
@Composable
fun TransactionWithdrawalComposableMultiQrPreview() {
    Surface {
        TransactionWithdrawalComposable(previewWithdrawalTx, true,
            null,
            null,
            {}, {}, {}, {})
    }
}