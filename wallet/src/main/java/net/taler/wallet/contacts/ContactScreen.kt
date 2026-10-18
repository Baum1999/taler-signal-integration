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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.main.MainViewModel

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
    var showNicknameDialog by remember { mutableStateOf(false) }
    var pendingContact by remember { mutableStateOf<PendingContact?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var contactToConfirm by remember { mutableStateOf<ContactEntry?>(null) }

    var initialAlias by remember { mutableStateOf("") }
    var initialAliasType by remember { mutableStateOf("email") }
    var initialMailboxBaseUri by remember { mutableStateOf("") }
    var initialMailboxAddress by remember { mutableStateOf("") }
    var initialSource by remember { mutableStateOf("") }
    var initialPetname by remember { mutableStateOf("") }

    fun fillFromUri(uriString: String, overriddenSource: String? = null) {
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
        val mailboxBaseUri = if (mailboxAddressFull.startsWith("http://") || mailboxAddressFull.startsWith("https://")) {
            mailboxAddressFull.removeSuffix(mailboxAddress)
        } else {
            buildString {
                append("https://")
                append(mailboxAddressFull.removeSuffix(mailboxAddress))
            }
        }
        val petname = uri.getQueryParameter("petname") ?: ""
        val source = overriddenSource ?: uri.getQueryParameter("source") ?: "QR"

        initialAlias = alias
        initialAliasType = aliasType
        initialMailboxBaseUri = mailboxBaseUri
        initialMailboxAddress = mailboxAddress
        initialSource = source
        initialPetname = petname
        showAddDialog = true
    }

    LaunchedEffect(Unit) {
        contactManager.loadContacts()
        addContactUri?.let { fillFromUri(it) }
    }

    LaunchedEffect(lookupResult) {
        if (lookupResult is LookupResult.Success) {
            val res = lookupResult as LookupResult.Success
            contactManager.resetLookup()
            fillFromUri(res.targetUri, res.source)
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
                        model.displayScope(it)
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
            onProceed = { alias, aliasType, mailboxBaseUri, mailboxAddress, source ->
                showAddDialog = false
                pendingContact = PendingContact(alias, aliasType, mailboxBaseUri, mailboxAddress, source)
                showNicknameDialog = true
            },
            onLookup = { alias, aliasType ->
                contactManager.taldirLookupAlias(alias, aliasType)
            },
            lookupResult = lookupResult,
            existingContacts = (contactResult as? ContactResult.Success)?.contacts ?: emptyList()
        )
    }

    if (showNicknameDialog && pendingContact != null) {
        val pending = pendingContact!!
        var nickname by remember { mutableStateOf(initialPetname) }

        AlertDialog(
            onDismissRequest = {
                showNicknameDialog = false
                pendingContact = null
            },
            title = { Text(stringResource(R.string.contact_petname)) },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.contact_petname)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        contactManager.addContact(
                            alias = pending.alias,
                            aliasType = pending.aliasType,
                            mailboxBaseUri = pending.mailboxBaseUri,
                            mailboxAddress = pending.mailboxAddress,
                            source = pending.source,
                            petname = nickname,
                        )
                        showNicknameDialog = false
                        pendingContact = null
                        contactManager.resetLookup()
                        initialAlias = ""
                        initialAliasType = "email"
                        initialMailboxBaseUri = ""
                        initialMailboxAddress = ""
                        initialSource = ""
                        initialPetname = ""
                    },
                    enabled = nickname.isNotBlank()
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNicknameDialog = false
                    pendingContact = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

data class PendingContact(
    val alias: String,
    val aliasType: String,
    val mailboxBaseUri: String,
    val mailboxAddress: String,
    val source: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactDialog(
    initialAlias: String = "",
    initialAliasType: String = "email",
    initialMailboxBaseUri: String = "",
    initialMailboxAddress: String = "",
    initialSource: String = "",
    onDismiss: () -> Unit,
    onProceed: (String, String, String, String, String) -> Unit,
    onLookup: (String, String) -> Unit,
    lookupResult: LookupResult,
    existingContacts: List<ContactEntry> = emptyList(),
) {
    var alias by remember { mutableStateOf(initialAlias) }
    var aliasType by remember { mutableStateOf(initialAliasType) }
    var aliasTypeExpanded by remember { mutableStateOf(false) }
    var mailbox by remember {
        mutableStateOf(if (initialMailboxBaseUri.isEmpty()) "" else "$initialMailboxBaseUri$initialMailboxAddress")
    }
    var source by remember { mutableStateOf(initialSource) }

    val aliasTypeOptions = listOf(
        stringResource(R.string.alias_registration_alias_email),
        stringResource(R.string.alias_registration_alias_phone),
    )

    val isDuplicate = existingContacts.any { it.alias == alias && it.aliasType == aliasType }

    LaunchedEffect(initialAlias) { alias = initialAlias }
    LaunchedEffect(initialAliasType) { aliasType = initialAliasType }
    LaunchedEffect(initialMailboxBaseUri, initialMailboxAddress) {
        mailbox = if (initialMailboxBaseUri.isEmpty()) "" else "$initialMailboxBaseUri$initialMailboxAddress"
    }
    LaunchedEffect(initialSource) { source = initialSource }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias") },
                    placeholder = {
                        Text(
                            if (aliasType == stringResource(R.string.alias_registration_alias_email)) {
                                stringResource(R.string.alias_registration_alias_hint_email)
                            } else if (aliasType == stringResource(R.string.alias_registration_alias_phone)) {
                                stringResource(R.string.alias_registration_alias_hint_phone)
                            } else ""
                        )
                    },
                    isError = isDuplicate,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = aliasTypeExpanded,
                    onExpandedChange = { aliasTypeExpanded = !aliasTypeExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = aliasType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alias Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aliasTypeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        isError = isDuplicate,
                    )
                    ExposedDropdownMenu(
                        expanded = aliasTypeExpanded,
                        onDismissRequest = { aliasTypeExpanded = false }
                    ) {
                        aliasTypeOptions.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    aliasType = selectionOption
                                    aliasTypeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                if (isDuplicate) {
                    Text(
                        text = "A contact with this alias and type already exists.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

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

                OutlinedTextField(
                    value = mailbox,
                    onValueChange = { mailbox = it },
                    label = { Text("Mailbox") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mailboxParts = mailbox.split("/")
                    val base = if (mailboxParts.size > 1) {
                        mailboxParts.dropLast(1).joinToString("/", postfix = "/")
                    } else ""
                    val addr = mailboxParts.last()
                    val finalSource = source.ifBlank { "local" }
                    onProceed(alias, aliasType, base, addr, finalSource)
                },
                enabled = alias.isNotBlank() && mailbox.isNotBlank() && !isDuplicate
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

@Preview
@Composable
fun ContactItemPreview() {
    TalerSurface {
        ContactItem(
            contact = ContactEntry(
                alias = "alice@taler.net",
                aliasType = "email",
                mailboxBaseUri = "https://mailbox.taler.net/",
                mailboxAddress = "alice",
                source = "manual",
                petname = "Alice",
            ),
            onDelete = {},
            onSelect = null
        )
    }
}

@Preview
@Composable
fun ContactListPreview() {
    TalerSurface {
        ContactList(
            contacts = listOf(
                ContactEntry(
                    alias = "alice@taler.net",
                    aliasType = "email",
                    mailboxBaseUri = "https://mailbox.taler.net/",
                    mailboxAddress = "alice",
                    source = "manual",
                    petname = "Alice",
                ),
                ContactEntry(
                    alias = "bob@taler.net",
                    aliasType = "email",
                    mailboxBaseUri = "https://mailbox.taler.net/",
                    mailboxAddress = "bob",
                    source = "manual",
                    petname = "Bob",
                ),
            ),
            onDelete = {}
        )
    }
}

@Preview
@Composable
fun AddContactDialogPreview() {
    TalerSurface {
        AddContactDialog(
            onDismiss = {},
            onProceed = { _, _, _, _, _ -> },
            onLookup = { _, _ -> },
            lookupResult = LookupResult.Idle
        )
    }
}
