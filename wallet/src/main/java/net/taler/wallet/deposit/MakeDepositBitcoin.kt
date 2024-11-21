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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.wallet.R

@Composable
fun MakeDepositBitcoin(
    bitcoinAddress: String,
    onFormEdited: (bitcoinAddress: String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ).focusRequester(focusRequester),
        value = bitcoinAddress,
        singleLine = true,
        onValueChange = { input ->
            onFormEdited(input)
        },
        isError = bitcoinAddress.isBlank(),
        label = {
            Text(
                stringResource(R.string.send_deposit_bitcoin_address),
                color = if (bitcoinAddress.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else Color.Unspecified,
            )
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

}