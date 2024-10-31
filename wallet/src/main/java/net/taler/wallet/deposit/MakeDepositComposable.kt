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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.PeerErrorComposable
import net.taler.wallet.transactions.AmountType.Negative
import net.taler.wallet.transactions.AmountType.Positive
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.useDebounce

@Composable
fun MakeDepositComposable(
    state: DepositState,
    defaultCurrency: String?,
    currencies: List<String>,
    getCurrencySpec: (currency: String) -> CurrencySpecification?,
    checkDeposit: suspend (amount: Amount, paytoUri: String) -> CheckDepositResult,
    getDepositWireTypes: suspend (currency: String) -> GetDepositWireTypesForCurrencyResponse?,
    presetName: String? = null,
    presetIban: String? = null,
    validateIban: suspend (iban: String) -> Boolean,
    onMakeDeposit: (Amount, String) -> Unit,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = CenterHorizontally,
    ) {
        // Amount/currency stuff
        // TODO: use scopeInfo instead of currency!
        var checkResult by remember { mutableStateOf<CheckDepositResult>(CheckDepositResult.None) }
        var amount by remember { mutableStateOf(Amount.zero(defaultCurrency ?: currencies[0])) }

        var depositWireTypes by remember { mutableStateOf<GetDepositWireTypesForCurrencyResponse?>(null) }
        val supportedWireTypes = remember(depositWireTypes) { depositWireTypes?.wireTypes ?: emptyList() }
        val talerBankHostnames = remember(depositWireTypes) { depositWireTypes?.wireTypeDetails?.flatMap { it.talerBankHostnames }?.distinct() ?: emptyList() }
        var selectedWireType by remember { mutableStateOf(supportedWireTypes.firstOrNull()) }

        LaunchedEffect(amount.currency) {
            depositWireTypes = getDepositWireTypes(amount.currency)
        }

        // payto:// stuff
        var formError by rememberSaveable { mutableStateOf(true) } // TODO: do an initial validation!
        var ibanName by rememberSaveable { mutableStateOf(presetName ?: "") }
        var ibanIban by rememberSaveable { mutableStateOf(presetIban ?: "") }
        var talerName by rememberSaveable { mutableStateOf(presetName ?: "") }
        var talerHost by rememberSaveable { mutableStateOf(talerBankHostnames.firstOrNull() ?: "") }
        var talerAccount by rememberSaveable { mutableStateOf("") }

        val paytoUri = when(selectedWireType) {
            WireType.IBAN -> getIbanPayto(ibanName, ibanIban)
            WireType.TalerBank -> getTalerPayto(talerName, talerHost, talerAccount)
            else -> null
        }

        // reset forms and selected wire type when switching currency
        DisposableEffect(supportedWireTypes, amount.currency) {
            selectedWireType = supportedWireTypes.firstOrNull()
            formError = true
            ibanName = presetName ?: ""
            ibanIban = presetIban ?: ""
            talerName = presetName ?: ""
            talerHost = talerBankHostnames.firstOrNull() ?: ""
            talerAccount = ""
            onDispose {  }
        }

        amount.useDebounce {
            if (paytoUri != null) {
                checkResult = checkDeposit(amount, paytoUri)
            }
        }

        paytoUri.useDebounce {
            if (paytoUri != null) {
                checkResult = checkDeposit(amount, paytoUri)
            }
        }

        LaunchedEffect(Unit) {
            if (paytoUri != null) {
                checkResult = checkDeposit(amount, paytoUri)
            }
        }

        if (supportedWireTypes.isEmpty()) {
            return@Column MakeDepositErrorComposable(
                message = stringResource(R.string.send_deposit_no_methods_error),
                onClose = onClose,
            )
        }

        if (selectedWireType != null && supportedWireTypes.size > 1) {
            MakeDepositWireTypeChooser(
                supportedWireTypes = supportedWireTypes,
                selectedWireType = selectedWireType!!,
                onSelectWireType = {
                    selectedWireType = it
                }
            )
        }

        AmountCurrencyField(
            modifier = Modifier
                .padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ).fillMaxWidth(),
            initialAmount = amount,
            initialCurrency = defaultCurrency,
            onAmountChanged = { amount = it },
            editableCurrency = true,
            currencies = currencies,
            getCurrencySpec = getCurrencySpec,
            isError = checkResult !is CheckDepositResult.Success,
            label = { Text(stringResource(R.string.amount_deposit)) },
            supportingText = {
                val res = checkResult
                if (res is CheckDepositResult.InsufficientBalance && res.maxAmountEffective != null) {
                    Text(stringResource(R.string.payment_balance_insufficient_max, res.maxAmountEffective))
                }
            }
        )

        when(selectedWireType) {
            WireType.IBAN -> {
                var ibanError by rememberSaveable { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                MakeDepositIBAN(
                    name = ibanName,
                    iban = ibanIban,
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

            WireType.TalerBank -> MakeDepositTaler(
                name = talerName,
                host = talerHost,
                account = talerAccount,
                supportedHosts = talerBankHostnames,
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
            enabled = checkResult is CheckDepositResult.Success && !formError,
            onClick = {
                focusManager.clearFocus()
                if (paytoUri != null) {
                    onMakeDeposit(amount, paytoUri)
                }
            },
        ) {
            Text(stringResource(R.string.send_deposit_create_button))
        }

        BottomInsetsSpacer()
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
fun MakeDepositErrorComposable(
    message: String,
    onClose: () -> Unit,
) {
    PeerErrorComposable(
        state = OutgoingError(info = TalerErrorInfo(
            message = message,
            code = TalerErrorCode.UNKNOWN,
        )),
        onClose = onClose,
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
            defaultCurrency = "KUDOS",
            currencies = listOf("KUDOS", "TESTKUDOS", "NETZBON"),
            getCurrencySpec = { null },
            getDepositWireTypes = { GetDepositWireTypesForCurrencyResponse(listOf(), listOf())},
            checkDeposit = { _, _ -> CheckDepositResult.Success(
                totalDepositCost = Amount.fromJSONString("KUDOS:10"),
                effectiveDepositAmount = Amount.fromJSONString("KUDOS:12"),
            ) },
            validateIban = { true },
            onMakeDeposit = { _, _ -> },
            onClose = {},
        )
    }
}
