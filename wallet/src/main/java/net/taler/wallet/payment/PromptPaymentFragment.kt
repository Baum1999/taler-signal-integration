/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.wallet.payment

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import net.taler.common.showError
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.TAG
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.showError

// TODO:

class PromptPaymentFragment: Fragment(), ProductImageClickListener {
    private val model: MainViewModel by activityViewModels()
    private val paymentManager by lazy { model.paymentManager }
    private val transactionManager by lazy { model.transactionManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                val payStatus by paymentManager.payStatus.observeAsState(PayStatus.None)
                when(val status = payStatus) {
                    is PayStatus.None,
                    is PayStatus.Loading,
                    is PayStatus.Prepared -> LoadingScreen()
                    is PayStatus.Checked -> {} // does not apply, only used for templates
                    is PayStatus.Choices -> {
                        PromptPaymentComposable(status,
                            onConfirm = { index ->
                                paymentManager.confirmPay(status.transactionId, index)
                            },
                            onCancel = {
                                transactionManager.abortTransaction(
                                    status.transactionId,
                                    onSuccess = {
                                        Snackbar.make(
                                            requireView(),
                                            getString(R.string.payment_aborted),
                                            LENGTH_LONG
                                        ).show()
                                        findNavController().popBackStack()
                                    },
                                    onError = { error ->
                                        Log.e(TAG, "Error abortTransaction $error")
                                        if (model.devMode.value == false) {
                                            showError(error.userFacingMsg)
                                        } else {
                                            showError(error)
                                        }
                                    }
                                )
                            },
                            onClickImage = { bitmap ->
                                onImageClick(bitmap)
                            }
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        paymentManager.payStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is PayStatus.Success -> {
                    paymentManager.resetPayStatus()
                    navigateToTransaction(status.transactionId)
                    if (status.automaticExecution) {
                        Snackbar.make(requireView(), R.string.payment_automatic_execution, LENGTH_LONG).show()
                    }
                }

                is PayStatus.AlreadyPaid -> {
                    paymentManager.resetPayStatus()
                    navigateToTransaction(status.transactionId)
                    Snackbar.make(requireView(), R.string.payment_already_paid, LENGTH_LONG).show()
                }

                is PayStatus.Pending -> {
                    paymentManager.resetPayStatus()
                    navigateToTransaction(status.transactionId)
                    if (status.error != null) {
                        if (model.devMode.value == true) {
                            showError(status.error)
                        } else {
                            showError(status.error.userFacingMsg)
                        }
                    }
                }

                else -> {}
            }
        }
    }

    override fun onImageClick(image: Bitmap) {
        val f = ProductImageFragment.new(image)
        f.show(parentFragmentManager, "image")
    }

    private fun navigateToTransaction(id: String?) {
        lifecycleScope.launch {
            if (id != null && transactionManager.selectTransaction(id)) {
                findNavController().navigate(R.id.action_promptPayment_to_nav_transactions_detail_payment)
            } else {
                findNavController().navigate(R.id.action_promptPayment_to_nav_main)
            }
        }
    }
}

