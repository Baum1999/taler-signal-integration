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

package net.taler.merchantpos.refund

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import net.taler.merchantpos.MainActivity
import net.taler.lib.android.AnimatedQrCodeComposable
import net.taler.lib.android.TalerNfcService.Companion.hasNfc
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.showPosError

class RefundUriFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val refundManager by lazy { model.refundManager }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val result = refundManager.refundResult.value as? RefundResult.Success
        if (result == null) {
            requireActivity().showPosError(R.string.refund_state_missing)
            (requireActivity() as MainActivity).navigateBack()
            return@apply
        }
        setContent {
            RefundUriScreen(
                result = result,
                deviceHasNfc = hasNfc(requireContext()),
                onAbort = {
                    refundManager.abortRefund()
                    (requireActivity() as MainActivity).navigateBack()
                },
            )
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refundManager.refundReceived.observe(viewLifecycleOwner) { received ->
            if (received == true) {
                refundManager.completeRefund()
                (requireActivity() as MainActivity).apply {
                    navigateBack()
                    navigateBack()
                }
            }
        }
    }
}

@Composable
private fun RefundUriScreen(
    result: RefundResult.Success,
    deviceHasNfc: Boolean,
    onAbort: () -> Unit,
) {
    PosTheme {
        val introText = if (deviceHasNfc) {
            stringResource(R.string.refund_intro_nfc)
        } else {
            stringResource(R.string.refund_intro)
        }
        val isTabletLayout = LocalConfiguration.current.smallestScreenWidthDp >= 720

        if (isTabletLayout) {
            TabletRefundUriScreen(result, introText, onAbort)
        } else {
            PhoneRefundUriScreen(result, introText, onAbort)
        }
    }
}

@Composable
private fun TabletRefundUriScreen(
    result: RefundResult.Success,
    introText: String,
    onAbort: () -> Unit,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                    val qrSize = minOf(maxWidth, maxHeight)
                    Box(
                        modifier = Modifier.size(qrSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedQrCodeComposable(
                            link = result.refundUri,
                            logoPainter = painterResource(R.drawable.ic_taler_logo_qr),
                            modifier = Modifier.fillMaxSize(),
                        )
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
                text = result.amount.toString(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.refund_order_ref,
                    result.item.orderId,
                    result.reason,
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onAbort,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.refund_abort))
                }
            }
        }
    }
}

@Composable
private fun PhoneRefundUriScreen(
    result: RefundResult.Success,
    introText: String,
    onAbort: () -> Unit,
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
                    val qrSize = minOf(maxWidth, maxHeight)
                    Box(
                        modifier = Modifier.size(qrSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedQrCodeComposable(
                            link = result.refundUri,
                            logoPainter = painterResource(R.drawable.ic_taler_logo_qr),
                            modifier = Modifier.fillMaxSize(),
                        )
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
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val verticalGap = (maxHeight * 0.02f).coerceIn(2.dp, 8.dp)
                val isCompactHeight = maxHeight < 420.dp
                val introStyle = if (isCompactHeight) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                }
                val amountStyle = if (isCompactHeight) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                }
                val detailsStyle = if (isCompactHeight) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(verticalGap, Alignment.CenterVertically),
                ) {
                    Text(
                        text = introText,
                        style = introStyle,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = result.amount.toString(),
                        style = amountStyle,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(
                            R.string.refund_order_ref,
                            result.item.orderId,
                            result.reason,
                        ),
                        style = detailsStyle,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = onAbort,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(stringResource(R.string.refund_abort))
                    }
                }
            }
        }
    }
}
