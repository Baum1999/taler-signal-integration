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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.CURRENCY_BTC
import net.taler.wallet.R
import net.taler.common.canAppHandleUri
import net.taler.common.copyToClipBoard
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.compose.ShareButton
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails.Status.Ok
import net.taler.wallet.withdraw.QrCodeSpec
import net.taler.wallet.withdraw.QrCodeSpec.Type.EpcQr
import net.taler.wallet.withdraw.QrCodeSpec.Type.SPC
import net.taler.wallet.withdraw.TransferData

sealed class TransferContext {
    data object ManualWithdrawal : TransferContext()
    data class DepositKycAuth(val debitPaytoUri: String) : TransferContext()
}

@Composable
fun ScreenTransfer(
    transfers: List<TransferData>,
    spec: CurrencySpecification?,
    showQrCodes: Boolean,
    getQrCodes: (account: TransferData) -> List<QrCodeSpec>,
    bankAppClick: ((transfer: TransferData) -> Unit)?,
    shareClick: ((transfer: TransferData) -> Unit)?,
    devMode: Boolean = false,
    transferContext: TransferContext,
) {
    // TODO: show some placeholder
    if (transfers.isEmpty()) return

    val transfers = transfers.filter {
        // TODO: in dev mode, show debug info when status is `Error'
        it.withdrawalAccount.status == Ok
    }.sortedByDescending {
        it.withdrawalAccount.priority
    }

    val defaultTransfer = transfers[0]
    var selectedTransfer by remember { mutableStateOf(defaultTransfer) }
    val qrCodes = remember(selectedTransfer) { getQrCodes(selectedTransfer) }
    val qrExpandedStates = remember(qrCodes) {
        val map = mutableStateMapOf<QrCodeSpec, Boolean>()
        qrCodes.forEach {
            map[it] = false
        }
        map
    }

    LaunchedEffect(Unit) {
        getQrCodes(defaultTransfer)
    }

    Column {
        if (transfers.size > 1) {
            TransferAccountChooser(
                accounts = transfers.map { it.withdrawalAccount },
                selectedAccount = selectedTransfer.withdrawalAccount,
                onSelectAccount = { account ->
                    transfers.find {
                        it.withdrawalAccount.paytoUri == account.paytoUri
                    }?.let { selectedTransfer = it }
                }
            )
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showQrCodes) {
                Text(
                    text = stringResource(R.string.withdraw_manual_qr_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(
                            vertical = 8.dp,
                            horizontal = 16.dp,
                        )
                )

                qrCodes.forEach { spec ->
                    PaytoQrCard(
                        expanded = qrExpandedStates[spec]!!,
                        setExpanded = { expanded ->
                            if (expanded) { // un-expand all others
                                qrExpandedStates.forEach { (k, _) ->
                                    qrExpandedStates[k] = false
                                }
                            }
                            // expand only toggled one
                            qrExpandedStates[spec] = expanded
                        },
                        qrCode = spec,
                    )
                }

                BottomInsetsSpacer()
                return
            }

            when (val transfer = selectedTransfer) {
                is TransferData.Taler -> TransferTaler(
                    transfer = transfer,
                    transactionAmountEffective = transfer.amountEffective.withSpec(spec),
                    transferContext = transferContext,
                )

                is TransferData.IBAN -> TransferIBAN(
                    transfer = transfer,
                    transactionAmountEffective = transfer.amountEffective.withSpec(spec),
                    transferContext = transferContext,
                )

                is TransferData.Bitcoin -> TransferBitcoin(
                    transfer = transfer,
                )
            }

            Spacer(Modifier.height(24.dp))

            if (devMode) {
                val paytoUri = selectedTransfer.withdrawalAccount.paytoUri
                if (bankAppClick != null && LocalContext.current.canAppHandleUri(paytoUri)) {
                    Button(
                        onClick = { bankAppClick(selectedTransfer) },
                        modifier = Modifier
                            .padding(bottom = 16.dp),
                    ) {
                        Text(text = stringResource(R.string.withdraw_manual_ready_bank_button))
                    }
                }

                if (shareClick != null) {
                    ShareButton(
                        content = selectedTransfer.withdrawalAccount.paytoUri,
                        modifier = Modifier
                            .padding(bottom = 16.dp),
                    )
                }
            }

            BottomInsetsSpacer()
        }
    }
}

@Composable
fun TransferStep(
    index: Int,
    description: String,
) {
    Text(
        modifier = Modifier.padding(
            top = 16.dp,
            start = 6.dp,
            end = 6.dp,
            bottom = 6.dp,
        ),
        text = AnnotatedString.fromHtml(
            stringResource(
                R.string.withdraw_manual_step,
                index,
                description,
            )
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun DetailRow(
    label: String,
    content: String,
    copy: Boolean = true,
    characterBreak: Boolean = false,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.padding(top = 16.dp, start = 6.dp, end = 6.dp),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.padding(
                    top = 8.dp,
                    start = 6.dp,
                    end = 6.dp,
                ).weight(1f),
                text = content,
                style = if (characterBreak) {
                    MaterialTheme.typography.bodyLarge.copy(
                        lineBreak = LineBreak.Heading,
                    )
                } else MaterialTheme.typography.bodyLarge,
                fontFamily = if (copy) FontFamily.Monospace else FontFamily.Default,
                textAlign = TextAlign.Center,
            )

            if (copy) {
                TextButton(
                    onClick = { copyToClipBoard(context, label, content) },
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.copy))
                }
            }
        }
    }
}

@Composable
fun WithdrawalAmountTransfer(
    conversionAmountRaw: Amount,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransactionAmountComposable(
            label = stringResource(R.string.amount_transfer),
            amount = conversionAmountRaw,
            amountType = AmountType.Neutral,
            context = LocalContext.current,
            copy = true,
        )
    }
}

@Composable
fun TransferAccountChooser(
    modifier: Modifier = Modifier,
    accounts: List<WithdrawalExchangeAccountDetails>,
    selectedAccount: WithdrawalExchangeAccountDetails,
    onSelectAccount: (account: WithdrawalExchangeAccountDetails) -> Unit,
) {
    val selectedIndex = accounts.indexOfFirst {
        it.paytoUri == selectedAccount.paytoUri
    }

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 8.dp,
    ) {
        accounts.forEachIndexed { index, account ->
            Tab(
                selected = selectedAccount.paytoUri == account.paytoUri,
                onClick = { onSelectAccount(account) },
                text = {
                    if (!account.bankLabel.isNullOrEmpty()) {
                        Text(account.bankLabel)
                    } else if (account.currencySpecification?.name != null) {
                        Text(stringResource(
                            R.string.withdraw_account_currency,
                            index + 1,
                            account.currencySpecification.name,
                        ))
                    } else if (account.transferAmount?.currency != null) {
                        Text(stringResource(
                            R.string.withdraw_account_currency,
                            index + 1,
                            account.transferAmount.currency,
                        ))
                    } else Text(stringResource(R.string.withdraw_account, index + 1))
                },
            )
        }
    }
}

@Preview
@Composable
fun ScreenTransferPreview(
    showQrCodes: Boolean = false,
    transferContext: TransferContext = TransferContext.ManualWithdrawal,
) {
    Surface {
        ScreenTransfer(
            transfers = listOf(
                TransferData.IBAN(
                    iban = "ASDQWEASDZXCASDQWE",
                    subject = "Taler Withdrawal P2T19EXRBY4B145JRNZ8CQTD7TCS03JE9VZRCEVKVWCP930P56WG",
                    amountRaw = Amount("KUDOS", 10, 0),
                    amountEffective = Amount("KUDOS", 9, 5),
                    transferAmount = Amount("KUDOS", 10, 0),
                    receiverTown = "Biel/Bienne",
                    receiverPostalCode = "2500",
                    withdrawalAccount = WithdrawalExchangeAccountDetails(
                        paytoUri = "https://taler.net/kudos",
                        transferAmount = Amount("KUDOS", 10, 0),
                        status = Ok,
                        currencySpecification = CurrencySpecification(
                            "KUDOS",
                            numFractionalInputDigits = 2,
                            numFractionalNormalDigits = 2,
                            numFractionalTrailingZeroDigits = 2,
                            altUnitNames = emptyMap(),
                        ),
                    ),
                ),
                TransferData.Bitcoin(
                    account = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    segwitAddresses = listOf(
                        "bc1qqleages8702xvg9qcyu02yclst24xurdrynvxq",
                        "bc1qsleagehks96u7jmqrzcf0fw80ea5g57qm3m84c"
                    ),
                    subject = "0ZSX8SH0M30KHX8K3Y1DAMVGDQV82XEF9DG1HC4QMQ3QWYT4AF00",
                    amountRaw = Amount(CURRENCY_BTC, 0, 14000000),
                    amountEffective = Amount(CURRENCY_BTC, 0, 14000000),
                    transferAmount = Amount("KUDOS", 10, 0),
                    withdrawalAccount = WithdrawalExchangeAccountDetails(
                        paytoUri = "https://taler.net/btc",
                        transferAmount = Amount("BTC", 0, 14000000),
                        status = Ok,
                        currencySpecification = CurrencySpecification(
                            "Bitcoin",
                            numFractionalInputDigits = 2,
                            numFractionalNormalDigits = 2,
                            numFractionalTrailingZeroDigits = 2,
                            altUnitNames = emptyMap(),
                        ),
                    ),
                ),
            ),
            spec = null,
            bankAppClick = {},
            shareClick = {},
            showQrCodes = showQrCodes,
            getQrCodes = {
                listOf(
                    QrCodeSpec(EpcQr, "BCD\\n002\\n1\\nSCT\\n\\n\\nGENODEM1GLS/DE54430609674049078800\\n\\n\\nTaler MJ15S835A5ENQZGJX161TS7FND6Q5DSABS8FCHB8ECF9NT1J8GH0"),
                    QrCodeSpec(SPC, "BCD\\n002\\n1\\nSCT\\n\\n\\nGENODEM1GLS/DE54430609674049078800\\n\\n\\nTaler MJ15S835A5ENQZGJX161TS7FND6Q5DSABS8FCHB8ECF9NT1J8GH0")
                )
            },
            transferContext = transferContext,
        )
    }
}

@Preview
@Composable
fun ScreenTransferKycAuthPreview() {
    ScreenTransferPreview(transferContext = TransferContext.DepositKycAuth("CH120912"))
}

@Preview
@Composable
fun ScreenTransferQRPreview() {
    ScreenTransferPreview(true)
}