/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.wallet.transfer

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.taler.common.QrLogoSize
import net.taler.wallet.R
import net.taler.wallet.compose.ExpandableCard
import net.taler.wallet.compose.QrCodeUriComposable
import net.taler.wallet.withdraw.QrCodeSpec
import net.taler.wallet.withdraw.QrCodeSpec.Type.EpcQr
import net.taler.wallet.withdraw.QrCodeSpec.Type.SPC

@Composable
fun ColumnScope.PaytoQrCode(
    modifier: Modifier = Modifier,
    qrCode: QrCodeSpec,
) {
    val context = LocalContext.current
    QrCodeUriComposable(
        modifier = modifier,
        talerUri = qrCode.qrContent,
        clipBoardLabel = getQrCodeLabel(qrCode),
        showContents = true,
        shareAsQrCode = true,
        centerLogoSize = QrLogoSize.SMALL,
        centerLogo = when (qrCode.type) {
            SPC -> ContextCompat.getDrawable(context, R.drawable.ic_swiss_qr)
            else -> null
        },
    )
}

@Composable
fun PaytoQrCard(
    expanded: Boolean,
    setExpanded: (expanded: Boolean) -> Unit,
    qrCode: QrCodeSpec,
) {
    ExpandableCard(
        expanded = expanded,
        setExpanded = setExpanded,
        header = {
            Text(getQrCodeLabel(qrCode),
                style = MaterialTheme.typography.titleMedium)
        },
        content = {
            PaytoQrCode(qrCode = qrCode)
            Spacer(Modifier.height(8.dp))
        },
    )
}

@Composable
fun getQrCodeLabel(qr: QrCodeSpec) = when(qr.type) {
    EpcQr -> stringResource(R.string.withdraw_manual_qr_epc)
    SPC -> stringResource(R.string.withdraw_manual_qr_spc)
    else -> stringResource(R.string.withdraw_manual_qr_unknown)
}