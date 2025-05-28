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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.accounts.BankAccountRow
import net.taler.wallet.accounts.KnownBankAccountInfo
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.systemBarsPaddingBottom
import net.taler.wallet.transactions.AmountType.Negative
import net.taler.wallet.transactions.AmountType.Positive
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.useDebounce

@Composable
fun DepositAmountComposable(
    state: DepositState.AccountSelected,
    getCurrencySpec: (currency: String) -> CurrencySpecification?,
    checkDeposit: suspend (amount: Amount) -> CheckDepositResult,
    onMakeDeposit: (amount: Amount) -> Unit,
    onClose: () -> Unit,
) {
    val availableScopes = remember(state.maxDepositable) {
        state.maxDepositable.filterValues { it?.rawAmount?.isZero() == false }
    }

    if (availableScopes.isEmpty()) {
        MakeDepositErrorComposable(
            message = "It is not possible to deposit to this account, please select another one",
            onClose = onClose,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        var checkResult by remember { mutableStateOf<CheckDepositResult>(CheckDepositResult.None()) }
        // TODO: use scopeInfo instead of currency
        // TODO: handle unavailable scopes in UI (i.e. explain restrictions)
        val currencies = remember(availableScopes) { availableScopes.keys.toList() }
        var amount by remember(state.maxDepositable) { mutableStateOf(Amount.zero(currencies.first())) }
        val spec = remember(amount) { getCurrencySpec(amount.currency) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = CenterHorizontally,
        ) {

            amount.useDebounce {
                if (!amount.isZero()) {
                    checkResult = checkDeposit(amount)
                }
            }

            BankAccountRow(
                account = state.account,
                showMenu = false,
            )

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 16.dp),
            )

            AnimatedVisibility(checkResult.maxDepositAmountRaw != null) {
                checkResult.maxDepositAmountRaw?.let {
                    Text(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        text = if (checkResult.maxDepositAmountEffective == it) {
                            stringResource(
                                R.string.amount_available_transfer,
                                it.withSpec(spec),
                            )
                        } else {
                            stringResource(
                                R.string.amount_available_transfer_fees,
                                it.withSpec(spec),
                            )
                        },
                    )
                }
            }

            AmountCurrencyField(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                amount = amount.withSpec(spec),
                onAmountChanged = { amount = it },
                editableCurrency = true,
                currencies = currencies,
                isError = checkResult !is CheckDepositResult.Success,
                label = { Text(stringResource(R.string.amount_deposit)) },
                supportingText = {
                    val res = checkResult
                    if (res is CheckDepositResult.InsufficientBalance) {
                        Text(stringResource(R.string.payment_balance_insufficient))
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

                        TransactionAmountComposable(
                            label = stringResource(R.string.amount_send),
                            amount = effectiveAmount.withSpec(amount.spec),
                            amountType = Positive,
                        )
                    }
                }
            }

            BottomInsetsSpacer()
        }

        BottomButtonBox(Modifier.fillMaxWidth()) {
            val focusManager = LocalFocusManager.current
            Button(
                modifier = Modifier
                    .systemBarsPaddingBottom(),
                enabled = checkResult is CheckDepositResult.Success,
                onClick = {
                    focusManager.clearFocus()
                    onMakeDeposit(amount)
                },
            ) {
                Text(stringResource(R.string.send_deposit_create_button))
            }
        }
    }
}

@Preview
@Composable
fun DepositAmountComposablePreview() {
    Surface {
        val state = DepositState.AccountSelected(
            KnownBankAccountInfo(
                bankAccountId = "acct:1234",
                paytoUri = "payto://",
                kycCompleted = false,
                currencies = listOf("KUDOS", "TESTKUDOS"),
                label = "Test accoul "
            ),
            maxDepositable = mapOf(
                "CHF" to GetMaxDepositAmountResponse(
                    effectiveAmount = Amount.fromJSONString("CHF:100"),
                    rawAmount = Amount.fromJSONString("CHF:100"),
                ),
                "EUR" to GetMaxDepositAmountResponse(
                    effectiveAmount = Amount.fromJSONString("EUR:0"),
                    rawAmount = Amount.fromJSONString("EUR:0"),
                ),
                "MXN" to GetMaxDepositAmountResponse(
                    effectiveAmount = Amount.fromJSONString("MXN:1000"),
                    rawAmount = Amount.fromJSONString("MXN:1000"),
                ),
                "USD" to GetMaxDepositAmountResponse(
                    effectiveAmount = Amount.fromJSONString("USD:0"),
                    rawAmount = Amount.fromJSONString("USD:0"),
                ),
            ),
        )
        DepositAmountComposable(
            state = state,
            checkDeposit = { CheckDepositResult.Success(
                totalDepositCost = Amount.fromJSONString("KUDOS:10"),
                effectiveDepositAmount = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12")
            ) },
            onMakeDeposit = {},
            getCurrencySpec = { null },
            onClose = {}
        )
    }
}

@Preview
@Composable
fun DepositAmountComposableErrorPreview() {
    Surface {
        val state = DepositState.AccountSelected(
            KnownBankAccountInfo(
                bankAccountId = "acct:1234",
                paytoUri = "payto://",
                kycCompleted = false,
                currencies = listOf("KUDOS", "TESTKUDOS"),
                label = "Test accoul "
            ),
            maxDepositable = mapOf(),
        )
        DepositAmountComposable(
            state = state,
            checkDeposit = { CheckDepositResult.Success(
                totalDepositCost = Amount.fromJSONString("KUDOS:10"),
                effectiveDepositAmount = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12")
            ) },
            onMakeDeposit = {},
            getCurrencySpec = { null },
            onClose = {}
        )
    }
}
