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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.accounts.KnownBankAccountInfo
import net.taler.wallet.accounts.PaytoUri
import net.taler.wallet.accounts.PaytoUriBitcoin
import net.taler.wallet.accounts.PaytoUriIban
import net.taler.wallet.accounts.PaytoUriTalerBank
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.WarningLabel
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.PeerErrorComposable
import net.taler.wallet.useDebounce

@Composable
fun AddAccountComposable(
    presetAccount: KnownBankAccountInfo? = null,
    depositWireTypes: GetDepositWireTypesResponse,
    validateIban: suspend (iban: String) -> Boolean,
    onSubmit: (paytoUri: String, label: String) -> Unit,
    onClose: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val supportedWireTypes = remember(depositWireTypes) { depositWireTypes.wireTypes }
    val talerBankHostnames = remember(depositWireTypes) { depositWireTypes.hostNames }
    val presetPaytoUri = remember(presetAccount) { presetAccount?.let { PaytoUri.parse(it.paytoUri) } }

    if (supportedWireTypes.isEmpty()) {
        return AddAccountErrorComposable(
            message = stringResource(R.string.send_deposit_no_methods_error),
            onClose = onClose,
        )
    }

    var selectedWireType by remember(supportedWireTypes, presetAccount) {
        if (presetAccount == null) {
            return@remember mutableStateOf(supportedWireTypes.firstOrNull())
        }

        val parsed = PaytoUri.parse(presetAccount.paytoUri)
        mutableStateOf(when (parsed) {
            is PaytoUriIban -> WireType.IBAN
            is PaytoUriTalerBank -> WireType.TalerBank
            is PaytoUriBitcoin -> WireType.Bitcoin
            else -> supportedWireTypes.firstOrNull()
        })
    }

    // payto:// stuff
    var formError by rememberSaveable { mutableStateOf(false) } // TODO: do an initial validation!
    var ibanError by rememberSaveable(presetPaytoUri) { mutableStateOf(presetPaytoUri == null) }
    var formAlias by rememberSaveable(presetAccount) { mutableStateOf(presetAccount?.label ?: "") }
    var ibanName by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriIban)?.receiverName ?: "") }
    var ibanTown by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriIban)?.receiverTown) }
    var ibanZip by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriIban)?.receiverPostalCode) }
    var ibanIban by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriIban)?.iban ?: "") }
    var talerName by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriTalerBank)?.receiverName ?: "") }
    var talerHost by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriTalerBank)?.host ?: talerBankHostnames.firstOrNull() ?: "") }
    var talerAccount by rememberSaveable(presetPaytoUri) { mutableStateOf((presetPaytoUri as? PaytoUriTalerBank)?.account ?: "") }
    var bitcoinAddress by rememberSaveable(presetPaytoUri) { mutableStateOf("") } // TODO: fill-in bitcoin address

    val paytoUri = when(selectedWireType) {
        WireType.IBAN -> getIbanPayto(ibanName, ibanZip, ibanTown, ibanIban)
        WireType.TalerBank -> getTalerPayto(talerName, talerHost, talerAccount)
        WireType.Bitcoin -> getBitcoinPayto(bitcoinAddress)
        else -> null
    }

    // form validation
    paytoUri.useDebounce {
        formError = when (selectedWireType) {
            WireType.IBAN -> {
                val valid = validateIban(ibanIban)
                ibanError = !valid || ibanIban.isBlank()
                !valid || ibanName.isBlank()
            }

            WireType.TalerBank -> {
                talerName.isBlank()
                        || talerHost.isBlank()
                        || talerAccount.isBlank()
            }

            WireType.Bitcoin -> {
                bitcoinAddress.isBlank()
            }

            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = CenterHorizontally,
    ) {
        // Do not show chooser when editing account
        if (presetAccount == null
            && selectedWireType != null
            && supportedWireTypes.size > 1) {
            item {
                MakeDepositWireTypeChooser(
                    supportedWireTypes = supportedWireTypes,
                    selectedWireType = selectedWireType!!,
                    onSelectWireType = {
                        selectedWireType = it
                    }
                )
            }
        }

        item {
            WarningLabel(
                modifier = Modifier.padding(16.dp),
                label = stringResource(R.string.send_deposit_account_warning),
            )
        }

        when(selectedWireType) {
            WireType.IBAN -> item {
                AddAccountIBAN(
                    name = ibanName,
                    town = ibanTown,
                    zip = ibanZip,
                    iban = ibanIban,
                    ibanError = ibanError,
                    onFormEdited = { name, town, zip, iban ->
                        ibanName = name
                        ibanTown = town
                        ibanZip = zip
                        ibanIban = iban
                    }
                )
            }

            WireType.TalerBank -> item {
                AddAccountTaler(
                    name = talerName,
                    host = talerHost,
                    account = talerAccount,
                    supportedHosts = talerBankHostnames,
                    onFormEdited = { name, host, account ->
                        talerName = name
                        talerHost = host
                        talerAccount = account
                    }
                )
            }

            WireType.Bitcoin -> item {
                AddAccountBitcoin(
                    bitcoinAddress = bitcoinAddress,
                    onFormEdited = { address ->
                        bitcoinAddress = address
                    }
                )
            }

            else -> {}
        }

        item {
            OutlinedTextField(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                value = formAlias,
                onValueChange = {
                    formAlias = it
                },
                label = {
                    Text(stringResource(R.string.send_deposit_account_note))
                },
                singleLine = true,
                isError = formAlias.isBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }

        item {
            Button(
                modifier = Modifier.padding(16.dp),
                enabled = !formError && formAlias.isNotBlank(),
                onClick = {
                    focusManager.clearFocus()
                    if (paytoUri != null && formAlias.isNotEmpty()) {
                        onSubmit(paytoUri, formAlias)
                    }
                },
            ) {
                Icon(
                    if (presetAccount == null) {
                        Icons.Default.Add
                    } else {
                        Icons.Default.Edit
                    },
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )

                Spacer(Modifier.size(ButtonDefaults.IconSpacing))

                if (presetAccount == null) {
                    Text(stringResource(R.string.send_deposit_account_add))
                } else {
                    Text(stringResource(R.string.send_deposit_account_edit))
                }
            }
        }

        item {
            BottomInsetsSpacer()
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
fun AddAccountErrorComposable(
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
fun PreviewAddAccountComposable() {
    Surface {
        AddAccountComposable(
            depositWireTypes = GetDepositWireTypesResponse(
                wireTypeDetails = listOf(
                    WireTypeDetails(
                        paymentTargetType = WireType.IBAN,
                        talerBankHostnames = listOf("bank.test.taler.net")
                    ),
                    WireTypeDetails(
                        paymentTargetType = WireType.TalerBank,
                        talerBankHostnames = listOf("bank.test.taler.net")
                    ),
                    WireTypeDetails(
                        paymentTargetType = WireType.Bitcoin,
                        talerBankHostnames = emptyList(),
                    )
                ),
            ),
            validateIban = { true },
            onSubmit = { _, _ -> },
            onClose = {},
        )
    }
}
