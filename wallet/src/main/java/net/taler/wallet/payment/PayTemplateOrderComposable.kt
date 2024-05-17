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

package net.taler.wallet.payment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.RelativeTime
import net.taler.wallet.AmountResult
import net.taler.wallet.R
import net.taler.wallet.compose.AmountInputField
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.deposit.CurrencyDropdown

@Composable
fun PayTemplateOrderComposable(
    currencies: List<String>, // assumed to have size > 0
    templateDetails: WalletTemplateDetails,
    onCreateAmount: (String, String) -> AmountResult,
    onError: (msgRes: Int) -> Unit,
    onSubmit: (params: TemplateParams) -> Unit,
) {
    val defaultSummary = templateDetails.editableDefaults?.summary
        ?: templateDetails.templateContract.summary
    // TODO: also handle “plain currency string”
    val defaultAmount = templateDetails.editableDefaults?.amount?.let {
        Amount.fromJSONString(it).amountStr
    } ?: templateDetails.templateContract.amount?.amountStr
    // TODO: also take into account `requiredCurrency'
    val defaultCurrency = templateDetails.editableDefaults?.currency
        ?: templateDetails.templateContract.currency

    var summary by remember { mutableStateOf(defaultSummary) }
    var currency by remember { mutableStateOf(defaultCurrency ?: currencies[0]) }
    var amount by remember { mutableStateOf(defaultAmount ?: "0") }

    Column(horizontalAlignment = End) {
        if (defaultSummary != null) OutlinedTextField(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            value = summary ?: "",
            isError = summary.isNullOrBlank(),
            onValueChange = { summary = it },
            singleLine = true,
            readOnly = templateDetails.editableDefaults?.summary == null,
            label = { Text(stringResource(R.string.withdraw_manual_ready_subject)) },
        )

        if (defaultAmount != null || defaultCurrency != null) AmountField(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            amount = amount,
            currency = currency,
            currencies = currencies,
            readOnlyCurrency = templateDetails.editableDefaults?.currency == null,
            readOnlyAmount = templateDetails.editableDefaults?.amount == null,
            onAmountChosen = { a, c ->
                amount = a
                currency = c
            },
        )

        Button(
            modifier = Modifier.padding(16.dp),
            enabled = templateDetails.editableDefaults?.summary == null || !summary.isNullOrBlank(),
            onClick = {
                when (val res = onCreateAmount(amount, currency)) {
                    is AmountResult.InsufficientBalance -> onError(R.string.payment_balance_insufficient)
                    is AmountResult.InvalidAmount -> onError(R.string.amount_invalid)
                    is AmountResult.Success -> onSubmit(TemplateParams(
                        summary = summary,
                        amount = res.amount,
                    ))
                }
            },
        ) {
            Text(stringResource(R.string.payment_create_order))
        }
    }
}

@Composable
private fun AmountField(
    modifier: Modifier = Modifier,
    currencies: List<String>,
    amount: String,
    currency: String,
    readOnlyAmount: Boolean = false,
    readOnlyCurrency: Boolean = false,
    onAmountChosen: (amount: String, currency: String) -> Unit,
) {
    Row(
        modifier = modifier,
    ) {
        AmountInputField(
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(1f),
            value = amount,
            onValueChange = { onAmountChosen(it, currency) },
            label = { Text(stringResource(R.string.amount_send)) },
            readOnly = readOnlyAmount,
        )

        CurrencyDropdown(
            modifier = Modifier.weight(1f),
            initialCurrency = currency,
            currencies = currencies,
            onCurrencyChanged = { onAmountChosen(amount, it) },
            readOnly = readOnlyCurrency,
        )
    }
}

val defaultTemplateDetails = WalletTemplateDetails(
    templateContract = TemplateContractDetails(
        minimumAge = 18,
        payDuration = RelativeTime.forever(),
    ),
    editableDefaults = TemplateContractDetailsDefaults(
        summary = "Donation",
        amount = "KUDOS:10.0",
    ),
)

@Preview
@Composable
fun PayTemplateDefaultPreview() {
    TalerSurface {
        PayTemplateOrderComposable(
            templateDetails = defaultTemplateDetails,
            currencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { },
        )
    }
}

@Preview
@Composable
fun PayTemplateFixedAmountPreview() {
    TalerSurface {
        PayTemplateOrderComposable(
            templateDetails = defaultTemplateDetails,
            currencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { },
        )
    }
}

@Preview
@Composable
fun PayTemplateBlankSubjectPreview() {
    TalerSurface {
        PayTemplateOrderComposable(
            templateDetails = defaultTemplateDetails,
            currencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { },
        )
    }
}
