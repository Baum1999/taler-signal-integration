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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.RelativeTime
import net.taler.wallet.AmountResult
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.compose.TalerSurface

@Composable
fun PayTemplateOrderComposable(
    usableCurrencies: List<String>, // non-empty intersection between the stored currencies and the ones supported by the merchant
    templateDetails: WalletTemplateDetails,
    onCreateAmount: (String, String) -> AmountResult,
    getCurrencySpec: (String) -> CurrencySpecification?,
    onError: (msgRes: Int) -> Unit,
    onSubmit: (params: TemplateParams) -> Unit,
) {
    val defaultSummary = templateDetails.defaultSummary
    val defaultAmount = templateDetails.defaultAmount
    val defaultCurrency = templateDetails.defaultCurrency

    val summaryFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var summary by remember { mutableStateOf(defaultSummary ?: "") }
    var amount by remember {
        val currency = defaultCurrency ?: usableCurrencies[0]
        mutableStateOf(defaultAmount?.withCurrency(currency) ?: Amount.zero(currency))
    }
    val currencySpec = remember(amount.currency) {
        getCurrencySpec(amount.currency)
    }

    Column(horizontalAlignment = End) {
        OutlinedTextField(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .focusRequester(summaryFocusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                },
            value = summary,
            isError = templateDetails.isSummaryEditable() && summary.isBlank(),
            onValueChange = { summary = it },
            singleLine = true,
            readOnly = !templateDetails.isSummaryEditable(),
            label = { Text(stringResource(R.string.withdraw_manual_ready_subject)) },
        )

        AmountCurrencyField(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            amount = amount.withSpec(currencySpec),
            currencies = usableCurrencies,
            editableCurrency = !templateDetails.isCurrencyEditable(usableCurrencies),
            readOnly = !templateDetails.isAmountEditable(),
            onAmountChanged = { amount = it },
            label = { Text(stringResource(R.string.amount_send)) },
        )

        Button(
            modifier = Modifier.padding(16.dp),
            enabled = !templateDetails.isSummaryEditable() || summary.isNotBlank(),
            onClick = {
                when (val res = onCreateAmount(amount.amountStr, amount.currency)) {
                    is AmountResult.InsufficientBalance -> onError(R.string.payment_balance_insufficient)
                    is AmountResult.InvalidAmount -> onError(R.string.amount_invalid)
                    // NOTE: it is important to nullify non-editable values!
                    is AmountResult.Success -> onSubmit(TemplateParams(
                        summary = if (templateDetails.isSummaryEditable()) summary else null,
                        amount = if(templateDetails.isAmountEditable()) res.amount else null,
                    ))
                }
            },
        ) {
            Text(stringResource(R.string.payment_create_order))
        }

        BottomInsetsSpacer()
    }

    LaunchedEffect(Unit) {
        if (templateDetails.isSummaryEditable()
            && templateDetails.defaultSummary == null) {
            summaryFocusRequester.requestFocus()
        }
    }
}

val defaultTemplateDetails = WalletTemplateDetails(
    templateContract = TemplateContractDetails(
        minimumAge = 18,
        payDuration = RelativeTime.forever(),
    ),
    editableDefaults = TemplateContractDetailsDefaults(
        summary = "Donation",
        amount = Amount.fromJSONString("KUDOS:10.0"),
    ),
)

@Preview
@Composable
fun PayTemplateDefaultPreview() {
    TalerSurface {
        PayTemplateOrderComposable(
            templateDetails = defaultTemplateDetails,
            usableCurrencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { },
            getCurrencySpec = { null },
        )
    }
}

@Preview
@Composable
fun PayTemplateFixedAmountPreview() {
    TalerSurface {
        PayTemplateOrderComposable(
            templateDetails = defaultTemplateDetails,
            usableCurrencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { },
            getCurrencySpec = { null },
        )
    }
}

@Preview
@Composable
fun PayTemplateBlankSubjectPreview() {
    TalerSurface {
        PayTemplateOrderComposable(
            templateDetails = defaultTemplateDetails,
            usableCurrencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { },
            getCurrencySpec = { null },
        )
    }
}
