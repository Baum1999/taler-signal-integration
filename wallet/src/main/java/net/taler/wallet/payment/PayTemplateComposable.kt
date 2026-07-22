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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.main.AmountResult
import net.taler.wallet.R
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.systemBarsPaddingBottom

@Composable
fun PayTemplateComposable(
    currencies: List<String>,
    payStatus: PayStatus,
    getCurrencySpec: (String) -> CurrencySpecification?,
    onCreateAmount: (String, String) -> AmountResult,
    onSubmit: (params: TemplateParams) -> Unit,
    onError: (msg: String) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    if (currencies.isEmpty()) {
        PayTemplateError(stringResource(R.string.payment_balance_insufficient))
    } else when (val p = payStatus) {
        is PayStatus.Checked -> {
            val usableCurrencies = currencies
                .intersect(p.supportedCurrencies.toSet())
                .toList()
            if (usableCurrencies.isEmpty()) {
                // If user doesn't have any supported currency, they can't pay either
                PayTemplateError(stringResource(R.string.payment_balance_insufficient))
            } else {
                PayTemplateOrderComposable(
                    usableCurrencies = usableCurrencies,
                    templateDetails = p.details,
                    onCreateAmount = onCreateAmount,
                    onError = onError,
                    onSubmit = onSubmit,
                    getCurrencySpec = getCurrencySpec,
                )
            }
        }
        is PayStatus.None, is PayStatus.Loading -> PayTemplateLoading()
        is PayStatus.Pending -> PayTemplateError(
            message = if (p.error != null) {
                stringResource(R.string.payment_error, p.error.userFacingMsg)
            } else {
                stringResource(R.string.payment_template_error)
            },
            onRetry = onRetry,
        )
        is PayStatus.Prepared -> PayTemplateLoading()

        // not emitted by template flow
        is PayStatus.AlreadyPaid,
        is PayStatus.InsufficientBalance,
        is PayStatus.Success,
        is PayStatus.Choices -> {}
    }
}

@Composable
fun PayTemplateError(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .systemBarsPaddingBottom(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        if (onRetry != null) {
            Button(
                modifier = Modifier.padding(top = 24.dp),
                onClick = onRetry,
            ) {
                Text(stringResource(R.string.transactions_retry))
            }
        }
    }
}

@Composable
fun PayTemplateLoading() {
    LoadingScreen()
}

@Preview
@Composable
fun PayTemplateLoadingPreview() {
    TalerSurface {
        PayTemplateComposable(
            payStatus = PayStatus.Loading,
            currencies = listOf("KUDOS", "ARS"),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { _ -> },
            getCurrencySpec = { null },
        )
    }
}

@Preview
@Composable
fun PayTemplateNoCurrenciesPreview() {
    TalerSurface {
        PayTemplateComposable(
            payStatus = PayStatus.None,
            currencies = emptyList(),
            onCreateAmount = { text, currency ->
                AmountResult.Success(amount = Amount.fromString(currency, text))
            },
            onSubmit = { _ -> },
            onError = { _ -> },
            getCurrencySpec = { null },
        )
    }
}
