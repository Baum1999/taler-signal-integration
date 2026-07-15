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

package net.taler.wallet.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.transactions.TransactionStateFilter.Nonfinal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    model: MainViewModel,
    sendUri: String? = null,
    scopeInfo: ScopeInfo? = null,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val contactManager = model.contactManager
    val contactResult by contactManager.contacts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var contactToConfirm by remember { mutableStateOf<ContactEntry?>(null) }

    LaunchedEffect(Unit) {
        contactManager.loadContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (sendUri != null) "Send to Contact" else "Contacts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            when (val res = contactResult) {
                is ContactResult.Loading -> Text("Loading...", modifier = Modifier.padding(16.dp))
                is ContactResult.Error -> Text("Error: ${res.error}", modifier = Modifier.padding(16.dp))
                is ContactResult.Success -> {
                    val filteredContacts = res.contacts.filter { contact ->
                        contact.petname.fuzzyMatches(searchQuery) ||
                                contact.alias.fuzzyMatches(searchQuery) ||
                                contact.aliasType.fuzzyMatches(searchQuery)
                    }
                    ContactList(
                        contacts = filteredContacts,
                        onDelete = { contact -> contactManager.deleteContact(contact) },
                        onSelect = if (sendUri != null) { contact ->
                            contactToConfirm = contact
                        } else null
                    )
                }
            }
        }
    }

    contactToConfirm?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToConfirm = null },
            title = { Text("Send to ${contact.petname}") },
            text = { Text("Are you sure you want to send this to ${contact.petname}?") },
            confirmButton = {
                TextButton(onClick = {
                    model.mailboxManager.sendMessage(contact, sendUri!!)
                    scopeInfo?.let {
                        model.showTransactions(it, Nonfinal)
                    }
                    onNavigate(WalletDestination.Main, true)
                    contactToConfirm = null
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { alias, aliasType, mailboxBaseUri, mailboxAddress, source, petname ->
                contactManager.addContact(alias, aliasType, mailboxBaseUri, mailboxAddress, source, petname)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String) -> Unit,
) {
    var alias by remember { mutableStateOf("") }
    var aliasType by remember { mutableStateOf("") }
    var mailboxBaseUri by remember { mutableStateOf("") }
    var mailboxAddress by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var petname by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column {
                OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") })
                OutlinedTextField(value = aliasType, onValueChange = { aliasType = it }, label = { Text("Alias Type") })
                OutlinedTextField(value = mailboxBaseUri, onValueChange = { mailboxBaseUri = it }, label = { Text("Mailbox Base URI") })
                OutlinedTextField(value = mailboxAddress, onValueChange = { mailboxAddress = it }, label = { Text("Mailbox Address") })
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source") })
                OutlinedTextField(value = petname, onValueChange = { petname = it }, label = { Text("Petname") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(alias, aliasType, mailboxBaseUri, mailboxAddress, source, petname) },
                enabled = alias.isNotBlank() && petname.isNotBlank()
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ContactList(
    contacts: List<ContactEntry>,
    onDelete: (ContactEntry) -> Unit,
    onSelect: ((ContactEntry) -> Unit)? = null,
) {
    if (contacts.isEmpty()) {
        Text("No contacts yet.", modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn {
            items(contacts) { contact ->
                ContactItem(contact, onDelete, onSelect)
                HorizontalDivider()
            }
        }
    }
}

private fun String.fuzzyMatches(query: String): Boolean {
    if (query.isEmpty()) return true
    val lowerQuery = query.lowercase()
    val lowerTarget = this.lowercase()
    var targetIdx = 0
    var queryIdx = 0
    while (targetIdx < lowerTarget.length && queryIdx < lowerQuery.length) {
        if (lowerTarget[targetIdx] == lowerQuery[queryIdx]) {
            queryIdx++
        }
        targetIdx++
    }
    return queryIdx == lowerQuery.length
}

@Composable
fun ContactItem(
    contact: ContactEntry,
    onDelete: (ContactEntry) -> Unit,
    onSelect: ((ContactEntry) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onSelect != null) { onSelect?.invoke(contact) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.petname, style = MaterialTheme.typography.titleMedium)
            Text(contact.alias, style = MaterialTheme.typography.bodyMedium)
            Text(contact.mailboxBaseUri, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onDelete(contact) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
