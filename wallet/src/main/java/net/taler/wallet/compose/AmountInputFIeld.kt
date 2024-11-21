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

package net.taler.wallet.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.deposit.CurrencyDropdown

@Composable
fun AmountCurrencyField(
    modifier: Modifier = Modifier,
    amount: Amount,
    editableCurrency: Boolean = true,
    currencies: List<String>,
    onAmountChanged: (amount: Amount) -> Unit,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
) {
    Row(modifier = modifier) {
        AmountInputFieldBase(
            modifier = Modifier
                .weight(2f, true)
                .padding(end = 16.dp),
            amount = amount,
            onAmountChanged = onAmountChanged,
            label = label,
            isError = isError,
            supportingText = supportingText,
            readOnly = readOnly,
            enabled = enabled,
        )

        if (editableCurrency) {
            CurrencyDropdown(
                modifier = Modifier.weight(1f),
                currencies = currencies,
                onCurrencyChanged = { onAmountChanged(amount.copy(currency = it)) },
                initialCurrency = amount.currency,
                readOnly = readOnly || !enabled,
            )
        }
    }
}

@Composable
private fun AmountInputFieldBase(
    amount: Amount,
    onAmountChanged: (amount: Amount) -> Unit,
    modifier: Modifier,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
) {
    // TODO: use non-deprecated PlatformTextInputModifierNode instead
    val inputService = LocalTextInputService.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused: Boolean by interactionSource.collectIsFocusedAsState()
    val isClicked: Boolean by interactionSource.collectIsPressedAsState()
    var session by remember { mutableStateOf<TextInputSession?>(null) }

    val currentOnEnterDigit by rememberUpdatedState { digit: Char ->
        amount.addInputDigit(digit)?.let {
            onAmountChanged(it)
            true
        } ?: false
    }

    val currentOnRemoveDigit by rememberUpdatedState {
        amount.removeInputDigit()?.let {
            onAmountChanged(it)
            true
        } ?: false
    }

    LaunchedEffect(isFocused, isClicked) {
        if (readOnly && !enabled) return@LaunchedEffect
        if (isFocused || isClicked) {
            session = startSession(inputService) { commands ->
                commands.forEach { cmd ->
                    when (cmd) {
                        is BackspaceCommand -> currentOnRemoveDigit()
                        is DeleteSurroundingTextCommand -> currentOnRemoveDigit()
                        is CommitTextCommand -> cmd.text.forEach { currentOnEnterDigit(it) }
                    }
                }
            }
        } else if (session != null) {
            session?.let { inputService?.stopInput(it) }
            session = null
        }
    }

    OutlinedTextField(
        value = amount.toString(),
        onValueChange = {},
        modifier = modifier.onKeyEvent {
            if (it.type == KeyEventType.KeyDown) return@onKeyEvent false
            if (it.key == Key.Backspace) {
                currentOnRemoveDigit()
            } else {
                currentOnEnterDigit(it.utf16CodePoint.toChar())
            }
        },
        readOnly = true,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        label = label,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        maxLines = 1,
        interactionSource = interactionSource,
        enabled = enabled,
    )
}

@SuppressLint("RestrictedApi")
@OptIn(InternalTextApi::class)
fun startSession(
    textInputService: TextInputService?,
    onEditCommand: (List<EditCommand>) -> Unit,
): TextInputSession? = textInputService?.let { service ->
    service.startInput(
        TextFieldValue(),
        imeOptions = ImeOptions.Default.copy(
            singleLine = false,
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        onEditCommand = onEditCommand,
        onImeActionPerformed = { action ->
            if (action == ImeAction.Done) {
                service.stopInput()
            }
        }
    )
}