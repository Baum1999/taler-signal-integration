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
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.AmountScope
import net.taler.wallet.compose.AmountScopeField
import net.taler.wallet.compose.BottomButtonBox
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
    defaultScope: ScopeInfo,
    editableScope: Boolean,
    scopes: List<ScopeInfo>,
    spec: CurrencySpecification?,
    onSelectAmount: (amount: Amount, scope: ScopeInfo) -> Unit,
    onSelectExchange: () -> Unit,
    onTosReview: () -> Unit,
    onConfirm: (age: Int?) -> Unit,
) {
    val defaultAmount = status.amountInfo?.amountRaw
        ?: status.uriInfo?.amount
        ?: Amount.zero(defaultScope.currency)
    val maxAmount = status.uriInfo?.maxAmount
    val editableAmount = status.uriInfo?.editableAmount ?: true
    val wireFee = status.uriInfo?.wireFee ?: Amount.zero(defaultScope.currency)
    val exchange = status.exchangeBaseUrl
    val possibleExchanges = status.uriInfo?.possibleExchanges ?: emptyList()
    val ageRestrictionOptions = status.amountInfo?.ageRestrictionOptions ?: emptyList()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedAmount by remember { mutableStateOf(AmountScope(defaultAmount, defaultScope)) }
    var selectedAge by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()
    val insufficientBalance = remember(selectedAmount, maxAmount) {
        maxAmount == null || selectedAmount.amount > maxAmount
    }

    var startup by remember { mutableStateOf(true) }
    selectedAmount.useDebounce {
        if (startup) { // do not fire at startup
            startup = false
        } else {
            onSelectAmount(
                selectedAmount.amount,
                selectedAmount.scope,
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
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                amount = selectedAmount.copy(
                    amount = selectedAmount.amount.withSpec(spec)
                ),
                scopes = scopes,
                editableScope = true,
                enabledAmount = false,
                showShortcuts = false,
                onAmountChanged = { amount ->
                    selectedAmount = amount
                },
            )

            if (status.status == Error && status.error != null) {
                WithdrawalError(status.error)
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
                        amount = selectedAmount.amount.withSpec(spec)),
                    scopes = scopes,
                    editableScope = false,
                    enabledAmount = status.status != TosReviewRequired,
                    onAmountChanged = { amount ->
                        selectedAmount = if (amount.scope != status.scopeInfo) {
                            // if amount changes, reset to zero!
                            amount.copy(amount = Amount.zero(amount.scope.currency))
                        } else {
                            amount
                        }
                    },
                    label = { Text(stringResource(R.string.amount_withdraw)) },
                    isError = selectedAmount.amount.isZero()
                            || maxAmount != null
                            && selectedAmount.amount > maxAmount,
                    supportingText = {
                        if (insufficientBalance && maxAmount != null) {
                            Text(stringResource(R.string.amount_excess, maxAmount))
                        }
                    },
                    showShortcuts = true,
                    onShortcutSelected = { amount ->
                        selectedAmount = amount
                    }
                )

                if (status.status == TosReviewRequired) Text(
                    modifier = Modifier.padding(22.dp),
                    text = stringResource(R.string.withdraw_review_terms),
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                TransactionAmountComposable(
                    label = if (wireFee.isZero()) {
                        stringResource(R.string.amount_total)
                    } else {
                        stringResource(R.string.amount_chosen)
                    },
                    amount = selectedAmount.amount,
                    amountType = if (wireFee.isZero()) {
                        AmountType.Positive
                    } else {
                        AmountType.Neutral
                    },
                )
            }

            if (status.status != TosReviewRequired && !wireFee.isZero()) {
                TransactionAmountComposable(
                    label = stringResource(R.string.amount_fee),
                    amount = wireFee,
                    amountType = AmountType.Negative,
                )

                TransactionAmountComposable(
                    label = stringResource(R.string.amount_total),
                    amount = selectedAmount.amount + wireFee,
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
    currency = "KUDOS",
    exchangeBaseUrl = "exchange.head.taler.net",
    transactionId = "tx:343434",
    error = null,
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
        tosAccepted = true,
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
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            editableScope = true,
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            spec = null,
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
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            editableScope = true,
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            spec = null,
            onSelectExchange = {},
            onSelectAmount = { _, _ -> },
            onTosReview = {},
            onConfirm = {},
        )
    }
}