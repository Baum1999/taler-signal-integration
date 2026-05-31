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

package net.taler.wallet.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.taler.common.Merchant
import net.taler.lib.android.base64Bitmap

@Composable
fun MerchantAvatar(
    merchantInfo: Merchant?,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    onClickImage: ((Bitmap) -> Unit) = {},
) {
    val logo = remember(merchantInfo?.logo) { merchantInfo?.logo?.base64Bitmap }

    Box(
        modifier
            .size(size)
            .background(
                shape = CircleShape,
                color = if (logo != null) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clip(CircleShape)
            .clickable { if (logo != null) onClickImage(logo) },
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                logo.asImageBitmap(),
                modifier = Modifier.fillMaxSize(),
                contentDescription = null,
            )
        } else {
            Icon(
                Icons.Default.Store,
                modifier = Modifier.fillMaxSize(0.54f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
    }
}