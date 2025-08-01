/*
 * This file is part of GNU Taler
 * (C) 2023 Taler Systems S.A.
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

import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat.startActivity
import kotlinx.coroutines.launch
import net.taler.common.shareAsQrCode
import net.taler.wallet.BuildConfig
import net.taler.wallet.R

@Composable
fun ShareButton(
    content: String,
    modifier: Modifier = Modifier,
    buttonText: String = stringResource(R.string.share),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shareAsQrCode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Button(
        modifier = modifier,
        colors = colors,
        onClick = {
            if (shareAsQrCode) {
                scope.launch { content.shareAsQrCode(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                ) }
            } else {
                val sendIntent: Intent = Intent().apply {
                    action = ACTION_SEND
                    putExtra(EXTRA_TEXT, content)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(context, shareIntent, null)
            }
        },
    ) {
        Icon(
            Icons.Default.Share,
            buttonText,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(buttonText)
    }
}
