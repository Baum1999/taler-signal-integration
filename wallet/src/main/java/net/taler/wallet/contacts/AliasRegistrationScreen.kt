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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.mailbox.MailboxConfiguration
import net.taler.wallet.mailbox.MailboxResult
import net.taler.wallet.main.MainViewModel

@Composable
fun AliasRegistrationScreen(
    model: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val mailboxState by model.mailboxManager.mailbox.collectAsState()
    val registerResult by model.contactManager.registerResult.collectAsState()
    val contactsResult by model.contactManager.contacts.collectAsState()
    val aliasStatuses by model.contactManager.aliasStatuses.collectAsState()

    var showAddAliasDialog by remember { mutableStateOf(false) }
    var activeRegistrationAlias by remember { mutableStateOf<ContactEntry?>(null) }
    var isUnlinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        model.contactManager.loadContacts()
    }

    val mailboxConfig = (mailboxState as? MailboxResult.Success)?.config

    LaunchedEffect(contactsResult, mailboxConfig) {
        if (contactsResult is ContactResult.Success && mailboxConfig != null) {
            val userAliases = (contactsResult as ContactResult.Success).contacts.filter { it.self }
            userAliases.forEach { contact ->
                model.contactManager.checkAliasRegistration(
                    alias = contact.alias,
                    aliasType = contact.aliasType,
                    currentMailboxBaseUrl = mailboxConfig.mailboxBaseUrl,
                    currentMailboxAddress = mailboxConfig.hAddress,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            model.contactManager.resetRegistration()
        }
    }

    val userAliases = (contactsResult as? ContactResult.Success)?.contacts?.filter { it.self } ?: emptyList()

    AliasRegistrationContent(
        userAliases = userAliases,
        mailboxConfig = mailboxConfig,
        aliasStatuses = aliasStatuses,
        registerResult = registerResult,
        activeRegistrationAlias = activeRegistrationAlias,
        isUnlinking = isUnlinking,
        onNavigateBack = onNavigateBack,
        onAddAliasClick = { showAddAliasDialog = true },
        onStartRegister = { contact ->
            activeRegistrationAlias = contact
            isUnlinking = false
            if (mailboxConfig != null) {
                model.contactManager.taldirRegister(
                    alias = contact.alias,
                    aliasType = contact.aliasType,
                    taldirBaseUri = "https://taldir.gnunet.org",
                    mailboxBaseUrl = mailboxConfig.mailboxBaseUrl,
                    mailboxAddress = mailboxConfig.hAddress,
                    durationMonths = 12,
                )
            }
        },
        onCompleteRegister = { solution ->
            val contact = activeRegistrationAlias
            if (contact != null) {
                if (isUnlinking) {
                    model.contactManager.completeTaldirRegister(
                        alias = contact.alias,
                        aliasType = contact.aliasType,
                        taldirBaseUri = "https://taldir.gnunet.org",
                        mailboxBaseUrl = "",
                        mailboxAddress = "",
                        solution = solution,
                    )
                } else if (mailboxConfig != null) {
                    model.contactManager.completeTaldirRegister(
                        alias = contact.alias,
                        aliasType = contact.aliasType,
                        taldirBaseUri = "https://taldir.gnunet.org",
                        mailboxBaseUrl = mailboxConfig.mailboxBaseUrl,
                        mailboxAddress = mailboxConfig.hAddress,
                        solution = solution,
                    )
                }
            }
        },
        onRecoverRegister = {
            val contact = activeRegistrationAlias
            if (contact != null) {
                if (isUnlinking) {
                    model.contactManager.recoverRegistration(
                        alias = contact.alias,
                        aliasType = contact.aliasType,
                        taldirBaseUri = "https://taldir.gnunet.org",
                        expectedTargetUri = "",
                    )
                } else if (mailboxConfig != null) {
                    val expectedTargetUri = "${mailboxConfig.mailboxBaseUrl.trimEnd('/')}/${mailboxConfig.hAddress}"
                    model.contactManager.recoverRegistration(
                        alias = contact.alias,
                        aliasType = contact.aliasType,
                        taldirBaseUri = "https://taldir.gnunet.org",
                        expectedTargetUri = expectedTargetUri,
                    )
                }
            }
        },
        onCancelRegister = {
            activeRegistrationAlias = null
            isUnlinking = false
            model.contactManager.resetRegistration()
        },
        onDeleteAlias = { contact ->
            val key = "${contact.aliasType}:${contact.alias}"
            val isRegistered = aliasStatuses[key] is AliasRegistrationStatus.Registered
            if (isRegistered) {
                activeRegistrationAlias = contact
                isUnlinking = true
                model.contactManager.taldirDeleteAlias(
                    alias = contact.alias,
                    aliasType = contact.aliasType,
                    taldirBaseUri = "https://taldir.gnunet.org",
                    durationMonths = 12,
                )
            } else {
                model.contactManager.deleteContact(contact)
            }
        },
        onRegistrationCompleted = {
            activeRegistrationAlias?.let { contact ->
                if (isUnlinking) {
                    model.contactManager.deleteContact(contact)
                } else if (mailboxConfig != null) {
                    model.contactManager.checkAliasRegistration(
                        alias = contact.alias,
                        aliasType = contact.aliasType,
                        currentMailboxBaseUrl = mailboxConfig.mailboxBaseUrl,
                        currentMailboxAddress = mailboxConfig.hAddress,
                    )
                }
            }
            activeRegistrationAlias = null
            isUnlinking = false
            model.contactManager.resetRegistration()
        }
    )

    if (showAddAliasDialog && mailboxConfig != null) {
        AddAliasDialog(
            onDismiss = { showAddAliasDialog = false },
            onConfirm = { alias, aliasType, nickname ->
                model.contactManager.addContact(
                    alias = alias,
                    aliasType = aliasType,
                    mailboxBaseUri = mailboxConfig.mailboxBaseUrl,
                    mailboxAddress = mailboxConfig.hAddress,
                    source = "self",
                    petname = nickname,
                    self = true,
                )
                model.contactManager.checkAliasRegistration(
                    alias = alias,
                    aliasType = aliasType,
                    currentMailboxBaseUrl = mailboxConfig.mailboxBaseUrl,
                    currentMailboxAddress = mailboxConfig.hAddress,
                )
                showAddAliasDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasRegistrationContent(
    userAliases: List<ContactEntry>,
    mailboxConfig: MailboxConfiguration?,
    aliasStatuses: Map<String, AliasRegistrationStatus>,
    registerResult: RegisterResult,
    activeRegistrationAlias: ContactEntry?,
    isUnlinking: Boolean,
    onNavigateBack: () -> Unit,
    onAddAliasClick: () -> Unit,
    onStartRegister: (ContactEntry) -> Unit,
    onCompleteRegister: (solution: String) -> Unit,
    onRecoverRegister: () -> Unit,
    onCancelRegister: () -> Unit,
    onDeleteAlias: (ContactEntry) -> Unit,
    onRegistrationCompleted: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alias_registration_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAddAliasClick, enabled = mailboxConfig != null) {
                        Icon(Icons.Default.Add, contentDescription = "Add Alias")
                    }
                }
            )
        },
        floatingActionButton = {
            if (mailboxConfig != null) {
                FloatingActionButton(onClick = onAddAliasClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add Alias")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (mailboxConfig == null) {
                Text(
                    text = "Mailbox not configured or loading...",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else if (userAliases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No aliases added yet. Tap + to add an alias.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(userAliases) { contact ->
                        val key = "${contact.aliasType}:${contact.alias}"
                        val status = aliasStatuses[key] ?: AliasRegistrationStatus.Checking
                        UserAliasCard(
                            contact = contact,
                            status = status,
                            isRegistering = activeRegistrationAlias?.alias == contact.alias && activeRegistrationAlias?.aliasType == contact.aliasType,
                            onRegister = { onStartRegister(contact) },
                            onDelete = { onDeleteAlias(contact) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (activeRegistrationAlias != null && registerResult !is RegisterResult.Idle) {
        RegistrationChallengeDialog(
            contact = activeRegistrationAlias,
            isUnlinking = isUnlinking,
            registerResult = registerResult,
            onComplete = onCompleteRegister,
            onRecover = onRecoverRegister,
            onCancel = onCancelRegister,
            onDismissSuccess = onRegistrationCompleted,
        )
    }
}

@Composable
fun UserAliasCard(
    contact: ContactEntry,
    status: AliasRegistrationStatus,
    isRegistering: Boolean,
    onRegister: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.alias,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (contact.petname.isNotBlank()) {
                        Text(
                            text = contact.petname,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Type: ${contact.aliasType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Alias")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    is AliasRegistrationStatus.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = " Checking status...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is AliasRegistrationStatus.Registered -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = " Registered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    is AliasRegistrationStatus.NotRegistered, is AliasRegistrationStatus.Mismatched -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (status is AliasRegistrationStatus.Mismatched) " Mismatched Target" else " Not Registered",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onRegister,
                            enabled = !isRegistering,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(stringResource(R.string.alias_registration_button))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAliasDialog(
    onDismiss: () -> Unit,
    onConfirm: (alias: String, aliasType: String, nickname: String) -> Unit,
) {
    var aliasType by remember { mutableStateOf("email") }
    var aliasTypeExpanded by remember { mutableStateOf(false) }
    var alias by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    val aliasTypeOptions = listOf("email", "phone")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Alias") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = aliasTypeExpanded,
                    onExpandedChange = { aliasTypeExpanded = !aliasTypeExpanded },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = aliasType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.alias_registration_alias_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aliasTypeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = aliasTypeExpanded,
                        onDismissRequest = { aliasTypeExpanded = false }
                    ) {
                        aliasTypeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    aliasType = option
                                    aliasTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.alias_registration_alias)) },
                    placeholder = {
                        Text(if (aliasType == "email") "user@example.com" else "+1234567890")
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname / Petname") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(alias.trim(), aliasType, nickname.trim()) },
                enabled = alias.isNotBlank()
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
fun RegistrationChallengeDialog(
    contact: ContactEntry,
    isUnlinking: Boolean = false,
    registerResult: RegisterResult,
    onComplete: (solution: String) -> Unit,
    onRecover: () -> Unit,
    onCancel: () -> Unit,
    onDismissSuccess: () -> Unit,
) {
    var solution by remember { mutableStateOf("") }
    val titleText = if (isUnlinking) "Unlink ${contact.alias}" else "Register ${contact.alias}"
    val actionProgressText = if (isUnlinking) " Unlinking alias..." else " Registering alias..."
    val promptText = if (isUnlinking) {
        "Enter the challenge solution code sent to unlink ${contact.alias}:"
    } else {
        "Enter the challenge solution code sent for ${contact.alias}:"
    }
    val successText = if (isUnlinking) "Unlinking successful!" else stringResource(R.string.alias_registration_success)

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(titleText) },
        text = {
            Column {
                when (val res = registerResult) {
                    is RegisterResult.Loading, is RegisterResult.CompletionLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(actionProgressText, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    is RegisterResult.Error -> {
                        Text(
                            text = stringResource(R.string.alias_registration_error, res.error.userFacingMsg),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextButton(
                            onClick = onRecover,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.alias_registration_out_of_band))
                        }
                    }
                    is RegisterResult.Success -> {
                        Text(
                            text = promptText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = solution,
                            onValueChange = { solution = it },
                            label = { Text(stringResource(R.string.alias_registration_solution)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        TextButton(
                            onClick = onRecover,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.alias_registration_out_of_band))
                        }
                    }
                    is RegisterResult.CompletionSuccess -> {
                        Text(
                            text = successText,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (registerResult is RegisterResult.Success) {
                Button(
                    onClick = { onComplete(solution.trim()) },
                    enabled = solution.isNotBlank()
                ) {
                    Text(stringResource(R.string.ok))
                }
            } else if (registerResult is RegisterResult.CompletionSuccess) {
                Button(onClick = onDismissSuccess) {
                    Text(stringResource(R.string.ok))
                }
            }
        },
        dismissButton = {
            if (registerResult !is RegisterResult.CompletionSuccess) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Preview
@Composable
fun AliasRegistrationContentPreview() {
    TalerSurface {
        AliasRegistrationContent(
            userAliases = listOf(
                ContactEntry(
                    alias = "alice@example.com",
                    aliasType = "email",
                    mailboxBaseUrl = "https://mailbox.taler.net",
                    mailboxAddress = "alice",
                    source = "self",
                    petname = "Personal Email",
                    self = true,
                )
            ),
            mailboxConfig = MailboxConfiguration(
                mailboxBaseUrl = "https://mailbox.taler.net",
                privateKey = "",
                privateEncryptionKey = "",
                hAddress = "alice",
                expiration = net.taler.common.Timestamp.fromMillis(0)
            ),
            aliasStatuses = mapOf("email:alice@example.com" to AliasRegistrationStatus.Registered("https://mailbox.taler.net/alice")),
            registerResult = RegisterResult.Idle,
            activeRegistrationAlias = null,
            isUnlinking = false,
            onNavigateBack = {},
            onAddAliasClick = {},
            onStartRegister = {},
            onCompleteRegister = {},
            onRecoverRegister = {},
            onCancelRegister = {},
            onDeleteAlias = {},
            onRegistrationCompleted = {},
        )
    }
}
