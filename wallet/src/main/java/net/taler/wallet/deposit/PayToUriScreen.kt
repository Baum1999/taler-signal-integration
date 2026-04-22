/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
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

package net.taler.wallet.deposit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.main.AmountResult
import net.taler.wallet.main.MainViewModel

@Composable
fun PayToUriScreen(
    model: MainViewModel,
    uri: String,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val u = uri.toUri()
    val receiverName = u.getQueryParameter("receiver-name")?.replace('+', ' ') ?: ""
    val iban = u.pathSegments.lastOrNull() ?: ""

    val currencies = model.balanceManager.getCurrencies()

    TalerSurface {
        GlobalScaffold(
            model = model,
            title = { Text(stringResource(R.string.transactions_send_funds)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            Box(Modifier.padding(paddingValues)) {
                if (currencies.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(id = R.string.payment_balance_insufficient),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (model.depositManager.isSupportedPayToUri(uri)) {
                    PayToComposable(
                        currencies = currencies,
                        getAmount = model::createAmount,
                        onAmountChosen = { amount ->
                            onNavigate(
                                WalletDestination.Deposit(
                                    amount = amount.toJSONString(),
                                    receiverName = receiverName,
                                    IBAN = iban
                                ), true,
                            )
                        },
                        getCurrencySpec = model.exchangeManager::getSpecForCurrency,
                    )
                } else {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(id = R.string.uri_invalid),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PayToComposable(
    currencies: List<String>,
    getAmount: (String, String) -> AmountResult,
    getCurrencySpec: (String) -> CurrencySpecification?,
    onAmountChosen: (Amount) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        var amount by remember { mutableStateOf(Amount.zero(currencies[0])) }
        val currencySpec = remember(amount.currency) { getCurrencySpec(amount.currency) }
        var amountError by rememberSaveable { mutableStateOf("") }

        AmountCurrencyField(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            amount = amount.withSpec(currencySpec),
            currencies = currencies,
            readOnly = false,
            onAmountChanged = { amount = it },
            label = { Text(stringResource(R.string.amount_send)) },
            isError = amountError.isNotBlank(),
            supportingText = {
                if (amountError.isNotBlank()) {
                    Text(amountError)
                }
            }
        )

        val focusManager = LocalFocusManager.current
        val errorStrInvalidAmount = stringResource(id = R.string.amount_invalid)
        val errorStrInsufficientBalance = stringResource(id = R.string.payment_balance_insufficient)
        Button(
            modifier = Modifier.padding(16.dp),
            enabled = !amount.isZero(),
            onClick = {
                when (val amountResult = getAmount(amount.amountStr, amount.currency)) {
                    is AmountResult.Success -> {
                        focusManager.clearFocus()
                        onAmountChosen(amountResult.amount)
                    }
                    is AmountResult.InvalidAmount -> amountError = errorStrInvalidAmount
                    is AmountResult.InsufficientBalance -> amountError = errorStrInsufficientBalance
                }
            },
        ) {
            Text(text = stringResource(R.string.send_deposit_check_fees_button))
        }

        BottomInsetsSpacer()
    }
}
