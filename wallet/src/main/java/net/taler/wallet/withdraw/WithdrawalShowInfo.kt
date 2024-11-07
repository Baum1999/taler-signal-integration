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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.systemBarsPaddingBottom
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.useDebounce
import net.taler.wallet.withdraw.WithdrawStatus.Status.TosReviewRequired
import net.taler.wallet.withdraw.WithdrawStatus.Status.Updating

@Composable
fun WithdrawalShowInfo(
    status: WithdrawStatus,
    defaultCurrency: String,
    editableCurrency: Boolean,
    currencies: List<String>,
    spec: CurrencySpecification?,
    onSelectAmount: (amount: Amount) -> Unit,
    onSelectExchange: () -> Unit,
    onTosReview: () -> Unit,
    onConfirm: (age: Int?) -> Unit,
) {
    val defaultAmount = status.amountInfo?.amountRaw
        ?: status.uriInfo?.amount
        ?: Amount.zero(defaultCurrency)
    val maxAmount = status.uriInfo?.maxAmount
    val editableAmount = status.uriInfo?.editableAmount ?: true
    val wireFee = status.uriInfo?.wireFee ?: Amount.zero(defaultCurrency)
    val exchange = status.exchangeBaseUrl
    val possibleExchanges = status.uriInfo?.possibleExchanges ?: emptyList()
    val ageRestrictionOptions = status.amountInfo?.ageRestrictionOptions ?: emptyList()

    var startup by remember { mutableStateOf(true) }
    var selectedAmount by remember { mutableStateOf(defaultAmount) }
    var selectedAge by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val insufficientBalance = remember(selectedAmount, maxAmount) {
        maxAmount == null || selectedAmount > maxAmount
    }

    selectedAmount.useDebounce {
        if (startup) { // do not fire at startup
            startup = false
        } else {
            onSelectAmount(it)
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
            if (editableAmount) {
                AmountCurrencyField(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    amount = selectedAmount.withSpec(spec),
                    currencies = currencies,
                    editableCurrency = editableCurrency,
                    onAmountChanged = { amount ->
                        selectedAmount = if (amount.currency != status.currency) {
                            // if amount changes, reset to zero!
                            Amount.zero(amount.currency)
                        } else {
                            amount
                        }
                    },
                    label = { Text(stringResource(R.string.amount_withdraw)) },
                    isError = selectedAmount.isZero() || maxAmount != null && selectedAmount > maxAmount,
                    supportingText = {
                        if (insufficientBalance && maxAmount != null) {
                            Text(stringResource(R.string.amount_excess, maxAmount))
                        }
                    },
                )
            } else {
                TransactionAmountComposable(
                    label = if (wireFee.isZero()) {
                        stringResource(R.string.amount_total)
                    } else {
                        stringResource(R.string.amount_chosen)
                    },
                    amount = selectedAmount,
                    amountType = if (wireFee.isZero()) {
                        AmountType.Positive
                    } else {
                        AmountType.Neutral
                    },
                )
            }

            if (!wireFee.isZero()) {
                TransactionAmountComposable(
                    label = stringResource(R.string.amount_fee),
                    amount = wireFee,
                    amountType = AmountType.Negative,
                )

                TransactionAmountComposable(
                    label = stringResource(R.string.amount_total),
                    amount = selectedAmount + wireFee,
                    amountType = AmountType.Positive,
                )
            }

            exchange?.let {
                TransactionInfoComposable(
                    label = stringResource(R.string.withdraw_exchange),
                    info = cleanExchange(it),
                    trailing = {
                        if (possibleExchanges.size > 1) {
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

            if (ageRestrictionOptions.isNotEmpty()) {
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
                enabled = !error
                        && status.status != Updating
                        && !selectedAmount.isZero(),
                onClick = {
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