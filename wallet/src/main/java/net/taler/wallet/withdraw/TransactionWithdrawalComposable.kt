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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.RelativeTime
import net.taler.common.Timestamp
import net.taler.lib.android.toAbsoluteTime
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
import net.taler.wallet.transactions.TransferOption
import net.taler.wallet.transactions.TransitionsComposable
import net.taler.wallet.transactions.WithdrawalDetails.ManualTransfer
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.transactions.WithdrawalTransfers

@Composable
fun TransactionWithdrawalComposable(
    t: TransactionWithdrawal,
    devMode: Boolean,
    spec: CurrencySpecification?,
    onSelectOption: (option: TransferOption?) -> Unit,
    onConfirmKyc: (url: String) -> Unit,
    onConfirmBank: () -> Unit,
    onConfirmManual: (option: TransferOption?) -> Unit,
    onShowQrCodes: () -> Unit,
    onTransition: (t: TransactionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val accounts = (t.withdrawalDetails as? ManualTransfer)
            ?.exchangeCreditAccountDetails
            ?.let { details ->
            details.filter {
                it.status == WithdrawalExchangeAccountDetails.Status.Ok
            }.sortedByDescending {
                it.priority
            }
        }

        val defaultAccountIndex = 0
        var selectedAccountIndex by rememberSaveable {
            mutableIntStateOf(defaultAccountIndex) }
        val selectedAccount = accounts?.getOrNull(selectedAccountIndex)
        val defaultOptionIndex = 0
        var selectedOptionIndex by rememberSaveable (selectedAccountIndex) {
            mutableIntStateOf(defaultOptionIndex) }
        val selectedOption = selectedAccount
            ?.transferOptions[selectedOptionIndex]

        LaunchedEffect(selectedOption) {
            selectedOption?.let { onSelectOption(it) }
        }

        val showAccountChooser = accounts != null && accounts.size > 1
        val showOptionChooser = selectedAccount != null && selectedAccount.transferOptions.size > 1

        if (showAccountChooser) {
            TransferAccountChooser(
                accounts = accounts.map { it },
                selectedIndex = selectedAccountIndex,
                onSelectAccount = { selectedAccountIndex = it },
            )
        }

        if (showOptionChooser) {
            TransferOptionChooser(
                options = selectedAccount.transferOptions,
                selectedIndex = selectedOptionIndex,
                onSelectOption = { selectedOptionIndex = it },
            )
        }

        if (showAccountChooser || showOptionChooser) {
            Spacer(Modifier.height(16.dp))
        }

        TransactionStateComposable(state = t.txState, tx = t)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(LocalContext.current).toString(),
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

        WithdrawalTransfers (
            t,
            selectedOption,
            onConfirmBank = onConfirmBank,
            onConfirmKyc = onConfirmKyc,
            onConfirmManual = { onConfirmManual(selectedOption) },
            onShowQrCodes = onShowQrCodes,
        )

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

@Composable
fun TransferAccountChooser(
    modifier: Modifier = Modifier,
    accounts: List<WithdrawalExchangeAccountDetails>,
    selectedIndex: Int,
    onSelectAccount: (index: Int) -> Unit,
) {
    val selectedIndex = accounts.indexOfFirst {
        it.paytoUri == accounts[selectedIndex].paytoUri
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 8.dp,
    ) {
        accounts.forEachIndexed { index, account ->
            Tab(
                selected = accounts[selectedIndex].paytoUri == account.paytoUri,
                onClick = { onSelectAccount(index) },
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

@Composable
fun TransferOptionChooser(
    modifier: Modifier = Modifier,
    options: List<TransferOption>,
    selectedIndex: Int,
    onSelectOption: (index: Int) -> Unit,
) {
    SecondaryScrollableTabRow (
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 8.dp,
    ) {
        options.forEachIndexed { index, option ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelectOption(index) },
                text = {
                    when(option) {
                        // FIXME: better i18n labels
                        is TransferOption.Payto -> Text("payto://")
                        is TransferOption.SwissQrBill -> Text("Swiss")
                        is TransferOption.Uri -> Text("link")
                    }
                },
            )
        }
    }
}

@Preview
@Composable
fun TransactionWithdrawalComposableSingleQrPreview() {
    Surface {
        TransactionWithdrawalComposable(
            t = previewWithdrawalTx,
            devMode = true,
            spec = null,
            onSelectOption = {},
            onConfirmKyc = {},
            onConfirmBank = {},
            onConfirmManual = {},
            onShowQrCodes = {},
            onTransition = {},
        )
    }
}

@Preview
@Composable
fun TransactionWithdrawalComposableMultiQrPreview() {
    Surface {
        TransactionWithdrawalComposable(
            t = previewWithdrawalTx,
            devMode = true,
            spec = null,
            onSelectOption = {},
            onConfirmKyc = {},
            onConfirmBank = {},
            onConfirmManual = {},
            onShowQrCodes = {},
            onTransition = {},
        )
    }
}