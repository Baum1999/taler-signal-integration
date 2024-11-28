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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.taler.wallet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountTaler(
    supportedHosts: List<String>,
    name: String,
    host: String,
    account: String,
    onFormEdited: (name: String, host: String, account: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .padding(
                    bottom = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                )
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            value = host,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            onValueChange = {},
            label = {
                Text(
                    stringResource(R.string.send_deposit_host),
                    color = if (host.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else Color.Unspecified,
                )
            },
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            supportedHosts.forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        onFormEdited(name, it, account)
                        expanded = false
                    },
                )
            }
        }
    }

    OutlinedTextField(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        value = name,
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
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
    )

    OutlinedTextField(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        value = account,
        singleLine = true,
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
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
    )
}