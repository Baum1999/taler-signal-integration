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

package net.taler.wallet.link

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.launch
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit

/**
 * Eine Eingangs-URI samt der eigenen Transaktion, soweit diese Wallet sie
 * kennt. transactionId null: die URI gehoert keiner eigenen Transaktion, sie
 * bekommt `not_own` und wird nie angefasst. Betrag und Zweck stammen nur aus
 * der Transaktion, nie aus dem Link; die URI selbst wird nie angezeigt.
 */
private data class AbortEntry(
    val uri: String,
    val processable: Boolean,
    val transactionId: String? = null,
    val majorState: TransactionMajorState? = null,
    val amountText: String? = null,
    val summary: String? = null,
)

/**
 * Sammel-Bestaetigung fuer talerlink://compose-abort (link-protokoll.md
 * Abschnitt 5, Gegenstueck zu taler-ios SignalComposeAbortView). Abgebrochen
 * wird nur nach dem Tap des Nutzers, und nur Transaktionen, die diese Wallet
 * selbst erzeugt hat (OwnUriTracker). Eine fremde URI wird nicht angenommen -
 * das unterscheidet diesen Screen vom Aktionslink in HandleUriScreen.kt. Kennt
 * die Wallet keine der URIs (z. B. Signal auf einem zweiten Geraet, die
 * Zahlung kam von der Wallet des ersten), sagt der Screen genau das.
 *
 * Genau ein Ruecksprung pro Anfrage (Guard fired) mit einem
 * result=<uri>|<ergebnis> je Eingangs-URI.
 */
@Composable
fun ComposeAbortScreen(
    model: MainViewModel,
    destination: WalletDestination.ComposeAbort,
    onNavigateBack: () -> Unit,
    navController: NavController,
) {
    val context = LocalContext.current
    val transactionManager = model.transactionManager
    val ownUriTracker = remember { OwnUriTracker(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val uris = destination.uris

    var entries by remember { mutableStateOf<List<AbortEntry>?>(null) }
    var working by remember { mutableStateOf(false) }

    var fired by remember { mutableStateOf(false) }
    fun fireReturn(results: List<Pair<String, AbortOutcome>>) {
        if (fired) return
        fired = true
        ReturnIntentSender.fireAbort(context, destination.returnUri, destination.correlationId, results)
    }

    // Ergebnis ohne Abbruchversuch: fremde URIs not_own, nicht bearbeitbare
    // failed, alle anderen declined.
    fun resultsWithoutAbort(): List<Pair<String, AbortOutcome>> =
        entries?.map { entry ->
            entry.uri to when {
                !entry.processable -> AbortOutcome.FAILED
                entry.transactionId == null -> AbortOutcome.NOT_OWN
                else -> AbortOutcome.DECLINED
            }
        } ?: uris.map { it to AbortOutcome.DECLINED }

    DisposableEffect(Unit) {
        onDispose {
            // Gleiches Muster wie ComposeRefundScreen.kt: verlaesst der Nutzer
            // den Screen ohne Entscheidung, gibt es fuer jede URI ein Ergebnis
            // ohne Abbruch. Nicht, wenn eine neue Instanz diese ersetzt, und
            // nicht waehrend der Abbrueche (dann waeren Teile schon
            // abgebrochen, ohne dass das Ergebnis stimmt).
            val supersededByNewInstance = navController.currentBackStackEntry
                ?.destination?.hasRoute<WalletDestination.ComposeAbort>() == true
            if (!supersededByNewInstance && !working) {
                fireReturn(resultsWithoutAbort())
            }
        }
    }

    BackHandler(working) {
        // absichtlich leer: waehrend der Abbrueche wird Back geschluckt.
    }

    LaunchedEffect(destination) {
        entries = uris.mapIndexed { index, uri ->
            if (!ComposeAbortRequest.isProcessable(uris, index)) {
                return@mapIndexed AbortEntry(uri, processable = false)
            }
            val transactionId = ownUriTracker.transactionIdFor(uri)
                ?: ownUriTracker.transactionIdFor(TalerHttpsLink.unwrap(uri))
                ?: return@mapIndexed AbortEntry(uri, processable = true)
            val tx = transactionManager.getTransactionById(transactionId)
                ?: return@mapIndexed AbortEntry(uri, processable = true)
            AbortEntry(
                uri = uri,
                processable = true,
                transactionId = transactionId,
                majorState = tx.txState.major,
                amountText = tx.amountEffective.toString(),
                summary = when (tx) {
                    is TransactionPeerPushDebit -> tx.info.summary
                    is TransactionPeerPullCredit -> tx.info.summary
                    else -> null
                }?.filter { it.isDefined() && !it.isISOControl() }?.takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun abortOne(entry: AbortEntry): AbortOutcome {
        if (!entry.processable) return AbortOutcome.FAILED
        val transactionId = entry.transactionId ?: return AbortOutcome.NOT_OWN
        // Bereits angenommen/abgelaufen/abgebrochen: nicht erneut abbrechen,
        // nur mappen.
        if (ComposeAbortRequest.isSettled(entry.majorState)) {
            return ComposeAbortRequest.outcome(entry.majorState, abortSucceeded = null)
        }
        val error = transactionManager.abortTransactionAwait(transactionId)
        if (error == null) return AbortOutcome.ABORTED
        // Race: der Empfaenger hat inzwischen angenommen oder die Zahlung ist
        // abgelaufen - frischen Zustand laden, bevor gemappt wird.
        val fresh = transactionManager.getTransactionById(transactionId)?.txState?.major
        return ComposeAbortRequest.outcome(fresh ?: entry.majorState, abortSucceeded = false)
    }

    fun confirm() {
        val current = entries ?: return
        if (working) return
        working = true
        coroutineScope.launch {
            val results = current.map { it.uri to abortOne(it) }
            fireReturn(results)
            working = false
            navController.popBackStack()
        }
    }

    fun closeWithoutAbort() {
        fireReturn(resultsWithoutAbort())
        navController.popBackStack()
    }

    val current = entries
    val own = current?.filter { it.transactionId != null }.orEmpty()

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = {
                Text(
                    when {
                        current == null || own.isEmpty() -> stringResource(R.string.compose_abort_title)
                        own.size == 1 -> stringResource(R.string.compose_abort_title_one)
                        else -> stringResource(R.string.compose_abort_title_many, own.size)
                    }
                )
            },
            onNavigateBack = if (working) null else onNavigateBack,
        ) { paddingValues ->
            if (current == null || working) {
                LoadingScreen(modifier = Modifier.padding(paddingValues))
                return@GlobalScaffold
            }

            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                contextText(destination)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                if (own.isEmpty()) {
                    Text(stringResource(R.string.compose_abort_not_own))
                    Button(onClick = { closeWithoutAbort() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ok))
                    }
                    return@Column
                }

                own.forEach { entry -> AbortEntryRow(entry) }

                val foreignCount = current.size - own.size
                if (foreignCount > 0) {
                    Text(
                        stringResource(R.string.compose_abort_some_not_own, foreignCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.compose_abort_settled_info),
                    style = MaterialTheme.typography.bodySmall,
                )

                Button(onClick = { confirm() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compose_abort_confirm))
                }
                OutlinedButton(onClick = { closeWithoutAbort() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compose_abort_keep))
                }
            }
        }
    }
}

@Composable
private fun contextText(destination: WalletDestination.ComposeAbort): String? {
    val parts = mutableListOf<String>()
    when (destination.reason) {
        "menu" -> parts.add(stringResource(R.string.compose_abort_reason_menu))
        "delete" -> parts.add(stringResource(R.string.compose_abort_reason_delete))
    }
    ComposeAbortRequest.sanitizeHint(destination.recipientHint)?.let { hint ->
        parts.add(
            if (destination.isGroup) {
                stringResource(R.string.compose_abort_group_hint, hint)
            } else {
                stringResource(R.string.compose_abort_chat_hint, hint)
            }
        )
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

@Composable
private fun AbortEntryRow(entry: AbortEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            entry.amountText ?: stringResource(R.string.compose_abort_amount_unknown),
            style = MaterialTheme.typography.titleMedium,
        )
        entry.summary?.let {
            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (entry.majorState) {
            TransactionMajorState.Done -> R.string.compose_abort_state_done
            TransactionMajorState.Expired -> R.string.compose_abort_state_expired
            TransactionMajorState.Aborting,
            TransactionMajorState.Aborted,
            TransactionMajorState.SuspendedAborting -> R.string.compose_abort_state_aborted
            else -> null
        }?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall)
        }
    }
}
