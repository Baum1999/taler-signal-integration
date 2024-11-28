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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.accounts.BankAccountRow
import net.taler.wallet.accounts.KnownBankAccountInfo
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.PeerErrorComposable

@Composable
fun MakeDepositComposable(
    knownBankAccounts: List<KnownBankAccountInfo>,
    onAccountSelected: (account: KnownBankAccountInfo) -> Unit,
    onManageBankAccounts: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (knownBankAccounts.isEmpty()) item {
            Text(
                modifier = Modifier.padding(
                    vertical = 32.dp,
                    horizontal = 16.dp,
                ),
                text = stringResource(R.string.send_deposit_known_bank_accounts_empty),
                textAlign = TextAlign.Center,
            )
        }

        items(knownBankAccounts, key = { it.bankAccountId }) {
            BankAccountRow(it,
                showMenu = false,
                onClick = { onAccountSelected(it) },
            )
        }

        item {
            Button(
                modifier = Modifier.padding(16.dp),
                onClick = onManageBankAccounts,
            ) {
                Text(stringResource(R.string.send_deposit_account_manage))
            }
        }

        item {
            BottomInsetsSpacer()
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
        )
        ),
        onClose = onClose,
    )
}