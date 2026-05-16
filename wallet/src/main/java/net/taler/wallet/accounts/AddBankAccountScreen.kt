/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
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

package net.taler.wallet.accounts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.deposit.GetDepositWireTypesResponse
import net.taler.wallet.main.MainViewModel

@Composable
fun AddBankAccountScreen(
    model: MainViewModel,
    bankAccountId: String?,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val accountManager = model.accountManager
    val depositManager = model.depositManager
    val coroutineScope = rememberCoroutineScope()

    var depositWireTypes by remember { mutableStateOf<GetDepositWireTypesResponse?>(null) }
    var bankAccount by remember { mutableStateOf<KnownBankAccountInfo?>(null) }

    LaunchedEffect(bankAccountId) {
        if (bankAccountId != null) {
            bankAccount = accountManager.getBankAccountById(bankAccountId) { error ->
                onShowError(error)
            }
        }
    }

    LaunchedEffect(Unit) {
        depositWireTypes = depositManager.getDepositWireTypes()
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            onNavigateBack = onNavigateBack,
            title = {
                if (bankAccountId != null) {
                    Text(stringResource(R.string.send_deposit_account_edit))
                } else {
                    Text(stringResource(R.string.send_deposit_account_add))
                }
            },
        ) { paddingValues ->
            if (depositWireTypes == null || (bankAccountId != null && bankAccount == null)) {
                LoadingScreen(Modifier.padding(paddingValues))
            } else {
                AddAccountComposable(
                    modifier = Modifier.padding(paddingValues),
                    presetAccount = bankAccount,
                    depositWireTypes = depositWireTypes!!,
                    validateIban = depositManager::validateIban,
                    onSubmit = { paytoUri, label ->
                        coroutineScope.launch {
                            accountManager.addBankAccount(
                                paytoUri = paytoUri,
                                label = label,
                                replaceBankAccountId = bankAccountId,
                            ) {
                                onShowError(it)
                            }
                            onNavigateBack()
                        }
                    },
                    onClose = onNavigateBack,
                )
            }
        }
    }
}
