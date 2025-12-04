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

package net.taler.wallet.withdraw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.AmountScope
import net.taler.wallet.compose.AmountScopeField
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.WarningLabel
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.ExchangeTosStatus
import net.taler.wallet.systemBarsPaddingBottom
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.useDebounce
import net.taler.wallet.withdraw.WithdrawStatus.Status.Error
import net.taler.wallet.withdraw.WithdrawStatus.Status.TosReviewRequired
import net.taler.wallet.withdraw.WithdrawStatus.Status.Updating
import net.taler.wallet.withdraw.WithdrawalOperationStatusFlag.Pending

@Composable
fun WithdrawalShowInfo(
    status: WithdrawStatus,
    devMode: Boolean,
    initialAmountScope: AmountScope,
    editableScope: Boolean,
    scopes: List<ScopeInfo>,
    onSelectAmount: (amount: Amount, scope: ScopeInfo) -> Unit,
    onSelectExchange: () -> Unit,
    onTosReview: () -> Unit,
    onConfirm: (age: Int?) -> Unit,
) {
    val maxAmount = status.uriInfo?.maxAmount
    val editableAmount = status.uriInfo?.editableAmount ?: true
    val wireFee = status.uriInfo?.wireFee
    val exchange = status.exchangeBaseUrl
    val possibleExchanges = status.uriInfo?.possibleExchanges ?: emptyList()
    val ageRestrictionOptions = status.amountInfo?.ageRestrictionOptions ?: emptyList()

    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedAmount by remember(initialAmountScope) { mutableStateOf(initialAmountScope) }
    var selectedAge by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()
    val insufficientBalance = remember(selectedAmount, maxAmount) {
        val amount = selectedAmount.amount
        if (maxAmount != null && amount.currency == maxAmount.currency) {
            amount > maxAmount
        } else {
            false
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedAmount) {
        val selected = selectedAmount
        if (!selected.debounce && selected.userInput) {
            onSelectAmount(
                selected.amount,
                selected.scope,
            )
        }
    }

    selectedAmount.useDebounce {
        val selected = selectedAmount
        if (selected.debounce && selected.userInput) {
            onSelectAmount(
                selected.amount,
                selected.scope,
            )
        }
    }

    Column(
        Modifier
        .fillMaxSize()
        .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (editableScope) AmountScopeField(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                amount = selectedAmount.copy(
                    amount = selectedAmount.amount.withSpec(status.selectedSpec)
                ),
                scopes = scopes,
                showAmount = false,
                showScope = true,
                showShortcuts = false,
                readOnly = status.status == Updating,
                onAmountChanged = { amount ->
                    selectedAmount = amount.copy(
                        amount = Amount.zero(amount.amount.currency),
                        debounce = false,
                        userInput = true,
                    )
                },
            )

            if (status.status == WithdrawStatus.Status.Loading
                || status.status == WithdrawStatus.Status.None) {
                LoadingScreen(Modifier.weight(1f))
                return
            } else if (status.status == Error && status.error != null) {
                ErrorComposable(status.error,
                    modifier = Modifier.fillMaxSize(),
                    devMode = devMode)
                return
            } else if (status.isCashAcceptor) {
                WarningLabel(
                    label = stringResource(R.string.withdraw_cash_acceptor),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                )
            } else if (editableAmount) {
                AmountScopeField(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    amount = selectedAmount.copy(
                        amount = selectedAmount.amount.withSpec(status.selectedSpec)),
                    scopes = scopes,
                    showScope = false,
                    showAmount = status.status != TosReviewRequired,
                    readOnly = status.status == Updating,
                    onAmountChanged = { amount ->
                        selectedAmount = amount.copy(
                            debounce = true,
                            userInput = true,
                        )
                    },
                    label = { Text(stringResource(R.string.amount_withdraw)) },
                    isError = selectedAmount.amount.isZero() || insufficientBalance,
                    supportingText = {
                        if (insufficientBalance && maxAmount != null) {
                            Text(stringResource(R.string.amount_excess, maxAmount))
                        }
                    },
                    showShortcuts = true,
                    onShortcutSelected = { amount ->
                        selectedAmount = amount.copy(
                            debounce = false,
                            userInput = true,
                        )
                    }
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                if (status.status == TosReviewRequired) Text(
                    modifier = Modifier.padding(22.dp),
                    text = stringResource(R.string.withdraw_review_terms),
                )
            } else if (status.amountInfo != null) {
                TransactionAmountComposable(
                    label = if (wireFee != null && wireFee.isZero()) {
                        stringResource(R.string.amount_total)
                    } else {
                        stringResource(R.string.amount_chosen)
                    },
                    amount = status.amountInfo.amountRaw.withSpec(status.selectedSpec),
                    amountType = if (wireFee != null && wireFee.isZero()) {
                        AmountType.Positive
                    } else {
                        AmountType.Neutral
                    },
                )
            }

            if (status.status != TosReviewRequired
                && status.amountInfo != null
                && wireFee != null
                && !wireFee.isZero()) {
                TransactionAmountComposable(
                    label = stringResource(R.string.amount_fee),
                    amount = wireFee.withSpec(status.selectedSpec),
                    amountType = AmountType.Negative,
                )

                TransactionAmountComposable(
                    label = stringResource(R.string.amount_total),
                    amount = status.amountInfo.amountEffective.withSpec(status.selectedSpec) + wireFee,
                    amountType = AmountType.Positive,
                )
            }

            exchange?.let {
                TransactionInfoComposable(
                    label = stringResource(R.string.withdraw_exchange),
                    info = cleanExchange(it),
                    trailing = {
                        if (devMode && possibleExchanges.size > 1) {
                            IconButton(
                                modifier = Modifier.padding(start = 8.dp),
                                onClick = { onSelectExchange() },
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                )
                            }
                        }
                    },
                )
            }

            var expanded by remember { mutableStateOf(false) }

            if (status.status != TosReviewRequired && ageRestrictionOptions.isNotEmpty()) {
                TransactionInfoComposable(
                    label = stringResource(R.string.withdraw_restrict_age),
                    info = selectedAge?.toString()
                        ?: stringResource(R.string.withdraw_restrict_age_unrestricted)
                ) {
                    IconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.edit),
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.withdraw_restrict_age_unrestricted)) },
                            onClick = {
                                selectedAge = null
                                expanded = false
                            },
                        )

                        ageRestrictionOptions.forEach { age ->
                            DropdownMenuItem(
                                text = { Text(age.toString()) },
                                onClick = {
                                    selectedAge = age
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        BottomButtonBox(Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier
                    .systemBarsPaddingBottom(),
                enabled = status.status != Updating
                        && (status.isCashAcceptor
                        || status.status == TosReviewRequired
                        || !selectedAmount.amount.isZero()),
                onClick = {
                    keyboardController?.hide()
                    if (status.status == TosReviewRequired) {
                        onTosReview()
                    } else onConfirm(selectedAge)
                },
            ) {
                when (status.status) {
                    Updating -> CircularProgressIndicator(modifier = Modifier.size(15.dp))
                    TosReviewRequired -> Text(stringResource(R.string.withdraw_button_tos))
                    else -> Text(stringResource(R.string.withdraw_button_confirm))
                }
            }
        }
    }
}

private fun buildPreviewWithdrawStatus(
    status: WithdrawStatus.Status,
) = WithdrawStatus(
    status = status,
    talerWithdrawUri = "taler://",
    exchangeBaseUrl = "exchange.head.taler.net",
    transactionId = "tx:343434",
    error = TalerErrorInfo(TalerErrorCode.WALLET_EXCHANGE_UNAVAILABLE),
    uriInfo = WithdrawalDetailsForUri(
        amount = null,
        currency = "KUDOS",
        editableAmount = true,
        status = Pending,
        maxAmount = Amount.fromJSONString("KUDOS:10"),
        wireFee = Amount.fromJSONString("KUDOS:0.2"),
        defaultExchangeBaseUrl = "exchange.head.taler.net",
        possibleExchanges = listOf(
            ExchangeItem(
                exchangeBaseUrl = "exchange.demo.taler.net",
                currency = "KUDOS",
                paytoUris = emptyList(),
                scopeInfo = null,
                tosStatus = ExchangeTosStatus.Accepted,
            ),
            ExchangeItem(
                exchangeBaseUrl = "exchange.head.taler.net",
                currency = "KUDOS",
                paytoUris = emptyList(),
                scopeInfo = null,
                tosStatus = ExchangeTosStatus.Accepted,
            ),
        ),
    ),
    amountInfo = WithdrawalDetailsForAmount(
        amountRaw = Amount.fromJSONString("KUDOS:10.1"),
        amountEffective = Amount.fromJSONString("KUDOS:10.2"),
        withdrawalAccountsList = emptyList(),
        ageRestrictionOptions = listOf(18, 23),
        scopeInfo = ScopeInfo.Exchange(
            currency = "KUDOS",
            url = "exchange.head.taler.net",
        ),
    )
)

@Preview
@Composable
fun WithdrawalShowInfoUpdatingPreview() {
    TalerSurface {
        WithdrawalShowInfo(
            status = buildPreviewWithdrawStatus(Updating),
            devMode = true,
            initialAmountScope = AmountScope(
                amount = Amount.fromJSONString("KUDOS:10.10"),
                scope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            ),
            editableScope = true,
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            onSelectExchange = {},
            onSelectAmount = { _, _ -> },
            onTosReview = {},
            onConfirm = {},
        )
    }
}

@Preview
@Composable
fun WithdrawalShowInfoTosReviewPreview() {
    TalerSurface {
        WithdrawalShowInfo(
            status = buildPreviewWithdrawStatus(TosReviewRequired),
            devMode = true,
            initialAmountScope = AmountScope(
                amount = Amount.fromJSONString("KUDOS:10.10"),
                scope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            ),
            editableScope = true,
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            onSelectExchange = {},
            onSelectAmount = { _, _ -> },
            onTosReview = {},
            onConfirm = {},
        )
    }
}

@Preview
@Composable
fun WithdrawalShowInfoErrorPreview() {
    TalerSurface {
        WithdrawalShowInfo(
            status = buildPreviewWithdrawStatus(Error),
            devMode = true,
            initialAmountScope = AmountScope(
                amount = Amount.fromJSONString("KUDOS:10.10"),
                scope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            ),
            editableScope = true,
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            onSelectExchange = {},
            onSelectAmount = { _, _ -> },
            onTosReview = {},
            onConfirm = {},
        )
    }
}