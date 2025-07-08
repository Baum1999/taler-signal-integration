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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.compose.ExpandableCard
import net.taler.wallet.compose.QrCodeUriComposable
import net.taler.wallet.withdraw.QrCodeSpec
import net.taler.wallet.withdraw.QrCodeSpec.Type.EpcQr
import net.taler.wallet.withdraw.QrCodeSpec.Type.SPC

@Composable
fun PaytoQrCard(
    expanded: Boolean,
    setExpanded: (expanded: Boolean) -> Unit,
    qrCode: QrCodeSpec,
) {
    val label = when(qrCode.type) {
        EpcQr -> stringResource(R.string.withdraw_manual_qr_epc)
        SPC -> stringResource(R.string.withdraw_manual_qr_spc)
        else -> return
    }

    ExpandableCard(
        expanded = expanded,
        setExpanded = setExpanded,
        header = {
            Text(label, style = MaterialTheme.typography.titleMedium)
        },
        content = {
            QrCodeUriComposable(
                talerUri = qrCode.qrContent,
                clipBoardLabel = label,
                showContents = true,
                shareAsQrCode = true,
            )

            Spacer(Modifier.height(8.dp))
        },
    )
}