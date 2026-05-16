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

package net.taler.wallet.payment

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.main.TAG
import net.taler.wallet.transactions.TransactionManager

@Composable
fun PromptPaymentScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val paymentManager = model.paymentManager
    val transactionManager = model.transactionManager
    val payStatus by paymentManager.payStatus.observeAsState(PayStatus.None)
    var showImage by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(payStatus) {
        val status = payStatus
        when (status) {
            is PayStatus.Success -> {
                navigateToTransaction(status.transactionId, transactionManager, onNavigate, onNavigateBack)
                paymentManager.resetPayStatus()
            }

            is PayStatus.AlreadyPaid -> {
                navigateToTransaction(status.transactionId, transactionManager, onNavigate, onNavigateBack)
                paymentManager.resetPayStatus()
            }

            is PayStatus.Pending -> {
                navigateToTransaction(status.transactionId, transactionManager, onNavigate, onNavigateBack)
                paymentManager.resetPayStatus()
                status.error?.let { error ->
                    onShowError(error)
                }
            }

            else -> {}
        }
    }

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        title = { Text(stringResource(R.string.payment_title)) },
        onNavigateBack = onNavigateBack,
    ) { paddingValues ->
        when (val status = payStatus) {
            is PayStatus.None,
            is PayStatus.Loading,
            is PayStatus.Prepared -> LoadingScreen(Modifier.padding(paddingValues))
            is PayStatus.Choices -> {
                PromptPaymentComposable(
                    modifier = Modifier.padding(paddingValues),
                    status = status,
                    onConfirm = { index, useDonau ->
                        paymentManager.confirmPay(
                            transactionId = status.transactionId,
                            choiceIndex = index,
                            useDonau = useDonau,
                        )
                    },
                    onCancel = {
                        transactionManager.abortTransaction(
                            status.transactionId,
                            onSuccess = {
                                onNavigateBack()
                            },
                            onError = { error ->
                                Log.e(TAG, "Error abortTransaction $error")
                                onShowError(error)
                            }
                        )
                    },
                    onClickImage = { bitmap ->
                        showImage = bitmap
                    },
                    checkDonauStatus = { index ->
                        status.choices.find { it.choiceIndex == index }?.let { choice ->
                            paymentManager.checkDonauForChoice(choice)
                        } ?: DonauStatus.Unavailable
                    },
                    onSetupDonau = { donauBaseUrl ->
                        onNavigate(WalletDestination.SetDonau(donauBaseUrl), false)
                    },
                )
            }
            else -> {}
        }
    }

    if (showImage != null) {
        // ProductImageFragment was a DialogFragment. 
        // For now, we can just use a simple Modal or similar if needed, 
        // but let's just keep it simple or implement a basic compose dialog.
        ProductImageDialog(showImage!!) { showImage = null }
    }
}

@Composable
fun ProductImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
            )
        }
    }
}

private suspend fun navigateToTransaction(
    id: String?,
    transactionManager: TransactionManager,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    if (id != null && transactionManager.selectTransaction(id)) {
        onNavigate(WalletDestination.TransactionPayment, true)
    } else {
        onNavigateBack()
    }
}
