/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
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

package net.taler.wallet.transactions

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import net.taler.common.showError
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.TAG
import net.taler.wallet.launchInAppBrowser
import net.taler.wallet.showError
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Delete
import net.taler.wallet.transactions.TransactionAction.Fail
import net.taler.wallet.transactions.TransactionAction.Resume
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.WithdrawalDetails.ManualTransfer
import net.taler.wallet.transactions.WithdrawalDetails.TalerBankIntegrationApi

abstract class TransactionDetailFragment : Fragment(), ActionListener {

    private val model: MainViewModel by activityViewModels()
    protected val transactionManager by lazy { model.transactionManager }
    protected val balanceManager by lazy { model.balanceManager }
    protected val exchangeManager by lazy { model.exchangeManager }
    protected val withdrawManager by lazy { model.withdrawManager }
    protected val devMode get() = model.devMode.value == true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                transactionManager.selectedTransaction.collect {
                    requireActivity().apply {
                        it?.generalTitleRes?.let {
                            title = getString(it)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transactionManager.selectTransaction(null)
    }

    private fun dialogTitle(t: TransactionAction): Int = when (t) {
        Delete -> R.string.transactions_delete_dialog_title
        Abort -> R.string.transactions_abort_dialog_title
        Fail -> R.string.transactions_fail_dialog_title
        else -> error("unsupported action: $t")
    }

    private fun dialogMessage(t: TransactionAction): Int = when (t) {
        Delete -> R.string.transactions_delete_dialog_message
        Abort -> R.string.transactions_abort_dialog_message
        Fail -> R.string.transactions_fail_dialog_message
        else -> error("unsupported action: $t")
    }

    private fun dialogButton(t: TransactionAction): Int = when (t) {
        Delete -> R.string.transactions_delete
        Abort -> R.string.transactions_abort
        Fail -> R.string.transactions_fail
        else -> error("unsupported")
    }

    protected fun onTransitionButtonClicked(t: Transaction, ta: TransactionAction) = when (ta) {
        Delete -> showDialog(ta) { deleteTransaction(t) }
        Abort -> showDialog(ta) { abortTransaction(t) }
        Fail -> showDialog(ta) { failTransaction(t) }
        Retry -> retryTransaction(t)
        Suspend -> suspendTransaction(t)
        Resume -> resumeTransaction(t)
    }

    override fun onActionButtonClicked(tx: Transaction, type: ActionListener.Type) {
        when (type) {
            ActionListener.Type.COMPLETE_KYC -> {
                when (tx) {
                    is TransactionWithdrawal -> tx.kycUrl
                    is TransactionDeposit -> tx.kycUrl
                    is TransactionPeerPullCredit -> tx.kycUrl
                    is TransactionPeerPushCredit -> tx.kycUrl
                    else -> null
                }?.let { kycUrl ->
                    launchInAppBrowser(requireContext(), kycUrl)
                }
            }

            ActionListener.Type.CONFIRM_WITH_BANK -> {
                if (tx !is TransactionWithdrawal) return
                if (tx.withdrawalDetails !is TalerBankIntegrationApi) return
                tx.withdrawalDetails.bankConfirmationUrl?.let { url ->
                    launchInAppBrowser(requireContext(), url)
                }
            }

            ActionListener.Type.CONFIRM_MANUAL,
            ActionListener.Type.SHOW_WIRE_QR -> lifecycleScope.launch {
                when (tx) {
                    is TransactionWithdrawal -> {
                        if (tx.withdrawalDetails !is ManualTransfer) return@launch
                        if (tx.withdrawalDetails.exchangeCreditAccountDetails.isNullOrEmpty()) return@launch
                        findNavController().navigate(
                            R.id.nav_wire_transfer_details,
                            bundleOf("showQrCodes" to (type == ActionListener.Type.SHOW_WIRE_QR))
                        )

                    }

                    is TransactionDeposit -> {
                        if (tx.kycAuthTransferInfo == null) return@launch
                        findNavController().navigate(
                            R.id.nav_wire_transfer_details,
                            bundleOf("showQrCodes" to (type == ActionListener.Type.SHOW_WIRE_QR))
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    private fun showDialog(tt: TransactionAction, onAction: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Material3)
            .setTitle(dialogTitle(tt))
            .setMessage(dialogMessage(tt))
            .setNeutralButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .setNegativeButton(dialogButton(tt)) { dialog, _ ->
                onAction()
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteTransaction(t: Transaction) {
        transactionManager.deleteTransaction(t.transactionId) {
            Log.e(TAG, "Error deleteTransaction $it")
            if (model.devMode.value == true) {
                showError(it)
            } else {
                showError(it.userFacingMsg)
            }
        }
        findNavController().popBackStack()
    }

    private fun retryTransaction(t: Transaction) {
        transactionManager.retryTransaction(t.transactionId) {
            Log.e(TAG, "Error retryTransaction $it")
            if (model.devMode.value == true) {
                showError(it)
            } else {
                showError(it.userFacingMsg)
            }
        }
    }

    private fun abortTransaction(t: Transaction) {
        transactionManager.abortTransaction(
            t.transactionId,
            onSuccess = {},
            onError = {
                Log.e(TAG, "Error abortTransaction $it")
                if (model.devMode.value == true) {
                    showError(it)
                } else {
                    showError(it.userFacingMsg)
                }
            }
        )
    }

    private fun failTransaction(t: Transaction) {
        transactionManager.failTransaction(t.transactionId) {
            Log.e(TAG, "Error failTransaction $it")
            if (model.devMode.value == true) {
                showError(it)
            } else {
                showError(it.userFacingMsg)
            }
        }
    }

    private fun suspendTransaction(t: Transaction) {
        transactionManager.suspendTransaction(t.transactionId) {
            Log.e(TAG, "Error suspendTransaction $it")
            if (model.devMode.value == true) {
                showError(it)
            } else {
                showError(it.userFacingMsg)
            }
        }
    }

    private fun resumeTransaction(t: Transaction) {
        transactionManager.resumeTransaction(t.transactionId) {
            Log.e(TAG, "Error resumeTransaction $it")
            if (model.devMode.value == true) {
                showError(it)
            } else {
                showError(it.userFacingMsg)
            }
        }
    }
}
