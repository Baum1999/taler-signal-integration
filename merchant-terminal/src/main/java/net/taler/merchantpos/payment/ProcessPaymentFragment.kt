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
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
import net.taler.lib.android.TalerNfcService.Companion.hasNfc
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.databinding.FragmentProcessPaymentBinding
import androidx.core.graphics.createBitmap

class ProcessPaymentFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val paymentManager by lazy { model.paymentManager }

    private lateinit var ui: FragmentProcessPaymentBinding
    private lateinit var qrPreviewBackCallback: OnBackPressedCallback
    private var currentPayUri: String? = null
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
        val qrBitmap = (ui.qrcodeView.drawable as? BitmapDrawable)?.bitmap ?: return
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
            val qrSize = minOf(ui.qrcodeView.width, ui.qrcodeView.height).coerceAtLeast(256)
            ui.qrcodeView.setImageBitmap(makePaymentQrCode(text, qrSize))
            onRendered?.invoke()
        }
    }

    private fun makePaymentQrCode(text: String, size: Int): Bitmap {
        val qrBitmap = makeQrCode(
            text = text,
            size = size,
            margin = 1,
            errorCorrection = ErrorCorrectionLevel.H,
        )
        val logoDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_taler_logo_qr)
            ?: return qrBitmap
        return addCenteredLogo(qrBitmap, logoDrawable)
    }

    private fun addCenteredLogo(qrBitmap: Bitmap, logoDrawable: Drawable): Bitmap {
        val result = qrBitmap.copy(ARGB_8888, true)
        val canvas = Canvas(result)
        val logoBitmap = drawableToBitmap(logoDrawable)

        var logoMaxWidth = (result.width * 0.30f).toInt()
        val logoAspectRatio = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
        var logoWidth = logoMaxWidth
        var logoHeight = (logoWidth / logoAspectRatio).toInt().coerceAtLeast(1)
        var horizontalPadding = (logoHeight * 0.12f).toInt()
        var verticalPadding = (logoHeight * 0.09f).toInt()

        val maxOcclusionRatio = 0.11f
        val currentOcclusionRatio =
            ((logoWidth + horizontalPadding * 2f) * (logoHeight + verticalPadding * 2f)) /
                (result.width.toFloat() * result.height.toFloat())
        if (currentOcclusionRatio > maxOcclusionRatio) {
            val scale = kotlin.math.sqrt(maxOcclusionRatio / currentOcclusionRatio)
            logoMaxWidth = (logoMaxWidth * scale).toInt().coerceAtLeast(1)
            logoWidth = logoMaxWidth
            logoHeight = (logoWidth / logoAspectRatio).toInt().coerceAtLeast(1)
            horizontalPadding = (horizontalPadding * scale).toInt()
            verticalPadding = (verticalPadding * scale).toInt()
        }

        val centerX = result.width / 2
        val centerY = result.height / 2
        val halfBackgroundWidth = (logoWidth / 2f) + horizontalPadding
        val halfBackgroundHeight = (logoHeight / 2f) + verticalPadding
        val backgroundRect = RectF(
            centerX - halfBackgroundWidth,
            centerY - halfBackgroundHeight,
            centerX + halfBackgroundWidth,
            centerY + halfBackgroundHeight,
        )

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        val cornerRadius = halfBackgroundHeight // * 0.8f taler has circle in logo, so it can be fine
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

        val destinationRect = Rect(
            centerX - logoWidth / 2,
            centerY - logoHeight / 2,
            centerX + logoWidth / 2,
            centerY + logoHeight / 2,
        )
        canvas.drawBitmap(logoBitmap, null, destinationRect, Paint(Paint.ANTI_ALIAS_FLAG))
        return result
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

}
