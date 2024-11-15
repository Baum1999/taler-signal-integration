/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.withdraw.manual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.taler.common.openUri
import net.taler.common.shareText
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.withdraw.TransferData

class ManualWithdrawSuccessFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val withdrawManager by lazy { model.withdrawManager }
    private val transactionManager by lazy { model.transactionManager }
    private val balanceManager by lazy { model.balanceManager }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                val status by withdrawManager.withdrawStatus.collectAsStateLifecycleAware()
                val selectedTx by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
                val qrCodes by withdrawManager.qrCodes.observeAsState()

                BackHandler {
                    selectedTx?.let { navigateToDetails(it) }
                }

                ScreenTransfer(
                    status = status,
                    qrCodes = qrCodes ?: emptyList(),
                    getQrCodes = { withdrawManager.getQrCodesForPayto(it.paytoUri) },
                    spec = status.amountInfo?.amountRaw?.currency?.let { balanceManager.getSpecForCurrency(it) },
                    bankAppClick = { onBankAppClick(it) },
                    shareClick = { onShareClick(it) },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.withdrawManager.withdrawStatus.collect { status ->
                    // Set action bar subtitle and unset on exit
                    if (status.withdrawalTransfers.size > 1) {
                        (requireActivity() as? AppCompatActivity)?.apply {
                            supportActionBar?.subtitle = getString(R.string.withdraw_subtitle)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.transactionManager.selectedTransaction.collect { tx ->
                    if (tx?.txState?.major == Done) {
                        navigateToDetails(tx)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (requireActivity() as? AppCompatActivity)?.apply {
            supportActionBar?.subtitle = null
        }
    }

    private fun navigateToDetails(tx: Transaction) {
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.nav_main, false)
            .build()
        findNavController()
            .navigate(tx.detailPageNav, null, options)
    }

    private fun onBankAppClick(transfer: TransferData) {
        requireContext().openUri(
            uri = transfer.withdrawalAccount.paytoUri,
            title = requireContext().getString(R.string.share_payment)
        )
    }

    private fun onShareClick(transfer: TransferData) {
        requireContext().shareText(
            text = transfer.withdrawalAccount.paytoUri,
        )
    }
}
