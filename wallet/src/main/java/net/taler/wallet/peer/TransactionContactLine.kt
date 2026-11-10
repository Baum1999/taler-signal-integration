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

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import net.taler.wallet.R
import net.taler.wallet.compose.RecipientAvatar
import net.taler.wallet.link.OwnUriTracker
import net.taler.wallet.link.ReturnIntentSender
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPullDebit
import net.taler.wallet.transactions.TransactionPeerPushCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit
import java.util.concurrent.ConcurrentHashMap

/**
 * Peer-Zahlung, die ueber einen Messenger-Chat laufen kann: transactionId und
 * die URI, soweit wallet-core sie noch liefert. Selbst erstellt (compose-send):
 * bei pay-push nur, solange die Zahlung angenommen werden kann. Eingehend
 * (per Aktionslink angenommen): nie, der Hash steht dann im [ContactStore].
 * Sonst null.
 */
internal fun messengerPeerPayment(tx: Transaction): Pair<String, String?>? = when (tx) {
    is TransactionPeerPushDebit -> tx.transactionId to tx.talerUri
    is TransactionPeerPullCredit -> tx.transactionId to tx.talerUri
    is TransactionPeerPushCredit -> tx.transactionId to null
    is TransactionPeerPullDebit -> tx.transactionId to null
    else -> null
}

/**
 * Hashes, ueber die der Messenger die Nachricht zu [payments] wiederfindet
 * (link-protokoll.md Abschnitt 12): SHA-256 der URI wie im [OwnUriTracker].
 * Nach dem Annehmen (oder Ablauf, Abbruch) liefert wallet-core keine URI
 * mehr, dann kommt der Hash aus [hashesFor] (OwnUriTracker seit compose-send,
 * ContactStore fuer angenommene eingehende Zahlungen). Reihenfolge wie
 * [payments], ohne Duplikate, hoechstens [ReturnIntentSender.MAX_URI_HASHES].
 */
internal fun messengerUriHashes(
    payments: List<Pair<String, String?>>,
    hashesFor: (transactionId: String) -> List<String>,
): List<String> = payments
    .flatMap { (transactionId, talerUri) ->
        listOfNotNull(talerUri?.takeIf { it.isNotBlank() }?.let { OwnUriTracker.keyFor(it) }) + hashesFor(transactionId)
    }
    .distinct()
    .take(ReturnIntentSender.MAX_URI_HASHES)

/** [messengerUriHashes] fuer die Peer-Zahlungen unter [transactions], z.B. den Anteilen eines Gruppen-Splits. */
@Composable
fun rememberMessengerUriHashes(transactions: List<Transaction>): List<String> {
    val context = LocalContext.current
    return remember(transactions) {
        val tracker = OwnUriTracker(context)
        val contacts = ContactStore(context)
        messengerUriHashes(transactions.mapNotNull(::messengerPeerPayment)) { transactionId ->
            tracker.hashesFor(transactionId) + listOfNotNull(contacts.uriHash(transactionId))
        }
    }
}

/** Name und Launcher-Icon eines installierten, geprueften Messengers. */
private class MessengerBadge(val label: String, val icon: ImageBitmap)

private val badgeCache = ConcurrentHashMap<String, MessengerBadge>()

/**
 * Name und Launcher-Icon des Messengers, an den [returnUri] zurueckspringt.
 * Nur fuer ein Ziel aus der Allowlist, das installiert und korrekt signiert
 * ist ([ReturnIntentSender.verifiedMessengerPackage]): beides kommt dann vom
 * System, nicht aus dem Link. Sonst null.
 */
private fun messengerBadge(context: Context, returnUri: String?, iconPx: Int): MessengerBadge? {
    val packageName = ReturnIntentSender.verifiedMessengerPackage(context, returnUri) ?: return null
    val key = "$packageName@$iconPx"
    badgeCache[key]?.let { return it }
    return runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        MessengerBadge(
            label = pm.getApplicationLabel(info).toString(),
            icon = pm.getApplicationIcon(info).toBitmap(iconPx, iconPx).asImageBitmap(),
        )
    }.getOrNull()?.also { badgeCache[key] = it }
}

/**
 * Zeile "Messenger-Logo, Avatar, Name" fuer Transaktionen, die ueber einen
 * Messenger-Link erstellt oder angenommen wurden (siehe [ContactStore]). Ohne gespeicherten
 * Kontext wird nichts angezeigt. Das Logo ist das Launcher-Icon des
 * Messengers aus dem gespeicherten Ruecksprungziel; ist der nicht pruefbar
 * (aeltere Transaktion, Messenger deinstalliert), das Signal-Logo fuer
 * Zahlungen aus Signal, sonst ein neutrales Chat-Symbol. Mit [uriHashes]
 * ([rememberMessengerUriHashes]) ist die Zeile ein Link: ein Tipp oeffnet im
 * Messenger den Chat an der Zahlungsnachricht, der Messenger fragt dabei den
 * aktuellen Status ab ([ReturnIntentSender.openInMessenger]).
 */
@Composable
fun TransactionContactLine(
    transactionId: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 20.dp,
    uriHashes: List<String> = emptyList(),
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val info = remember(transactionId) { ContactStore(context).get(transactionId) } ?: return
    // Name stammt vom Remote-Kontakt (angreifer-kontrolliert): eine Zeile, Ellipsis.
    val name = info.displayName.filter { it.isDefined() && !it.isISOControl() }
    val logoSize = avatarSize * 0.8f
    val logoPx = with(LocalDensity.current) { logoSize.roundToPx() }.coerceAtLeast(1)
    val badge = remember(info.returnUri, logoPx) { messengerBadge(context, info.returnUri, logoPx) }
    val appName = badge?.label ?: info.senderAppName ?: stringResource(R.string.transaction_contact_messenger)
    val messengerAvailable = remember(info.returnUri) {
        ReturnIntentSender.canOpenInMessenger(context, info.returnUri)
    }
    val canOpen = uriHashes.isNotEmpty() && enabled && messengerAvailable
    val openLabel = stringResource(R.string.transaction_contact_open_chat, appName)
    Row(
        if (canOpen) {
            modifier.clickable(onClickLabel = openLabel, role = Role.Button) {
                ReturnIntentSender.openInMessenger(context, info.returnUri, uriHashes)
            }
        } else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            badge != null -> Image(
                badge.icon,
                contentDescription = appName,
                modifier = Modifier.size(logoSize),
            )
            // Signal schickt senderAppName schon laenger mit, als es den ContactStore gibt.
            info.senderAppName == SIGNAL_APP_NAME -> Image(
                painterResource(R.drawable.ic_signal_logo),
                contentDescription = appName,
                modifier = Modifier.size(logoSize),
            )
            else -> Icon(
                Icons.AutoMirrored.Outlined.Chat,
                contentDescription = appName,
                modifier = Modifier.size(logoSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

private const val SIGNAL_APP_NAME = "Signal"
