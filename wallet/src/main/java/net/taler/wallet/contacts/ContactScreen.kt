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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.net.toUri
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
    addContactUri: String? = null,
    scopeInfo: ScopeInfo? = null,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val contactManager = model.contactManager
    val contactResult by contactManager.contacts.collectAsState()
    val lookupResult by contactManager.lookupResult.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var contactToConfirm by remember { mutableStateOf<ContactEntry?>(null) }

    var initialAlias by remember { mutableStateOf("") }
    var initialAliasType by remember { mutableStateOf("email") }
    var initialMailboxBaseUri by remember { mutableStateOf("") }
    var initialMailboxAddress by remember { mutableStateOf("") }
    var initialSource by remember { mutableStateOf("") }
    var initialPetname by remember { mutableStateOf("") }

    fun fillFromUri(uriString: String) {
        val uri = uriString.toUri()
        val segments = uri.pathSegments
        var mailboxAddressTmp = uriString
        val aliasType = segments.getOrNull(0) ?: ""
        mailboxAddressTmp = mailboxAddressTmp.removePrefix("taler://add-contact/")
        mailboxAddressTmp = mailboxAddressTmp.removePrefix("""$aliasType/""")
        val alias = segments.getOrNull(1) ?: ""
        mailboxAddressTmp = mailboxAddressTmp.removePrefix("""$alias/""")
        val mailboxAddressFull = mailboxAddressTmp.split("?")[0]
        val mailboxAddress = mailboxAddressFull.split("/").last()
        val mailboxBaseUri = buildString {
            append("https://")
            append(mailboxAddressFull.removeSuffix(mailboxAddress))
        }
        val petname = uri.getQueryParameter("petname") ?: ""

        initialAlias = alias
        initialAliasType = aliasType
        initialMailboxBaseUri = mailboxBaseUri
        initialMailboxAddress = mailboxAddress
        initialSource = uriString
        initialPetname = petname
        showAddDialog = true
    }

    LaunchedEffect(Unit) {
        contactManager.loadContacts()
        addContactUri?.let { fillFromUri(it) }
    }

    LaunchedEffect(lookupResult) {
        if (lookupResult is LookupResult.Success) {
            val uri = (lookupResult as LookupResult.Success).targetUri
            contactManager.resetLookup()
            fillFromUri(uri)
        }
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
            initialAlias = initialAlias,
            initialAliasType = initialAliasType,
            initialMailboxBaseUri = initialMailboxBaseUri,
            initialMailboxAddress = initialMailboxAddress,
            initialSource = initialSource,
            initialPetname = initialPetname,
            onDismiss = {
                showAddDialog = false
                contactManager.resetLookup()
                initialAlias = ""
                initialAliasType = "email"
                initialMailboxBaseUri = ""
                initialMailboxAddress = ""
                initialSource = ""
                initialPetname = ""
            },
            onAdd = { alias, aliasType, mailboxBaseUri, mailboxAddress, source, petname ->
                contactManager.addContact(alias, aliasType, mailboxBaseUri, mailboxAddress, source, petname)
                showAddDialog = false
            },
            onLookup = { alias, aliasType ->
                contactManager.taldirLookupAlias(alias, aliasType)
            },
            lookupResult = lookupResult,
        )
    }
}

@Composable
fun AddContactDialog(
    initialAlias: String = "",
    initialAliasType: String = "email",
    initialMailboxBaseUri: String = "",
    initialMailboxAddress: String = "",
    initialSource: String = "",
    initialPetname: String = "",
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String) -> Unit,
    onLookup: (String, String) -> Unit,
    lookupResult: LookupResult,
) {
    var alias by remember { mutableStateOf(initialAlias) }
    var aliasType by remember { mutableStateOf(initialAliasType) }
    var mailboxBaseUri by remember { mutableStateOf(initialMailboxBaseUri) }
    var mailboxAddress by remember { mutableStateOf(initialMailboxAddress) }
    var source by remember { mutableStateOf(initialSource) }
    var petname by remember { mutableStateOf(initialPetname) }

    LaunchedEffect(initialAlias) { alias = initialAlias }
    LaunchedEffect(initialAliasType) { aliasType = initialAliasType }
    LaunchedEffect(initialMailboxBaseUri) { mailboxBaseUri = initialMailboxBaseUri }
    LaunchedEffect(initialMailboxAddress) { mailboxAddress = initialMailboxAddress }
    LaunchedEffect(initialSource) { source = initialSource }
    LaunchedEffect(initialPetname) { petname = initialPetname }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = petname,
                    onValueChange = { petname = it },
                    label = { Text("Petname") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = aliasType,
                    onValueChange = { aliasType = it },
                    label = { Text("Alias Type") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                if (lookupResult is LookupResult.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                } else {
                    Button(
                        onClick = { onLookup(alias, aliasType) },
                        enabled = alias.isNotBlank() && aliasType.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text("Lookup with Taldir")
                    }
                }

                if (lookupResult is LookupResult.Error) {
                    Text(
                        text = "Lookup failed: ${lookupResult.error.userFacingMsg}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(value = mailboxBaseUri, onValueChange = { mailboxBaseUri = it }, label = { Text("Mailbox Base URI") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                OutlinedTextField(value = mailboxAddress, onValueChange = { mailboxAddress = it }, label = { Text("Mailbox Address") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source") }, modifier = Modifier.fillMaxWidth())
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
