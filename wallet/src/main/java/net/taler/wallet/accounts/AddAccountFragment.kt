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

package net.taler.wallet.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.deposit.AddAccountComposable
import net.taler.wallet.deposit.GetDepositWireTypesResponse
import net.taler.wallet.showError

class AddAccountFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val accountManager by lazy { model.accountManager }
    private val depositManager by lazy { model.depositManager }
    private var bankAccountId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        bankAccountId = arguments?.getString("bankAccountId")

        val supportActionBar = (requireActivity() as? AppCompatActivity)?.supportActionBar
        if (bankAccountId == null) {
            supportActionBar?.setTitle(R.string.send_deposit_account_add)
        } else {
            supportActionBar?.setTitle(R.string.send_deposit_account_edit)
        }

        setContent {
            TalerSurface {
                var depositWireTypes by remember { mutableStateOf<GetDepositWireTypesResponse?>(null) }
                var bankAccount by remember { mutableStateOf<KnownBankAccountInfo?>(null) }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(bankAccountId) {
                    if (bankAccountId == null) return@LaunchedEffect
                    bankAccount = accountManager.getBankAccountById(bankAccountId!!) { error ->
                        showError(error)
                    }
                }

                LaunchedEffect(Unit) {
                    depositWireTypes = depositManager.getDepositWireTypes()
                }

                if (depositWireTypes == null || (bankAccountId != null && bankAccount == null)) {
                    LoadingScreen()
                } else {
                    AddAccountComposable(
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
                                    showError(it)
                                }
                                // TODO: should we return on error?
                                findNavController().popBackStack()
                            }
                        },
                        onClose = {
                            findNavController().popBackStack()
                        },
                    )
                }
            }
        }
    }
}