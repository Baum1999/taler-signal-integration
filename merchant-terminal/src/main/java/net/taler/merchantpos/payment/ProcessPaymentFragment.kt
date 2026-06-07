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

package net.taler.merchantpos.payment

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.compose.runtime.livedata.observeAsState
import net.taler.lib.android.AnimatedQrCodeComposable
import net.taler.lib.android.TalerNfcService.Companion.hasNfc
import net.taler.lib.android.copyToClipBoard
import net.taler.lib.android.shareText
import net.taler.merchantpos.MainActivity
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.showPosError

class ProcessPaymentFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val paymentManager by lazy { model.paymentManager }

    private var deviceHasNfc = false

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val payment by paymentManager.payment.observeAsState()
            val deleteNeedsForce by paymentManager.deleteNeedsForce.observeAsState()
            payment?.let {
                ProcessPaymentScreen(
                    payment = it,
                    deviceHasNfc = deviceHasNfc,
                    showForceDeleteDialog = deleteNeedsForce == true,
                    onCancel = {
                        if (it.claimed) {
                            paymentManager.tryDeleteOrder()
                        } else {
                            onPaymentCancel()
                        }
                    },
                    onForceDelete = ::onForceDeleteOrder,
                    onDismissForceDelete = {
                        paymentManager.clearDeleteNeedsForce()
                    },
                    onShare = { uri -> requireContext().shareText(uri) },
                    onCopy = { uri ->
                        copyToClipBoard(requireContext(), "Payment URI", uri)
                    },
                )
            }

            // Navigate back on successful (non-force) delete
            LaunchedEffect(deleteNeedsForce) {
                if (deleteNeedsForce == false) {
                    paymentManager.clearDeleteNeedsForce()
                    (requireActivity() as MainActivity).navigateBack()
                }
            }
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        deviceHasNfc = hasNfc(requireContext())
        paymentManager.payment.observe(viewLifecycleOwner) { payment ->
            onPaymentStateChanged(payment)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun onPaymentStateChanged(payment: Payment) {
        if (payment.error != null) {
            val (mainText, detailText) = getPaymentErrorDisplay(payment)
            requireActivity().showPosError(mainText, detailText)
            (requireActivity() as MainActivity).navigateBack()
            return
        }
        if (payment.paid) {
            model.orderManager.onOrderPaid(payment.order.id)
            (requireActivity() as MainActivity).navigateTo(PosDestination.PaymentSuccess)
        }
    }

    private fun onPaymentCancel() {
        paymentManager.cancelPayment()
        (requireActivity() as MainActivity).navigateBack()
    }

    private fun onForceDeleteOrder() {
        paymentManager.forceDeleteOrder()
        (requireActivity() as MainActivity).navigateBack()
    }

    private fun getPaymentErrorDisplay(payment: Payment): Pair<String, String> {
        val error = payment.error.orEmpty()
        if (payment.orderId != null) {
            return getString(R.string.error_payment) to error
        }
        val normalized = error.lowercase()
        return when {
            "inventory" in normalized ||
                "stock" in normalized ||
                "insufficient" in normalized ||
                "sold out" in normalized ||
                "out of stock" in normalized ->
                getString(R.string.error_inventory_unavailable) to error

            else ->
                getString(R.string.error_order_creation) to error
        }
    }
}

@Composable
private fun ProcessPaymentScreen(
    payment: Payment,
    deviceHasNfc: Boolean,
    showForceDeleteDialog: Boolean,
    onCancel: () -> Unit,
    onForceDelete: () -> Unit,
    onDismissForceDelete: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    if (showForceDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissForceDelete,
            title = { Text(stringResource(R.string.force_delete_dialog_title)) },
            text = { Text(stringResource(R.string.force_delete_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = onForceDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.force_delete_dialog_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissForceDelete) {
                    Text(stringResource(R.string.payment_cancel))
                }
            },
        )
    }

    PosTheme {
        val introText = if (payment.claimed) {
            stringResource(R.string.payment_claimed)
        } else if (deviceHasNfc && payment.talerPayUri != null) {
            stringResource(R.string.payment_intro_nfc)
        } else {
            stringResource(R.string.payment_intro)
        }
        val isTabletLayout = LocalConfiguration.current.smallestScreenWidthDp >= 720

        if (isTabletLayout) {
            TabletProcessPaymentScreen(payment, introText, onCancel, onShare, onCopy)
        } else {
            PhoneProcessPaymentScreen(payment, introText, onCancel, onShare, onCopy)
        }
    }
}

@Composable
private fun TabletProcessPaymentScreen(
    payment: Payment,
    introText: String,
    onCancel: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (!payment.claimed) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val payUri = payment.talerPayUri
                        val qrSize = minOf(maxWidth, maxHeight)
                        Box(
                            modifier = Modifier.size(qrSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (payUri == null) {
                                CircularProgressIndicator()
                            } else {
                                AnimatedQrCodeComposable(
                                    link = payUri,
                                    logoPainter = painterResource(R.drawable.ic_taler_logo_qr),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            if (!payment.claimed) payment.talerPayUri?.let { payUri ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { onShare(payUri) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.share))
                    }
                    Button(onClick = { onCopy(payUri) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.copy))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = introText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = payment.order.total.toString(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            payment.orderId?.let {
                Text(
                    text = stringResource(R.string.payment_order_id, it),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.payment_cancel))
            }
        }
    }
}

@Composable
private fun PhoneProcessPaymentScreen(
    payment: Payment,
    introText: String,
    onCancel: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!payment.claimed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val payUri = payment.talerPayUri
                        val qrSize = minOf(maxWidth, maxHeight)
                        Box(
                            modifier = Modifier.size(qrSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (payUri == null) {
                                CircularProgressIndicator()
                            } else {
                                AnimatedQrCodeComposable(
                                    link = payUri,
                                    logoPainter = painterResource(R.drawable.ic_taler_logo_qr),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = introText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = payment.order.total.toString(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            payment.orderId?.let {
                Text(
                    text = stringResource(R.string.payment_order_id, it),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            payment.talerPayUri?.let { payUri ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onShare(payUri) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.share))
                    }
                    OutlinedButton(
                        onClick = { onCopy(payUri) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.copy))
                    }
                }
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.payment_cancel))
            }
        }
    }
}
