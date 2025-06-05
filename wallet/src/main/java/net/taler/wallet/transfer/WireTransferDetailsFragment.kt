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

package net.taler.wallet.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
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
import net.taler.wallet.transactions.TransactionDeposit
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionWithdrawal
import net.taler.wallet.transactions.WithdrawalDetails
import net.taler.wallet.transactions.WithdrawalExchangeAccountDetails
import net.taler.wallet.transfer.ScreenTransfer
import net.taler.wallet.withdraw.TransferData

class WireTransferDetailsFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val withdrawManager by lazy { model.withdrawManager }
    private val transactionManager by lazy { model.transactionManager }
    private val exchangeManager by lazy { model.exchangeManager }

    private var navigating: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        val showQrCodes = arguments?.getBoolean("showQrCodes") == true
        setContent {
            TalerSurface {
                val selectedTx by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
                val devMode by model.devMode.observeAsState()

                // TODO: move this code somewhere else
                // TODO: better error handling
                val transfers = remember(selectedTx) {
                    selectedTx?.let { tx ->
                        when (tx) {
                            is TransactionWithdrawal -> when (tx.withdrawalDetails) {
                                is WithdrawalDetails.ManualTransfer -> {
                                    tx.withdrawalDetails.exchangeCreditAccountDetails
                                }

                                else -> null
                            }

                            is TransactionDeposit -> tx.kycAuthTransferInfo?.let {
                                it.creditPaytoUris.map { paytoUri ->
                                    WithdrawalExchangeAccountDetails(
                                        paytoUri = paytoUri,
                                        status = WithdrawalExchangeAccountDetails.Status.Ok,
                                    )
                                }
                            }

                            else -> null
                        }?.map {
                            it.getTransferDetails(
                                amountRaw = tx.amountRaw,
                                amountEffective = tx.amountEffective
                            )
                        }
                    }
                }?.filterNotNull() ?: return@TalerSurface

                ScreenTransfer(
                    transfers = transfers,
                    getQrCodes = { withdrawManager.getQrCodesForPayto(it.withdrawalAccount.paytoUri) },
                    spec = selectedTx?.amountRaw?.currency?.let {
                        selectedTx?.scopes?.let { selectedScopes ->
                            exchangeManager.getSpecForCurrency(it, selectedScopes)
                        } ?: run {
                            exchangeManager.getSpecForCurrency(it)
                        }
                    },
                    bankAppClick = { onBankAppClick(it) },
                    shareClick = { onShareClick(it) },
                    showQrCodes = showQrCodes,
                    devMode = devMode == true,
                    transferContext = when(val tx = selectedTx) {
                        is TransactionWithdrawal -> TransferContext.ManualWithdrawal
                        is TransactionDeposit -> TransferContext.DepositKycAuth(tx.kycAuthTransferInfo?.debitPaytoUri
                            ?: error("no kycAuthTransferInfo"))
                        else -> return@TalerSurface
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.transactionManager.selectedTransaction.collect { tx ->
                    if (tx?.txState?.major == Done) {
                        if (navigating) return@collect
                        findNavController().popBackStack()
                        navigating = true
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
