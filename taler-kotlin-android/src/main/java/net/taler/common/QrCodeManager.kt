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

package net.taler.common

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Bitmap.Config.RGB_565
import android.graphics.Canvas
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat.QR_CODE
import com.google.zxing.EncodeHintType.ERROR_CORRECTION
import com.google.zxing.EncodeHintType.MARGIN
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class QrLogoSize(val size: Float) {
    SMALL(0.15f),
    MEDIUM(0.20f),
    BIG(0.25f),
}

object QrCodeManager {

    fun makeQrCode(
        text: String,
        size: Int = 256,
        margin: Int = 2,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        centerLogo: Drawable? = null,
        centerLogoSize: QrLogoSize = QrLogoSize.MEDIUM,
        drawBackground: Boolean = false,
    ): Bitmap {
        val qrCodeWriter = QRCodeWriter()
        val hints = mapOf(
            MARGIN to margin.coerceAtLeast(0),
            ERROR_CORRECTION to errorCorrection,
        )
        val bitMatrix = qrCodeWriter.encode(text, QR_CODE, size, size, hints)
        val height = bitMatrix.height
        val width = bitMatrix.width
        val bmp = createBitmap(width, height, RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp[x, y] = if (bitMatrix.get(x, y)) BLACK else WHITE
            }
        }

        return if (centerLogo != null) {
            addCenteredLogo(bmp, centerLogo, centerLogoSize, drawBackground)
        } else {
            bmp
        }
    }

    private fun addCenteredLogo(
        qrBitmap: Bitmap,
        logoDrawable: Drawable,
        logoSize: QrLogoSize = QrLogoSize.MEDIUM,
        drawBackground: Boolean = false,
    ): Bitmap {
        val result = qrBitmap.copy(ARGB_8888, true)
        val canvas = Canvas(result)
        val logoBitmap = drawableToBitmap(logoDrawable)

        var logoMaxWidth = (result.width * logoSize.size).toInt()
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

        if (drawBackground) {
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
                color = WHITE
            }
            val cornerRadius =
                halfBackgroundHeight // * 0.8f taler has circle in logo, so it can be fine
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)
        }

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
