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

package net.taler.wallet.mailbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.taler.lib.android.toAbsoluteTime
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailboxScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val mailboxManager = model.mailboxManager
    val mailboxState by mailboxManager.mailbox.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessageEvent by model.showMessageEvent.observeAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showUrlDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showMessageEvent) {
        showMessageEvent?.getIfNotConsumed()?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mailbox") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    val state = mailboxState
                    if (state is MailboxResult.Success) {
                        IconButton(onClick = { showAddressDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Show Mailbox Address")
                        }
                    }
                    IconButton(onClick = { showUrlDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configure Mailbox")
                    }
                    if (state is MailboxResult.Success) {
                        IconButton(onClick = { mailboxManager.refreshMailbox(state.config) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val res = mailboxState) {
                is MailboxResult.Loading -> Text("Loading...", modifier = Modifier.padding(16.dp))
                is MailboxResult.Error -> Text("Error: ${res.error}", modifier = Modifier.padding(16.dp))
                is MailboxResult.Success -> {
                    MessageList(res.messages, onNavigate) { message ->
                        mailboxManager.deleteMessage(res.config, message)
                    }
                }
            }
        }
    }

    if (showAddressDialog) {
        val state = mailboxState
        if (state is MailboxResult.Success) {
            val mailboxAddressURI = "${state.config.mailboxBaseUrl}/${state.config.hAddress}"
            AlertDialog(
                onDismissRequest = { showAddressDialog = false },
                title = { Text("Your Mailbox Address") },
                text = {
                    Text(mailboxAddressURI)
                },
                confirmButton = {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(mailboxAddressURI))
                        scope.launch {
                            snackbarHostState.showSnackbar("Mailbox address URI copied to clipboard")
                        }
                        showAddressDialog = false
                    }) {
                        Text(stringResource(R.string.copy))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddressDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("https://mailbox.gnunet.org") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Configure Mailbox URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Mailbox Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    mailboxManager.loadMailbox(url)
                    showUrlDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun MessageList(
    messages: List<MailboxMessageRecord>,
    onNavigate: NavigateCallback,
    onDelete: (MailboxMessageRecord) -> Unit,
) {
    if (messages.isEmpty()) {
        Text("No messages yet.", modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn {
            items(messages) { message ->
                MessageItem(message, onNavigate, onDelete)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun MessageItem(
    message: MailboxMessageRecord,
    onNavigate: NavigateCallback,
    onDelete: (MailboxMessageRecord) -> Unit,
) {
    val context = LocalContext.current
    val isPayPush = message.talerUri.startsWith("taler://pay-push")
    val isPayPull = message.talerUri.startsWith("taler://pay-pull")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (isPayPush || isPayPull) {
                Button(
                    onClick = { onNavigate(WalletDestination.HandleUri(message.talerUri), false) }
                ) {
                    Text(if (isPayPush) "Accept payment" else "Pay invoice")
                }
            } else {
                Text(message.talerUri, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "From: ${message.originMailboxBaseUrl}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Received: ${message.downloadedAt.ms.toAbsoluteTime(context)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = { onDelete(message) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
