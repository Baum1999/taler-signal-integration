/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.compose

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import net.taler.common.QrCodeManager
import net.taler.common.QrLogoSize
import net.taler.common.copyToClipBoard
import net.taler.lib.android.AnimatedQrCodeComposable
import net.taler.wallet.R

sealed class QrCodeParams {
    data object Taler: QrCodeParams()

    data class Custom(
        val centerLogo: Drawable? = null,
        val centerLogoSize: QrLogoSize = QrLogoSize.MEDIUM,
        val drawCenterLogoBackground: Boolean = false,
    ): QrCodeParams()
}

@Composable
fun ColumnScope.QrCodeUriComposable(
    modifier: Modifier = Modifier,
    qrData: String,
    clipboardLabel: String,
    params: QrCodeParams,
    buttonText: String = stringResource(R.string.copy),
    showContents: Boolean = true,
    shareAsQrCode: Boolean = false,
    inBetween: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val qrCodeSize = getQrCodeSize()
    val qrState by produceState<Bitmap?>(null) {
        value = QrCodeManager.makeQrCode(
            qrData,
            qrCodeSize.value.toInt(),
            centerLogo = when (params) {
                QrCodeParams.Taler -> ContextCompat.getDrawable(context, net.taler.common.R.drawable.ic_taler_logo_qr)
                is QrCodeParams.Custom -> params.centerLogo
            },

            centerLogoSize = when (params) {
                QrCodeParams.Taler -> QrLogoSize.MEDIUM
                is QrCodeParams.Custom -> params.centerLogoSize
            },

            drawBackground = false,
        )
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(bottom = if (showContents) 8.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (params) {
            QrCodeParams.Taler -> {
                AnimatedQrCodeComposable(
                    modifier = Modifier.fillMaxSize(),
                    link = qrData,
                    logoPainter = painterResource(net.taler.common.R.drawable.ic_taler_logo_qr)
                )
            }

            is QrCodeParams.Custom -> {
                qrState?.let { qrCode ->
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = qrCode.asImageBitmap(),
                        contentDescription = null,
                    )
                }
            }
        }
    }

    if (inBetween != null) inBetween()
    val scrollState = rememberScrollState()
    if (showContents) {
        if (!shareAsQrCode) {
            Card(modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp, top = 10.dp)) {
                Text(
                    modifier = Modifier
                        .padding(6.dp)
                        .horizontalScroll(scrollState),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    text = qrData,
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (!shareAsQrCode) {
                CopyToClipboardButton(
                    label = clipboardLabel,
                    content = qrData,
                    buttonText = buttonText,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            ShareButton(
                content = qrData,
                shareAsQrCode = shareAsQrCode,
                qrBitmap = qrState,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
            )
        }
    }
}

@Composable
fun getQrCodeSize(): Dp {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    return min(screenHeight, screenWidth)
}

@Composable
fun CopyToClipboardButton(
    label: String,
    content: String,
    modifier: Modifier = Modifier,
    buttonText: String = stringResource(R.string.copy),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    val context = LocalContext.current
    Button(
        modifier = modifier,
        colors = colors,
        onClick = { copyToClipBoard(context, label, content) },
    ) {
        Icon(
            Icons.Default.ContentCopy,
            buttonText,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(buttonText)
    }
}
