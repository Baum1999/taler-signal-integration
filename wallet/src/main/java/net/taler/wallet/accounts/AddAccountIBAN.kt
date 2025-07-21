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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.taler.wallet.R

@Composable
fun AddAccountIBAN(
    name: String,
    town: String?,
    zip: String?,
    iban: String,
    ibanError: Boolean,
    onFormEdited: (name: String, town: String?, zip: String?, iban: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        modifier = Modifier
            .padding(
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ).fillMaxWidth(),
        value = name,
        onValueChange = { input ->
            onFormEdited(input, town, zip, iban)
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
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
    )

    OutlinedTextField(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        value = iban,
        singleLine = true,
        onValueChange = { input ->
            onFormEdited(name, town, zip, input
                .uppercase()
                .replace(" ", "")
                .replace("\n", "")
                .replace("\t", "")
                .trim())
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
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
    )

    OutlinedTextField(
        modifier = Modifier
            .padding(
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ).fillMaxWidth(),
        value = zip ?: "",
        singleLine = true,
        onValueChange = { input ->
            onFormEdited(name, town, input.trim(), iban)
        },
        isError = ibanError,
        label = {
            Text(stringResource(R.string.send_deposit_postal_code))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
    )

    OutlinedTextField(
        modifier = Modifier
            .padding(
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ).fillMaxWidth(),
        value = town ?: "",
        singleLine = true,
        onValueChange = { input ->
            onFormEdited(name, input.trim(), zip, iban)
        },
        isError = ibanError,
        label = {
            Text(stringResource(R.string.send_deposit_town))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
    )
}