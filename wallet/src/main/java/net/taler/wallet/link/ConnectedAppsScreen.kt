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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.taler.wallet.R
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.main.MainViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ConnectedAppsScreen(
    model: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val consentStore = remember { ConsentStore(context) }
    var entries by remember { mutableStateOf(consentStore.listEntries()) }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.settings_connected_apps)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            if (entries.isEmpty()) {
                EmptyComposable(
                    modifier = Modifier.padding(paddingValues),
                    message = stringResource(R.string.connected_apps_empty),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    items(entries, key = { it.certSha256 }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.packageName) },
                            supportingContent = {
                                Text(DateFormat.getDateTimeInstance().format(Date(entry.grantedAtMillis)))
                            },
                            trailingContent = {
                                Button(onClick = {
                                    consentStore.revoke(entry.certSha256)
                                    entries = consentStore.listEntries()
                                }) {
                                    Text(stringResource(R.string.connected_apps_disconnect))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
