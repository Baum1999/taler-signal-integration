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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface

@Composable
fun PayTemplateComposable(
    currencies: List<String>,
    payStatus: PayStatus,
    devMode: Boolean = false,
    getCurrencySpec: (String) -> CurrencySpecification?,
    onSubmit: (params: TemplateParams) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    if (currencies.isEmpty()) {
        ErrorComposable(
            error = TalerErrorInfo.makeCustomError(stringResource(R.string.payment_balance_insufficient)),
            devMode = devMode,
        )
    } else when (val p = payStatus) {
        is PayStatus.Checked -> {
            val usableCurrencies = currencies
                .intersect(p.supportedCurrencies.toSet())
                .toList()
            if (usableCurrencies.isEmpty()) {
                // If user doesn't have any supported currency, they can't pay either
                ErrorComposable(
                    error = TalerErrorInfo.makeCustomError(stringResource(R.string.payment_balance_insufficient)),
                    devMode = devMode,
                )
            } else if (!p.details.isTemplateEditable(usableCurrencies)) {
                // Non-editable: auto-preparing, show loading instead of flashing the form
                PayTemplateLoading()
            } else {
                PayTemplateOrderComposable(
                    usableCurrencies = usableCurrencies,
                    templateDetails = p.details,
                    onSubmit = onSubmit,
                    getCurrencySpec = getCurrencySpec,
                )
            }
        }
        is PayStatus.None, is PayStatus.Loading -> PayTemplateLoading()
        is PayStatus.Error -> ErrorComposable(
            error = p.error,
            message = stringResource(R.string.payment_template_error),
            devMode = devMode,
            onRetry = onRetry,
        )
        is PayStatus.Prepared -> PayTemplateLoading()

        // not emitted by template flow
        is PayStatus.Pending,
        is PayStatus.AlreadyPaid,
        is PayStatus.InsufficientBalance,
        is PayStatus.Success,
        is PayStatus.Choices -> {}
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
            onSubmit = { _ -> },
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
            onSubmit = { _ -> },
            getCurrencySpec = { null },
        )
    }
}
