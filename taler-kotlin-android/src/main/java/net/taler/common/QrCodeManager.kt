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
import android.graphics.Bitmap.Config.RGB_565
import android.graphics.Canvas
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat.QR_CODE
import com.google.zxing.EncodeHintType.ERROR_CORRECTION
import com.google.zxing.EncodeHintType.MARGIN
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeManager {

    fun makeQrCode(
        text: String,
        size: Int = 256,
        margin: Int = 4,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        centerLogo: ((size: Int) -> Bitmap)? = null,
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

        if (centerLogo != null) {
            val combined = createBitmap(bmp.width, bmp.height, bmp.config!!)
            val canvas = Canvas(combined)
            canvas.drawBitmap(bmp, Matrix(), null)

            val logo = centerLogo(canvas.width / 6)
            val centreX = (canvas.width - logo.width) / 2f
            val centreY = (canvas.height - logo.height) / 2f

            canvas.drawBitmap(logo, centreX, centreY, null)
            return combined
        }

        return bmp
    }
}
