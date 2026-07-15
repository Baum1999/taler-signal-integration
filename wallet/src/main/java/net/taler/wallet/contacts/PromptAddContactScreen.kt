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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptAddContactScreen(
    model: MainViewModel,
    uriString: String,
    onNavigate: (WalletDestination, Boolean) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uri = uriString.toUri()
    val segments = uri.pathSegments
    var mailboxAddressTmp = uriString;
    val aliasType = segments.getOrNull(0) ?: ""
    mailboxAddressTmp = mailboxAddressTmp.removePrefix("taler://add-contact/")
    mailboxAddressTmp = mailboxAddressTmp.removePrefix("""$aliasType/""")
    var alias = segments.getOrNull(1) ?: ""
    mailboxAddressTmp = mailboxAddressTmp.removePrefix("""$alias/""")
    val mailboxAddressFull = mailboxAddressTmp.split("?")[0]
    val mailboxAddress = mailboxAddressFull.split("/").last()
    val mailboxBaseUri = buildString {
        append("https://")
        append(mailboxAddressFull.removeSuffix(mailboxAddress))
    }
    var petname by remember { mutableStateOf(uri.getQueryParameter("petname") ?: "") }
    val source = uriString

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_add_title)) },
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
            Text(
                text = stringResource(R.string.contact_add_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = petname,
                onValueChange = { petname = it },
                label = { Text(stringResource(R.string.contact_petname)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = alias,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.contact_alias)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = mailboxAddress,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.contact_mailbox_address)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = mailboxBaseUri,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.contact_mailbox_base_uri)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    model.contactManager.addContact(
                        alias = alias,
                        aliasType = aliasType,
                        mailboxBaseUri = mailboxBaseUri,
                        mailboxAddress = mailboxAddress,
                        source = source,
                        petname = petname
                    )
                    onNavigate(WalletDestination.Contacts(), true)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = petname.isNotBlank()
            ) {
                Text(stringResource(R.string.contact_add_button))
            }
        }
    }
}
