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

package net.taler.wallet.deposit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.transactions.AmountType.Negative
import net.taler.wallet.transactions.AmountType.Positive
import net.taler.wallet.transactions.TransactionAmountComposable

@Composable
fun MakeDepositComposable(
    state: DepositState,
    supportedWireTypes: List<WireType>,
    amount: Amount,
    presetName: String? = null,
    presetIban: String? = null,
    validateIban: suspend (iban: String) -> Boolean,
    onMakeIbanDeposit: (Amount, String, String) -> Unit,
    onMakeTalerBankDeposit: (Amount, String, String, String) -> Unit,
) {
    // TODO: show some placeholder
    if (supportedWireTypes.isEmpty()) return

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = CenterHorizontally,
    ) {
        var selectedWireType by remember {
            mutableStateOf(supportedWireTypes.first())
        }

        if (supportedWireTypes.size > 1) {
            MakeDepositWireTypeChooser(
                supportedWireTypes = supportedWireTypes,
                selectedWireType = selectedWireType,
                onSelectWireType = {
                    selectedWireType = it
                }
            )
        }

        var formError by rememberSaveable { mutableStateOf(false) }
        var ibanName by rememberSaveable { mutableStateOf(presetName ?: "") }
        var ibanIban by rememberSaveable { mutableStateOf(presetIban ?: "") }
        var talerName by rememberSaveable { mutableStateOf(presetName ?: "") }
        var talerHost by rememberSaveable { mutableStateOf("") }
        var talerAccount by rememberSaveable { mutableStateOf("") }

        when(selectedWireType) {
            WireType.IBAN -> {
                var ibanError by rememberSaveable { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                MakeDepositIbanForm(
                    name = ibanName,
                    iban = ibanIban,
                    state = state,
                    ibanError = ibanError,
                    onFormEdited = { name, iban ->
                        ibanName = name
                        ibanIban = iban
                        coroutineScope.launch {
                            val valid = validateIban(iban)
                            formError = !valid || name.isBlank()
                            ibanError = !valid
                        }
                    }
                )
            }

            WireType.TalerBank -> MakeDepositTalerBankForm(
                name = talerName,
                host = talerHost,
                account = talerAccount,
                state = state,
                onFormEdited = { name, host, account ->
                    talerName = name
                    talerHost = host
                    talerAccount = account
                    formError = name.isBlank()
                            || host.isBlank()
                            || account.isBlank()
                }
            )

            else -> {}
        }

        TransactionAmountComposable(
            label = stringResource(R.string.amount_chosen),
            amount = amount,
            amountType = Positive,
        )
        AnimatedVisibility(visible = state.showFees) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = CenterHorizontally,
            ) {
                val totalAmount = state.totalDepositCost ?: amount
                val effectiveAmount = state.effectiveDepositAmount ?: Amount.zero(amount.currency)
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
            enabled = !formError,
            onClick = {
                focusManager.clearFocus()
                when (selectedWireType) {
                    WireType.IBAN -> onMakeIbanDeposit(amount, ibanName, ibanIban)
                    WireType.TalerBank -> onMakeTalerBankDeposit(amount, talerName, talerHost, talerAccount)
                    else -> {}
                }
            },
        ) {
            Text(
                text = stringResource(
                    if (state is DepositState.FeesChecked) R.string.send_deposit_create_button
                    else R.string.send_deposit_check_fees_button
                )
            )
        }
    }
}

@Composable
fun MakeDepositWireTypeChooser(
    modifier: Modifier = Modifier,
    supportedWireTypes: List<WireType>,
    selectedWireType: WireType,
    onSelectWireType: (wireType: WireType) -> Unit,
) {
    val selectedIndex = supportedWireTypes.indexOfFirst {
        it == selectedWireType
    }

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 8.dp,
    ) {
        supportedWireTypes.forEach { wireType ->
            if (wireType != WireType.Unknown) {
                Tab(
                    selected = selectedWireType == wireType,
                    onClick = { onSelectWireType(wireType) },
                    text = {
                        Text(when(wireType) {
                            WireType.IBAN -> stringResource(R.string.send_deposit_iban)
                            WireType.TalerBank -> stringResource(R.string.send_deposit_taler)
                            else -> error("unknown method")
                        })
                    }
                )
            }
        }
    }
}

@Composable
fun MakeDepositIbanForm(
    state: DepositState,
    name: String,
    iban: String,
    ibanError: Boolean,
    onFormEdited: (name: String, iban: String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        modifier = Modifier
            .padding(16.dp)
            .focusRequester(focusRequester)
            .fillMaxWidth(),
        value = name,
        enabled = !state.showFees,
        onValueChange = { input ->
            onFormEdited(input, iban)
        },
        singleLine = true,
        isError = name.isBlank(),
        label = {
            Text(
                stringResource(R.string.send_deposit_name),
                color = if (name.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else Color.Unspecified,
            )
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        value = iban,
        singleLine = true,
        enabled = !state.showFees,
        onValueChange = { input ->
            onFormEdited(name, input.uppercase())

        },
        isError = ibanError,
        supportingText = {
            if (ibanError) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.send_deposit_iban_error),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        label = {
            Text(
                text = stringResource(R.string.send_deposit_iban),
                color = if (ibanError) {
                    MaterialTheme.colorScheme.error
                } else Color.Unspecified,
            )
        }
    )
}

@Composable
fun MakeDepositTalerBankForm(
    state: DepositState,
    name: String,
    host: String,
    account: String,
    onFormEdited: (name: String, host: String, account: String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        modifier = Modifier
            .padding(16.dp)
            .focusRequester(focusRequester)
            .fillMaxWidth(),
        value = name,
        enabled = !state.showFees,
        onValueChange = { input ->
            onFormEdited(input, host, account)
        },
        singleLine = true,
        isError = name.isBlank(),
        label = {
            Text(
                stringResource(R.string.send_deposit_name),
                color = if (name.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else Color.Unspecified,
            )
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        value = host,
        enabled = !state.showFees,
        onValueChange = { input ->
            onFormEdited(name, input, account)
        },
        singleLine = true,
        isError = host.isBlank(),
        label = {
            Text(
                stringResource(R.string.send_deposit_host),
                color = if (host.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else Color.Unspecified,
            )
        }
    )

    OutlinedTextField(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        value = account,
        singleLine = true,
        enabled = !state.showFees,
        onValueChange = { input ->
            onFormEdited(name, host, input)
        },
        isError = account.isBlank(),
        label = {
            Text(
                text = stringResource(R.string.send_deposit_account),
                color = if (account.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else Color.Unspecified,
            )
        }
    )
}

@Preview
@Composable
fun PreviewMakeDepositComposable() {
    Surface {
        val state = DepositState.FeesChecked(
            effectiveDepositAmount = Amount.fromString("TESTKUDOS", "42.00"),
            totalDepositCost = Amount.fromString("TESTKUDOS", "42.23"),
        )
        MakeDepositComposable(
            state = state,
            supportedWireTypes = listOf(WireType.TalerBank, WireType.IBAN),
            amount = Amount.fromString("TESTKUDOS", "42.23"),
            validateIban = { true },
            onMakeIbanDeposit = { _, _, _ -> },
            onMakeTalerBankDeposit = { _, _, _, _ -> },
        )
    }
}
