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

package net.taler.wallet.deposit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.transactions.AmountType.Negative
import net.taler.wallet.transactions.AmountType.Positive
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.useDebounce

@Composable
fun DepositAmountComposable(
    state: DepositState,
    currency: String,
    currencySpec: CurrencySpecification?,
    checkDeposit: suspend (amount: Amount) -> CheckDepositResult,
    onMakeDeposit: (amount: Amount) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .imePadding(),
        horizontalAlignment = CenterHorizontally,
    ) {
        var checkResult by remember { mutableStateOf<CheckDepositResult>(CheckDepositResult.None()) }
        var amount by remember { mutableStateOf(Amount.zero(currency)) }

        amount.useDebounce {
            if (!amount.isZero()) {
                checkResult = checkDeposit(amount)
            }
        }

        AnimatedVisibility(checkResult.maxDepositAmountEffective != null) {
            checkResult.maxDepositAmountEffective?.let {
                Text(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                    text = stringResource(
                        R.string.amount_available_transfer,
                        it.withSpec(currencySpec),
                    ),
                )
            }
        }

        AmountCurrencyField(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            amount = amount.withSpec(currencySpec),
            onAmountChanged = { amount = it },
            editableCurrency = false,
            currencies = listOf(),
            isError = checkResult !is CheckDepositResult.Success,
            label = { Text(stringResource(R.string.amount_deposit)) },
            supportingText = {
                val res = checkResult
                if (res is CheckDepositResult.InsufficientBalance && res.maxAmountEffective != null) {
                    Text(
                        stringResource(
                            R.string.payment_balance_insufficient_max,
                            res.maxAmountEffective.withSpec(currencySpec),
                        )
                    )
                }
            }
        )

        AnimatedVisibility(visible = checkResult is CheckDepositResult.Success) {
            val res = checkResult as? CheckDepositResult.Success ?: return@AnimatedVisibility

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = CenterHorizontally,
            ) {
                val totalAmount = res.totalDepositCost
                val effectiveAmount = res.effectiveDepositAmount
                if (totalAmount > effectiveAmount) {
                    val fee = totalAmount - effectiveAmount

                    TransactionAmountComposable(
                        label = stringResource(R.string.amount_fee),
                        amount = fee.withSpec(amount.spec),
                        amountType = Negative,
                    )
                }

                TransactionAmountComposable(
                    label = stringResource(R.string.amount_send),
                    amount = effectiveAmount.withSpec(amount.spec),
                    amountType = Positive,
                )
            }
        }

        AnimatedVisibility(visible = state is DepositState.Error) {
            Text(
                modifier = Modifier.padding(16.dp),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error,
                text = (state as? DepositState.Error)?.error?.userFacingMsg ?: "",
            )
        }

        val focusManager = LocalFocusManager.current
        Button(
            modifier = Modifier.padding(16.dp),
            enabled = checkResult is CheckDepositResult.Success,
            onClick = {
                focusManager.clearFocus()
                onMakeDeposit(amount)
            },
        ) {
            Text(stringResource(R.string.send_deposit_create_button))
        }

        BottomInsetsSpacer()
    }
}

@Preview
@Composable
fun DepositAmountComposablePreview() {
    Surface {
        val state = DepositState.AccountSelected("payto://", "KUDOS")
        DepositAmountComposable(
            state = state,
            currency = "KUDOS",
            currencySpec = null,
            checkDeposit = { CheckDepositResult.Success(
                totalDepositCost = Amount.fromJSONString("KUDOS:10"),
                effectiveDepositAmount = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12")
            ) },
            onMakeDeposit = {},
        )
    }
}
