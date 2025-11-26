/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun ErrorComposable(
    error: TalerErrorInfo,
    devMode: Boolean,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 8.dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )

        Text(
            modifier = Modifier.padding(bottom = 16.dp),
            text = stringResource(R.string.error),
            style = MaterialTheme.typography.displaySmall,
        )

        val jsonError = remember(error) {
            val json = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
            json.encodeToString(error)
        }

        Text(
            modifier = Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            text = if (!devMode) {
                error.userFacingMsg
            } else jsonError,
            fontFamily = if (devMode) {
                FontFamily.Monospace
            } else {
                FontFamily.Default
            },
        )

        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CopyToClipboardButton(
                label = "Error",
                content = jsonError,
            )

            ShareButton(
                content = jsonError,
            )
        }

        if (onClose != null) Button(
            modifier = Modifier.padding(bottom = 16.dp),
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.close))
        }

        BottomInsetsSpacer()
    }
}

@Preview
@Composable
fun ErrorComposablePreview(devMode: Boolean = false) {
    TalerSurface {
        ErrorComposable(
            error = TalerErrorInfo.makeCustomError(
                message = "Some random error",
            ),
            devMode = devMode,
            onClose = {},
        )
    }
}

@Preview
@Composable
fun ErrorComposableDevPreview() {
    ErrorComposablePreview(
        devMode = true,
    )
}