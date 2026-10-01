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
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avatar eines talerlink://compose-send-Empfaengers (Paket T3, siehe
 * ComposeSendScreen.kt). avatarUri kommt wie recipientHint ungeprueft aus
 * dem Link - aus Taler-Sicht angreifer-kontrolliert, dient ausschliesslich
 * der Anzeige. Laden/Dekodieren faellt still auf das Platzhalter-Icon
 * zurueck (kein Crash, kein Toast - derselbe Umgang wie beim
 * recipientHint-Text in ComposeSendScreen.kt).
 */
@Composable
fun RecipientAvatar(
    avatarUri: String?,
    isGroup: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, avatarUri) {
        value = avatarUri?.let { uriString ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
        }
    }

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                loaded.asImageBitmap(),
                modifier = Modifier.fillMaxSize(),
                contentDescription = null,
            )
        } else {
            Icon(
                if (isGroup) Icons.Default.Group else Icons.Default.Person,
                modifier = Modifier.fillMaxSize(0.54f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
    }
}
