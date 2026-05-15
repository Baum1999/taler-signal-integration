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

package net.taler.lib.android

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import net.taler.lib.android.QrCodeManager.makeQrCode

const val QR_CORNER_RADIUS = 0.08f
const val QR_STRIPE_WIDTH = 0.025f
const val QR_DATA_SIZE = 0.88f
const val QR_LOGO_SIZE = 0.30f * QR_DATA_SIZE

@Composable
fun AnimatedQrCodeComposable(
    modifier: Modifier = Modifier,
    link: String,
    logoPainter: Painter? = null,
    qrCornerRadiusFraction: Float = QR_CORNER_RADIUS,
) {
    val infinite = rememberInfiniteTransition(label = "qrStripe")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "qrStripeAngle",
    )
    val stripeColor = Color("#3047a3".toColorInt()) // FIXME: load from light-mode primary
    val stripeBase = Color.White //MaterialTheme.colorScheme.surfaceVariant
    val stripeSoft = remember(stripeBase, stripeColor) {
        lerp(stripeBase, stripeColor, 0.55f)
    }
    val gradientStops = remember {
        floatArrayOf(0.00f, 0.05f, 0.09f, 0.12f, 0.16f, 0.21f, 0.50f, 0.55f, 0.59f, 0.62f, 0.66f, 1.00f)
    }
    val gradientColors = remember(stripeBase, stripeSoft, stripeColor) {
        intArrayOf(
            stripeBase.toArgb(),
            stripeBase.toArgb(),
            stripeSoft.toArgb(),
            stripeColor.toArgb(),
            stripeSoft.toArgb(),
            stripeBase.toArgb(),
            stripeBase.toArgb(),
            stripeSoft.toArgb(),
            stripeColor.toArgb(),
            stripeSoft.toArgb(),
            stripeBase.toArgb(),
            stripeBase.toArgb(),
        )
    }
    val gradientMatrix = remember { Matrix() }
    val stripePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        var drawSize by remember { mutableStateOf<Dp?>(379.dp) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(
                    percent = (qrCornerRadiusFraction * 100).toInt()))
                .zIndex(-1f)
                .background(Color.White), //TODO: VLADA design MaterialTheme.colorScheme.surfaceVariant),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { with(density) {
                    drawSize = it.size.width.toDp()
                } },
        ) {
            val s = with(density) { size.width.toDp() }
            val cornerRadius = s * qrCornerRadiusFraction
            val stripeWidth = s * QR_STRIPE_WIDTH
            val stripePx = stripeWidth.toPx()
            val inset = stripePx / 2f
            val cornerPx = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
            val cx = size.width / 2f
            val cy = size.height / 2f

            val shader = SweepGradient(cx, cy, gradientColors, gradientStops)
            gradientMatrix.reset()
            gradientMatrix.setRotate(angle, cx, cy)
            shader.setLocalMatrix(gradientMatrix)

            stripePaint.strokeWidth = stripePx
            stripePaint.shader = shader

            drawContext.canvas.nativeCanvas.drawRoundRect(
                RectF(inset, inset, size.width - inset, size.height - inset),
                cornerPx,
                cornerPx,
                stripePaint,
            )
        }

        drawSize?.let { drawSize ->
            val qrSize = drawSize * QR_DATA_SIZE
            val qrSizePx = with(density) {
                qrSize.roundToPx().coerceAtLeast(256) }
            val logoSize = drawSize * QR_LOGO_SIZE

            val qrBitmap by produceState<ImageBitmap?>(null) {
                value = makeQrCode(
                    text = link,
                    size = qrSizePx,
                    margin = 0,
                    errorCorrection = ErrorCorrectionLevel.H,
                    centerLogo = null,
                    centerLogoSize = null,
                    drawBackground = true,
                    darkColor = android.graphics.Color.BLACK,
                    lightColor = android.graphics.Color.WHITE,
                    trimQuietZone = true,
                ).asImageBitmap()
            }

            qrBitmap?.let { qr ->
                Image(
                    bitmap = qr,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(qrSize)
                        .background(Color.White),
                )

                if (logoPainter != null) {
                    Image(
                        painter = logoPainter,
                        contentDescription = null,
                        modifier = Modifier.width(logoSize),
                    )
                }
            }
        }
    }
}
