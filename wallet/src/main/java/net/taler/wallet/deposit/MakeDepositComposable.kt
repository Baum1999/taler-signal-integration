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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.WarningLabel
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.PeerErrorComposable

@Composable
fun MakeDepositComposable(
    defaultCurrency: String?,
    currencies: List<String>,
    getDepositWireTypes: suspend (currency: String) -> GetDepositWireTypesForCurrencyResponse?,
    presetName: String? = null,
    presetIban: String? = null,
    validateIban: suspend (iban: String) -> Boolean,
    onPaytoSelected: (payto: String, currency: String) -> Unit,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .imePadding(),
        horizontalAlignment = CenterHorizontally,
    ) {
        // TODO: use scopeInfo instead of currency
        var currency by remember { mutableStateOf(defaultCurrency ?: currencies[0]) }
        var depositWireTypes by remember { mutableStateOf<GetDepositWireTypesForCurrencyResponse?>(null) }
        val supportedWireTypes = remember(depositWireTypes) { depositWireTypes?.wireTypes ?: emptyList() }
        val talerBankHostnames = remember(depositWireTypes) { depositWireTypes?.wireTypeDetails?.flatMap { it.talerBankHostnames }?.distinct() ?: emptyList() }
        var selectedWireType by remember { mutableStateOf(supportedWireTypes.firstOrNull()) }

        LaunchedEffect(currency) {
            depositWireTypes = getDepositWireTypes(currency)
        }

        // payto:// stuff
        var formError by rememberSaveable { mutableStateOf(true) } // TODO: do an initial validation!
        var ibanName by rememberSaveable { mutableStateOf(presetName ?: "") }
        var ibanIban by rememberSaveable { mutableStateOf(presetIban ?: "") }
        var talerName by rememberSaveable { mutableStateOf(presetName ?: "") }
        var talerHost by rememberSaveable { mutableStateOf(talerBankHostnames.firstOrNull() ?: "") }
        var talerAccount by rememberSaveable { mutableStateOf("") }
        var bitcoinAddress by rememberSaveable { mutableStateOf("") }

        val paytoUri = when(selectedWireType) {
            WireType.IBAN -> getIbanPayto(ibanName, ibanIban)
            WireType.TalerBank -> getTalerPayto(talerName, talerHost, talerAccount)
            WireType.Bitcoin -> getBitcoinPayto(bitcoinAddress)
            else -> null
        }

        // reset forms and selected wire type when switching currency
        DisposableEffect(supportedWireTypes, currency) {
            selectedWireType = supportedWireTypes.firstOrNull()
            formError = true
            ibanName = presetName ?: ""
            ibanIban = presetIban ?: ""
            talerName = presetName ?: ""
            talerHost = talerBankHostnames.firstOrNull() ?: ""
            talerAccount = ""
            bitcoinAddress = ""
            onDispose {  }
        }

        if (supportedWireTypes.isEmpty()) {
            return@Column MakeDepositErrorComposable(
                message = stringResource(R.string.send_deposit_no_methods_error),
                onClose = onClose,
            )
        }

        CurrencyDropdown(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            currencies = currencies,
            onCurrencyChanged = { currency = it },
            initialCurrency = defaultCurrency,
        )

        if (selectedWireType != null && supportedWireTypes.size > 1) {
            MakeDepositWireTypeChooser(
                supportedWireTypes = supportedWireTypes,
                selectedWireType = selectedWireType!!,
                onSelectWireType = {
                    selectedWireType = it
                }
            )
        }

        WarningLabel(
            modifier = Modifier.padding(16.dp),
            label = stringResource(R.string.send_deposit_account_warning),
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

            WireType.Bitcoin -> MakeDepositBitcoin(
                bitcoinAddress = bitcoinAddress,
                onFormEdited = { address ->
                    bitcoinAddress = address
                    formError = address.isBlank()
                }
            )

            else -> {}
        }

        val focusManager = LocalFocusManager.current
        Button(
            modifier = Modifier.padding(16.dp),
            enabled = !formError,
            onClick = {
                focusManager.clearFocus()
                if (paytoUri != null) {
                    onPaytoSelected(paytoUri, currency)
                }
            },
        ) {
            Text(stringResource(R.string.withdraw_select_amount))
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
                            WireType.Bitcoin -> stringResource(R.string.send_deposit_bitcoin)
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
        MakeDepositComposable(
            defaultCurrency = "KUDOS",
            currencies = listOf("KUDOS", "TESTKUDOS", "NETZBON"),
            getDepositWireTypes = { GetDepositWireTypesForCurrencyResponse(
                wireTypes = listOf(
                    WireType.IBAN,
                    WireType.TalerBank,
                    WireType.Bitcoin,
                ),
                wireTypeDetails = listOf(
                    WireTypeDetails(
                        paymentTargetType = WireType.IBAN,
                        talerBankHostnames = listOf("bank.test.taler.net")
                    ),
                    WireTypeDetails(
                        paymentTargetType = WireType.TalerBank,
                        talerBankHostnames = listOf("bank.test.taler.net")
                    ),
                ),
            )},
            validateIban = { true },
            onPaytoSelected = { _, _ -> },
            onClose = {},
        )
    }
}
