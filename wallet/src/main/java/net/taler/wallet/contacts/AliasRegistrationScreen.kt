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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.mailbox.MailboxResult
import net.taler.wallet.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasRegistrationScreen(
    model: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    var aliasType by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var durationMonths by remember { mutableStateOf("12") }
    var solution by remember { mutableStateOf("") }
    val taldirBaseUri = "https://taldir.gnunet.org"

    val mailboxState by model.mailboxManager.mailbox.collectAsState()
    val registerResult by model.contactManager.registerResult.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            model.contactManager.resetRegistration()
        }
    }

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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val mailboxAddress = (mailboxState as? MailboxResult.Success)?.let {
                "${it.config.mailboxBaseUrl}/${it.config.hAddress}"
            }

            if (registerResult is RegisterResult.Idle || registerResult is RegisterResult.Loading || registerResult is RegisterResult.Error) {
                OutlinedTextField(
                    value = aliasType,
                    onValueChange = { aliasType = it },
                    label = { Text(stringResource(R.string.alias_registration_alias_type)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    enabled = registerResult !is RegisterResult.Loading
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.alias_registration_alias)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    enabled = registerResult !is RegisterResult.Loading
                )
                OutlinedTextField(
                    value = durationMonths,
                    onValueChange = { durationMonths = it },
                    label = { Text(stringResource(R.string.alias_registration_duration)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    enabled = registerResult !is RegisterResult.Loading
                )

                Button(
                    onClick = {
                        mailboxAddress?.let { targetUri ->
                            model.contactManager.taldirRegister(
                                alias = alias,
                                aliasType = aliasType,
                                taldirBaseUri = taldirBaseUri,
                                targetUri = targetUri,
                                durationMonths = durationMonths.toIntOrNull() ?: 12
                            )
                        }
                    },
                    enabled = aliasType.isNotBlank() && alias.isNotBlank() && mailboxAddress != null && registerResult !is RegisterResult.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.alias_registration_button))
                }
            }

            when (val res = registerResult) {
                is RegisterResult.Loading -> {
                    Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 8.dp))
                }
                is RegisterResult.Error -> {
                    Text(
                        text = stringResource(R.string.alias_registration_error, res.error.userFacingMsg),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is RegisterResult.Success -> {
                    if (res.validFor != null) {
                        Text(
                            text = stringResource(R.string.alias_registration_exists),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = solution,
                            onValueChange = { solution = it },
                            label = { Text(stringResource(R.string.alias_registration_solution)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = {
                                mailboxAddress?.let { targetUri ->
                                    model.contactManager.completeTaldirRegister(
                                        alias = alias,
                                        aliasType = aliasType,
                                        taldirBaseUri = taldirBaseUri,
                                        targetUri = targetUri,
                                        solution = solution
                                    )
                                }
                            },
                            enabled = solution.isNotBlank() && mailboxAddress != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
                is RegisterResult.CompletionLoading -> {
                    Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 8.dp))
                }
                is RegisterResult.CompletionSuccess -> {
                    Text(
                        text = stringResource(R.string.alias_registration_success),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
                else -> {}
            }

            if (mailboxAddress == null) {
                Text(
                    text = if (mailboxState is MailboxResult.Loading) "Loading mailbox..." else "Mailbox not configured",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
