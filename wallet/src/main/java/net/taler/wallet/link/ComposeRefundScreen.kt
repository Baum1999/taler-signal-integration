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

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.delay
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.peer.OutgoingCreating
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.OutgoingPushComposable
import net.taler.wallet.peer.OutgoingResponse
import net.taler.wallet.transactions.TransactionPeerPushCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit

private const val TALER_URI_TIMEOUT_MS = 15_000L // wie in ComposeSendScreen.kt, gleiche Begruendung

/**
 * Bestaetigungs-Screen fuer eine von Signal ueber prepareRefund vorbereitete
 * Rueckerstattung (docs/API.md 2.4, Meilenstein 6). Eine Rueckerstattung ist
 * eine NEUE ausgehende Zahlung, keine Rueckabwicklung der urspruenglichen.
 * Betrag/Zweck kommen nicht von Signal (Signal hat in [PrepareRefundRequest]
 * bewusst kein Betrag-/Zweck-Feld) - sie werden hier live aus der bereits von
 * TalerLinkService.prepareRefund aufgeloesten Original-Transaktion gelesen
 * (originalTransactionId), damit es fuer diese Zahlen keine zweite,
 * potenziell veraltete Quelle gibt (eiserne Regel 4), und dienen NUR als
 * Vorbefuellung (initialAmount/initialSubject) fuer dieselbe
 * OutgoingPushComposable-UI, die auch ComposeSendScreen.kt fuer den
 * normalen Sende-Flow verwendet - der Nutzer kann den Betrag vor dem
 * Bestaetigen weiterhin anpassen, eine Rueckerstattung ist eine neue
 * Zahlung, kein erzwungenes Abbild der alten.
 */
@Composable
fun ComposeRefundScreen(
    model: MainViewModel,
    correlationId: String,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
    navController: NavController,
) {
    val peerManager = model.peerManager
    val transactionManager = model.transactionManager
    val context = LocalContext.current
    val sentRefundStore = remember { SentRefundStore(context.applicationContext) }

    // take() ist single-use - genau einmal bei der ersten Komposition lesen,
    // gleiches Muster wie ComposeSendScreen.kt.
    val entry = remember { PendingRefundStore.take(correlationId) }

    // Dauerhafte "schon erstattet"-Pruefung (SentRefundStore.kt) - unabhaengig
    // von PendingRefundStore/correlationId, ueberlebt App-/Prozessneustarts.
    // overrideWarning: Nutzer hat explizit "verwerfen & neu erstellen"
    // gewaehlt, der alte Eintrag bleibt bestehen, bis diese neue Rueckerstattung
    // tatsaechlich gesendet wird (record() unten ueberschreibt ihn dann).
    val alreadySent = remember(entry) {
        entry?.let { sentRefundStore.entryFor(it.originalTransactionId) }
    }
    var overrideWarning by remember { mutableStateOf(false) }

    var fired by remember { mutableStateOf(false) }
    fun fireReturn(status: ReturnStatus, talerUri: String? = null) {
        if (fired || entry == null) return
        fired = true
        ReturnIntentSender.fire(context, entry.request.returnUri, entry.request.correlationId, status, talerUri)
    }

    if (entry == null) {
        // Unbekannte/abgelaufene correlationId - kommentarlos abbrechen,
        // gleiches Prinzip wie ComposeSendScreen.kt/TalerReturnActivity.
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    // Gleicher Reset-bei-Betreten-Grund wie ComposeSendScreen.kt: pushState
    // ist prozessweiter, geteilter Zustand, den eine fruehere Session hier
    // sonst ungewollt sofort verarbeiten wuerde.
    remember { peerManager.resetPushPayment() }
    DisposableEffect(Unit) {
        onDispose {
            peerManager.resetPushPayment()
            // Gleiches Cancel-Prinzip wie ComposeSendScreen.kt: erst wenn der
            // Nutzer den Screen tatsaechlich verlaesst, nie synchron mit
            // einem onShowError-Aufruf im selben Tick.
            //
            // Ausnahme: MainActivity.emitComposeRefund() navigiert mit
            // popUpTo<ComposeRefund>{inclusive=true}, was diese Instanz
            // ersetzt statt sie zu verlassen (z.B. erneuter Refund-Tap,
            // waehrend die App im Hintergrund lief und dieser Screen nie
            // disposed wurde). In diesem Fall ist die aktuelle Backstack-
            // Entry bereits die NEUE ComposeRefund-Instanz - CANCELLED darf
            // dann nicht feuern, sonst reisst der startActivity()-Ruecksprung
            // zu Signal (ReturnIntentSender) den Fokus vom gerade erst
            // gezeigten neuen Screen wieder weg (sichtbares Aufblitzen/
            // "Taler ploppt auf und schliesst sofort wieder").
            val supersededByNewInstance = navController.currentBackStackEntry
                ?.destination?.hasRoute<WalletDestination.ComposeRefund>() == true
            if (!supersededByNewInstance) {
                fireReturn(ReturnStatus.CANCELLED)
            }
        }
    }

    // Original-Transaktion live nachlesen statt eines im Store mitgefuehrten
    // Betrags-Snapshots (siehe Klassendoc). selectTransaction(transactionId)
    // ist suspend und liefert deterministisch per Rueckgabewert, ob die
    // Transaktion gefunden wurde - kein Timeout/Race auf dem Flow noetig.
    // transactionManager.selectedTransaction wird spaeter (nach
    // initiatePeerPushDebit) fuer die NEUE Transaktion wiederverwendet - das
    // ist unproblematisch, da originalTx bis dahin bereits in lokalen State
    // kopiert ist.
    var originalTx by remember { mutableStateOf<TransactionPeerPushCredit?>(null) }
    var originalTxFailed by remember { mutableStateOf(false) }
    LaunchedEffect(entry) {
        val found = transactionManager.selectTransaction(entry.originalTransactionId)
        val current = transactionManager.selectedTransaction.value
        if (found && current is TransactionPeerPushCredit && current.transactionId == entry.originalTransactionId) {
            originalTx = current
        } else {
            originalTxFailed = true
            onShowError(
                TalerErrorInfo.makeCustomError(
                    context.getString(R.string.compose_refund_error_original_not_found),
                )
            )
        }
    }

    if (originalTxFailed) {
        // Kein sofortiges fireReturn/onNavigateBack - gleiches Prinzip wie
        // die Fehlerzweige in ComposeSendScreen.kt: nur zeigen, der obige
        // onDispose-Hook uebernimmt den Ruecksprung erst beim tatsaechlichen
        // Verlassen.
        return
    }

    val tx = originalTx

    // Gleicher Leerer-Kontostand-Guard wie ComposeSendScreen.kt: OutgoingPush-
    // IntroComposable greift bei der ersten Komposition auf scopes[0] zu,
    // falls kein passender Scope aus der Original-Transaktion gefunden wird.
    val scopes = remember { model.balanceManager.getScopes(true) }
    if (tx != null && scopes.isEmpty()) {
        LaunchedEffect(Unit) {
            onShowError(
                TalerErrorInfo.makeCustomError(
                    context.getString(R.string.compose_send_error_no_balance),
                )
            )
        }
        return
    }
    val originalScope = tx?.scopes?.firstOrNull { it.currency == tx.amountEffective.currency }

    val devMode by model.devMode.observeAsState(false)
    val pushState by peerManager.pushState.collectAsStateLifecycleAware()
    var initiated by remember { mutableStateOf(false) }

    // Gleicher Grund/gleiches Muster wie ComposeSendScreen.kt Critical #1/C2.
    BackHandler(initiated && (pushState is OutgoingCreating || pushState is OutgoingResponse)) {
        // absichtlich leer: Back wird in diesem Fenster geschluckt.
    }

    LaunchedEffect(pushState, initiated) {
        if (!initiated) return@LaunchedEffect
        val s = pushState
        if (s is OutgoingResponse) {
            transactionManager.selectTransaction(s.transactionId)
            delay(TALER_URI_TIMEOUT_MS)
            if (!fired) {
                onShowError(
                    TalerErrorInfo.makeCustomError(
                        context.getString(R.string.compose_send_error_uri_timeout),
                    )
                )
            }
        } else if (s is OutgoingError) {
            onShowError(s.info)
        }
    }

    LaunchedEffect(initiated) {
        if (!initiated) return@LaunchedEffect
        transactionManager.selectedTransaction.collect { t ->
            val expectedId = (pushState as? OutgoingResponse)?.transactionId
            if (t is TransactionPeerPushDebit && t.transactionId == expectedId) {
                t.talerUri?.let { uri ->
                    OwnUriTracker.track(uri, t.transactionId)
                    sentRefundStore.record(entry.originalTransactionId, t.transactionId)
                    fireReturn(ReturnStatus.READY, uri)
                    // Gleicher Grund wie ComposeSendScreen.kt: NICHT das
                    // geteilte onNavigateBack(), siehe Kommentar am
                    // navController-Parameter dort.
                    navController.popBackStack()
                }
            }
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.compose_refund_title)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            if (tx == null) {
                LoadingScreen(modifier = Modifier.padding(paddingValues))
                return@GlobalScaffold
            }

            // Dauerhafte Dopplungs-Warnung (SentRefundStore.kt): fuer diese
            // originalTransactionId wurde bereits erfolgreich eine
            // Rueckerstattung gesendet. Blockiert das Formular, bis der
            // Nutzer explizit "verwerfen & neu erstellen" waehlt - der alte
            // SentRefundStore-Eintrag bleibt dabei unveraendert bestehen und
            // wird erst ueberschrieben, wenn die neue Rueckerstattung
            // tatsaechlich abgeschickt wird (siehe record()-Aufruf oben).
            if (alreadySent != null && !overrideWarning) {
                Column(
                    modifier = Modifier.padding(paddingValues).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.compose_refund_already_sent_warning,
                            DateUtils.formatDateTime(
                                context,
                                alreadySent.sentAtMillis,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                            ),
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNavigateBack) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(onClick = { overrideWarning = true }) {
                            Text(stringResource(R.string.compose_refund_already_sent_override))
                        }
                    }
                }
                return@GlobalScaffold
            }

            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                // Gleiche Absicherung wie recipientHint in ComposeSendScreen.kt
                // (Fix Final-Review I4): info.summary stammt aus der ORIGINAL-
                // Transaktion, deren Zweck-Text vom urspruenglichen Sender
                // frei gewaehlt wurde - also aus Talers Sicht angreiferkontrolliert.
                tx.info.summary
                    ?.filter { it.isDefined() && !it.isISOControl() }
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            stringResource(R.string.compose_refund_original_summary_format, it),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                // OutgoingPushComposable deckt Intro/Checked/Error UND
                // Checking/Creating/Response (LoadingScreen) bereits
                // vollstaendig ab, siehe ComposeSendScreen.kt fuer dieselbe
                // Begruendung. initialAmount/initialSubject befuellen das
                // Formular mit der Original-Transaktion vor, bleiben aber
                // editierbar.
                OutgoingPushComposable(
                    state = pushState,
                    defaultScope = originalScope,
                    scopes = scopes,
                    devMode = devMode,
                    getCurrencySpec = model.exchangeManager::getSpecForScopeInfo,
                    getFees = {
                        model.selectScope(it.scope)
                        peerManager.checkPeerPushFees(it.amount, restrictScope = it.scope)
                    },
                    onSend = { amountScope, summary, hours ->
                        initiated = true
                        peerManager.initiatePeerPushDebit(
                            amountScope.amount,
                            summary,
                            hours,
                            restrictScope = amountScope.scope,
                        )
                    },
                    initialAmount = tx.amountEffective,
                    initialSubject = tx.info.summary,
                )
            }
        }
    }
}
