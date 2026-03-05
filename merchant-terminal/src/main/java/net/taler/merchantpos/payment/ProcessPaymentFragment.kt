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

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import net.taler.common.QrCodeManager.makeQrCode
import net.taler.common.copyToClipBoard
import net.taler.common.fadeIn
import net.taler.common.fadeOut
import net.taler.common.shareText
import net.taler.common.showError
import net.taler.lib.android.AnimatedQrCodeComposable
import net.taler.lib.android.TalerNfcService.Companion.hasNfc
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.databinding.FragmentProcessPaymentBinding

class ProcessPaymentFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val paymentManager by lazy { model.paymentManager }

    private lateinit var ui: FragmentProcessPaymentBinding
    private lateinit var qrPreviewBackCallback: OnBackPressedCallback
    private var currentPayUri: String? = null
    private var currentQrBitmap: Bitmap? = null
    private var deviceHasNfc: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentProcessPaymentBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        deviceHasNfc = hasNfc(requireContext())
        ui.payIntroView.setText(R.string.payment_intro)
        // Show only a simple loader before the first QR bitmap is rendered.
        ui.qrcodeLayout.visibility = View.INVISIBLE
        ui.qrcodeView.visibility = View.INVISIBLE
        ui.progressBar.visibility = View.VISIBLE
        ui.shareButton.isEnabled = false
        ui.copyButton.isEnabled = false
        paymentManager.payment.observe(viewLifecycleOwner) { payment ->
            onPaymentStateChanged(payment)
        }
        ui.qrcodeView.setOnClickListener {
            showQrPreview()
        }
        ui.qrPreviewOverlay.setOnClickListener {
            hideQrPreview()
        }
        qrPreviewBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                hideQrPreview()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, qrPreviewBackCallback)
        ui.cancelPaymentButton.setOnClickListener {
            onPaymentCancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentManager.cancelPayment()
    }

    private fun onPaymentStateChanged(payment: Payment) {
        val previewShouldClose =
            payment.error != null ||
                payment.paid ||
                payment.claimed ||
                (currentPayUri != null && payment.talerPayUri != currentPayUri)
        if (previewShouldClose) {
            hideQrPreview()
        }
        if (payment.error != null) {
            requireActivity().showError(R.string.error_payment, payment.error)
            findNavController().navigateUp()
            return
        }
        if (payment.paid) {
            model.orderManager.onOrderPaid(payment.order.id)
            val nav = findNavController()
            val previousDestinationId = nav.previousBackStackEntry?.destination?.id
            val options = previousDestinationId?.let {
                NavOptions.Builder()
                    .setPopUpTo(it, false)
                    .build()
            }
            nav.navigate(R.id.paymentSuccess, null, options)
            return
        }
        if (payment.claimed) {
            ui.qrcodeLayout.fadeOut()
            ui.payIntroView.setText(R.string.payment_claimed)
        } else {
            val introRes =
                if (deviceHasNfc && payment.talerPayUri != null) {
                    R.string.payment_intro_nfc
                } else {
                    R.string.payment_intro
                }
            ui.payIntroView.setText(introRes)
            payment.talerPayUri?.let {
                val uriChanged = it != currentPayUri
                if (uriChanged) {
                    currentPayUri = it
                    renderPaymentQrCode(it) {
                        ui.qrcodeView.visibility = View.VISIBLE
                        if (ui.qrcodeLayout.visibility != View.VISIBLE) {
                            ui.qrcodeLayout.fadeIn()
                        }
                        ui.progressBar.fadeOut()
                    }
                    ui.shareButton.setOnClickListener { _ ->
                        requireContext().shareText(it)
                    }
                    ui.copyButton.setOnClickListener { _ ->
                        copyToClipBoard(requireContext(), "Payment URI", it)
                    }
                } else {
                    if (ui.qrcodeLayout.visibility != View.VISIBLE) {
                        ui.qrcodeLayout.fadeIn()
                    }
                    ui.qrcodeView.visibility = View.VISIBLE
                    ui.progressBar.fadeOut()
                }
                ui.shareButton.isEnabled = true
                ui.copyButton.isEnabled = true
            }
        }
        ui.payIntroView.fadeIn()
        ui.amountView.text = payment.order.total.toString()
        payment.orderId?.let {
            ui.orderRefView.text = getString(R.string.payment_order_id, it)
            ui.orderRefView.fadeIn()
        }
    }

    private fun onPaymentCancel() {
        paymentManager.cancelPayment()
        findNavController().navigateUp()
        Snackbar.make(requireView(), R.string.payment_canceled, LENGTH_LONG).show()
    }

    private fun showQrPreview() {
        val qrBitmap = currentQrBitmap ?: return
        ui.qrPreviewImage.setImageBitmap(qrBitmap)
        ui.qrPreviewOverlay.visibility = View.VISIBLE
        qrPreviewBackCallback.isEnabled = true
    }

    private fun hideQrPreview() {
        if (ui.qrPreviewOverlay.visibility != View.VISIBLE) return
        ui.qrPreviewOverlay.visibility = View.GONE
        ui.qrPreviewImage.setImageDrawable(null)
        qrPreviewBackCallback.isEnabled = false
    }

    private fun renderPaymentQrCode(text: String, onRendered: (() -> Unit)? = null) {
        ui.qrcodeView.post {
            val blockSize = minOf(ui.qrcodeView.width, ui.qrcodeView.height).coerceAtLeast(256)
            val qrSize = (blockSize * 0.88f).toInt().coerceAtLeast(256)
            currentQrBitmap = makePaymentQrCode(text, qrSize)

            val density = resources.displayMetrics.density
            val widthDp = ui.qrcodeView.width / density
            val heightDp = ui.qrcodeView.height / density
            ui.qrcodeView.setContent {
                PosTheme {
                    AnimatedQrCodeComposable(
                        width = widthDp.dp,
                        height = heightDp.dp,
                        link = text,
                        logoPainter = painterResource(R.drawable.ic_taler_logo_qr),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            onRendered?.invoke()
        }
    }

    private fun makePaymentQrCode(text: String, size: Int): Bitmap {
        return makeQrCode(
            text = text,
            size = size,
            margin = 0,
            errorCorrection = ErrorCorrectionLevel.H,
            centerLogo = null,
            drawBackground = true,
            lightColor = ContextCompat.getColor(requireContext(), R.color.colorSurfaceVariant),
            trimQuietZone = true,
        )
    }



}
