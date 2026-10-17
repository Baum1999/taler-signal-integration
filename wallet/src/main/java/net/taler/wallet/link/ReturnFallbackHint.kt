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

package net.taler.wallet.link

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.compose.WarningLabel

/** Einmal pro Screen-Instanz: kann die Wallet an [returnUri] zurueckspringen? */
@Composable
fun rememberCanReturn(returnUri: String): Boolean {
    val context = LocalContext.current
    return remember(returnUri) { ReturnIntentSender.canReturn(context, returnUri) }
}

/**
 * Hinweis fuer die Compose-Screens bei unbekanntem Ruecksprungziel. Stoesst
 * beim ersten Anzeigen selbst eine Update-Pruefung an (UpdateCheckHook) und
 * zeigt deren Stand; ohne Updater (gnu-fork) entfaellt die Statuszeile.
 */
@Composable
fun ReturnFallbackHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updateState by produceState<UpdateCheckState?>(initialValue = null) {
        UpdateCheckHook.checkNow?.invoke(context)?.collect { value = it }
    }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        WarningLabel(stringResource(R.string.compose_return_unknown_hint))
        updateState?.let { state ->
            Text(
                text = stringResource(
                    when (state) {
                        UpdateCheckState.SEARCHING -> R.string.compose_return_update_searching
                        UpdateCheckState.UPDATE_FOUND -> R.string.compose_return_update_found
                        UpdateCheckState.NO_UPDATE -> R.string.compose_return_update_none
                        UpdateCheckState.FAILED -> R.string.compose_return_update_failed
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
