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

package net.taler.wallet.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.taler.wallet.R
import net.taler.wallet.compose.CopyToClipboardButton
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalSerializationApi::class)
private val observabilityJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

@Composable
fun ObservabilityDialog(
    events: List<ObservabilityEvent>,
    onDismiss: () -> Unit,
) {
    var showJson by remember { mutableStateOf(false) }

    AlertDialog(
        title = { Text(stringResource(R.string.observability_title)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(events) { event ->
                    ObservabilityItem(event, showJson)
                }
            }
        },
        onDismissRequest = onDismiss,
        dismissButton = {
            Button(onClick = { showJson = !showJson }) {
                Text(if (showJson) {
                    stringResource(R.string.observability_hide_json)
                } else {
                    stringResource(R.string.observability_show_json)
                })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
fun ObservabilityItem(
    event: ObservabilityEvent,
    showJson: Boolean,
) {
    val body = observabilityJson.encodeToString(event.body)
    val timestamp = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(event.timestamp)

    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(event.type) },
        overlineContent = { Text(timestamp) },
        supportingContent = if (!showJson) null else { ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    )
                ) {
                    Text(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        text = body,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                CopyToClipboardButton(
                    label = "Event",
                    content = body,
                    colors = ButtonDefaults.textButtonColors(),
                )
            }
        },
    )
}