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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.QrCodeParams
import net.taler.wallet.compose.QrCodeUriComposable
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
    val devMode by model.devMode.observeAsState(false)
    var showAddDialog by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var pendingContact by remember { mutableStateOf<PendingContact?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var contactToConfirm by remember { mutableStateOf<ContactEntry?>(null) }
    var contactForQr by remember { mutableStateOf<ContactEntry?>(null) }
    var contactToDelete by remember { mutableStateOf<ContactEntry?>(null) }
    var contactToEdit by remember { mutableStateOf<ContactEntry?>(null) }

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
                is ContactResult.Error -> ErrorComposable(
                    error = res.error,
                    devMode = devMode,
                    modifier = Modifier.weight(1f),
                    scrollable = true,
                    onRetry = { contactManager.loadContacts() },
                )
                is ContactResult.Success -> {
                    val filteredContacts = res.contacts.filter { contact ->
                        contact.petname.fuzzyMatches(searchQuery) ||
                                contact.alias.fuzzyMatches(searchQuery) ||
                                contact.aliasType.fuzzyMatches(searchQuery)
                    }
                    ContactList(
                        contacts = filteredContacts,
                        onDelete = { contact -> contactToDelete = contact },
                        onEditNickname = { contact -> contactToEdit = contact },
                        onShowQr = { contact -> contactForQr = contact },
                        onSelect = if (sendUri != null) { contact ->
                            contactToConfirm = contact
                        } else null
                    )
                }
            }
        }
    }

    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Delete Contact") },
            text = { Text("Are you sure you want to delete ${contact.petname.ifBlank { contact.alias }}?") },
            confirmButton = {
                TextButton(onClick = {
                    contactManager.deleteContact(contact)
                    contactToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.transactions_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    contactToEdit?.let { contact ->
        EditNicknameDialog(
            contact = contact,
            onDismiss = { contactToEdit = null },
            onSave = { newNickname ->
                contactManager.addContact(
                    alias = contact.alias,
                    aliasType = contact.aliasType,
                    mailboxBaseUri = contact.mailboxBaseUrl,
                    mailboxAddress = contact.mailboxAddress,
                    source = contact.source,
                    petname = newNickname,
                    isSelf = contact.isSelf,
                )
                contactToEdit = null
            }
        )
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

    contactForQr?.let { contact ->
        ShowContactQrDialog(
            contact = contact,
            onDismiss = { contactForQr = null }
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

@Composable
fun EditNicknameDialog(
    contact: ContactEntry,
    onDismiss: () -> Unit,
    onSave: (newNickname: String) -> Unit,
) {
    var nickname by remember { mutableStateOf(contact.petname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Nickname") },
        text = {
            Column {
                Text(
                    text = "Alias: ${contact.alias}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.contact_petname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nickname.trim()) },
                enabled = nickname.isNotBlank()
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
fun ShowContactQrDialog(
    contact: ContactEntry,
    onDismiss: () -> Unit,
) {
    val qrUri = contact.toAddContactUri()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contact.petname.ifBlank { contact.alias }) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QrCodeUriComposable(
                    qrData = qrUri,
                    clipboardLabel = "Contact QR Code",
                    params = QrCodeParams.Taler,
                    shareAsQrCode = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

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
fun SelfBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "You",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ContactGroupCard(
    targetUriKey: String,
    contactsInGroup: List<ContactEntry>,
    onDelete: (ContactEntry) -> Unit,
    onEditNickname: (ContactEntry) -> Unit,
    onShowQr: (ContactEntry) -> Unit,
    onSelect: ((ContactEntry) -> Unit)? = null,
) {
    val isSelfGroup = contactsInGroup.any { it.isSelf || it.source == "self" }

    val distinctPetnames = contactsInGroup
        .map { it.petname.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val primaryPetname = distinctPetnames.firstOrNull()
        ?: contactsInGroup.firstOrNull()?.alias
        ?: targetUriKey

    val otherPetnames = distinctPetnames.drop(1)

    val firstContact = contactsInGroup.firstOrNull()
    val displayTargetUri = if (firstContact != null && firstContact.mailboxBaseUrl.isNotBlank()) {
        val cleanBase = firstContact.mailboxBaseUrl.trimEnd('/')
        val addr = firstContact.mailboxAddress
        val shortAddr = if (addr.length > 5) "${addr.take(5)}…" else addr
        "$cleanBase/$shortAddr"
    } else {
        targetUriKey
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = primaryPetname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelfGroup) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SelfBadge()
                    }
                }
            }

            if (otherPetnames.isNotEmpty()) {
                Text(
                    text = "Also: ${otherPetnames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = displayTargetUri,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            contactsInGroup.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onSelect != null) { onSelect?.invoke(contact) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(contact.alias, style = MaterialTheme.typography.bodyMedium)
                            if (contact.isSelf || contact.source == "self") {
                                Spacer(modifier = Modifier.width(6.dp))
                                SelfBadge()
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Type: ${contact.aliasType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (contact.petname.isNotBlank() && contact.petname.trim() != primaryPetname) {
                                Text(
                                    text = " • ${contact.petname}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (onSelect != null) {
                        IconButton(onClick = { onSelect(contact) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message to Contact",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditNickname(contact) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Nickname")
                            }
                            IconButton(onClick = { onShowQr(contact) }) {
                                Icon(Icons.Default.QrCode, contentDescription = "Show QR Code")
                            }
                            IconButton(onClick = { onDelete(contact) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactList(
    contacts: List<ContactEntry>,
    onDelete: (ContactEntry) -> Unit,
    onEditNickname: (ContactEntry) -> Unit,
    onShowQr: (ContactEntry) -> Unit,
    onSelect: ((ContactEntry) -> Unit)? = null,
) {
    if (contacts.isEmpty()) {
        Text("No contacts yet.", modifier = Modifier.padding(16.dp))
    } else {
        val grouped = contacts.groupBy {
            val base = it.mailboxBaseUrl.trimEnd('/')
            if (base.isNotBlank() && it.mailboxAddress.isNotBlank()) {
                "$base/${it.mailboxAddress}"
            } else {
                it.alias
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(grouped.entries.toList()) { (targetUriKey, contactsInGroup) ->
                ContactGroupCard(
                    targetUriKey = targetUriKey,
                    contactsInGroup = contactsInGroup,
                    onDelete = onDelete,
                    onEditNickname = onEditNickname,
                    onShowQr = onShowQr,
                    onSelect = onSelect
                )
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
    onShowQr: (ContactEntry) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.petname, style = MaterialTheme.typography.titleMedium)
                if (contact.isSelf || contact.source == "self") {
                    Spacer(modifier = Modifier.width(6.dp))
                    SelfBadge()
                }
            }
            Text(contact.alias, style = MaterialTheme.typography.bodyMedium)
            Text(contact.mailboxBaseUri, style = MaterialTheme.typography.bodySmall)
        }
        if (onSelect != null) {
            IconButton(onClick = { onSelect(contact) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message to Contact",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShowQr(contact) }) {
                    Icon(Icons.Default.QrCode, contentDescription = "Show QR Code")
                }
                IconButton(onClick = { onDelete(contact) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
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
                mailboxBaseUrl = "https://mailbox.taler.net/",
                mailboxAddress = "alice",
                source = "manual",
                petname = "Alice",
                isSelf = true,
            ),
            onDelete = {},
            onShowQr = {},
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
                    mailboxBaseUrl = "https://mailbox.taler.net/",
                    mailboxAddress = "alice",
                    source = "manual",
                    petname = "Alice",
                    isSelf = true,
                ),
                ContactEntry(
                    alias = "bob@taler.net",
                    aliasType = "email",
                    mailboxBaseUrl = "https://mailbox.taler.net/",
                    mailboxAddress = "bob",
                    source = "manual",
                    petname = "Bob",
                ),
            ),
            onDelete = {},
            onEditNickname = {},
            onShowQr = {},
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
