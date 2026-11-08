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

package net.taler.wallet.peer

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.compose.RecipientAvatar
import net.taler.wallet.link.ReturnIntentSender
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit

/**
 * Die URI, ueber die der Messenger die Nachricht zu [tx] wiederfindet: nur
 * fuer selbst erstellte Peer-Zahlungen, die per compose-send verschickt
 * wurden.
 */
fun messengerTalerUri(tx: Transaction): String? = when (tx) {
    is TransactionPeerPushDebit -> tx.talerUri
    is TransactionPeerPullCredit -> tx.talerUri
    else -> null
}?.takeIf { it.isNotBlank() }

/**
 * Zeile "Signal-Logo, Avatar, Name" fuer Transaktionen, die ueber einen
 * Messenger-Link erstellt wurden (siehe [ContactStore]). Ohne gespeicherten
 * Kontext wird nichts angezeigt. Mit [talerUri] ist die Zeile ein Link: ein
 * Tipp oeffnet im Messenger den Chat an der Zahlungsnachricht, der Messenger
 * fragt dabei den aktuellen Status ab ([ReturnIntentSender.openInMessenger]).
 */
@Composable
fun TransactionContactLine(
    transactionId: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 20.dp,
    talerUri: String? = null,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val info = remember(transactionId) { ContactStore(context).get(transactionId) } ?: return
    // Name stammt vom Remote-Kontakt (angreifer-kontrolliert): eine Zeile, Ellipsis.
    val name = info.displayName.filter { it.isDefined() && !it.isISOControl() }
    val appName = info.senderAppName ?: "Signal"
    val messengerAvailable = remember(info.returnUri) {
        ReturnIntentSender.canOpenInMessenger(context, info.returnUri)
    }
    val canOpen = talerUri != null && enabled && messengerAvailable
    val openLabel = stringResource(R.string.transaction_contact_open_chat, appName)
    Row(
        if (canOpen && talerUri != null) {
            modifier.clickable(onClickLabel = openLabel, role = Role.Button) {
                ReturnIntentSender.openInMessenger(context, info.returnUri, talerUri)
            }
        } else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painterResource(R.drawable.ic_signal_logo),
            contentDescription = info.senderAppName,
            modifier = Modifier.size(avatarSize * 0.8f),
        )
        RecipientAvatar(
            avatarUri = info.avatarFile?.let { Uri.fromFile(it).toString() },
            isGroup = info.isGroup,
            size = avatarSize,
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (canOpen) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(avatarSize * 0.7f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
