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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.map
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.delay
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.peer.GroupPushState
import net.taler.wallet.peer.GroupShareInfo
import net.taler.wallet.peer.GroupShareStore
import net.taler.wallet.peer.OutgoingCreating
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.OutgoingPushComposable
import net.taler.wallet.peer.OutgoingResponse
import net.taler.wallet.peer.ShareResult
import net.taler.wallet.peer.allSucceeded
import net.taler.wallet.peer.failures
import net.taler.wallet.peer.successfulUris
import net.taler.wallet.transactions.TransactionPeerPushDebit

// Fix round 1 (Task-A6 Review, Critical #2): Sicherheitsnetz - falls
// initiatePeerPushDebit erfolgreich eine transactionId liefert, die
// anschliessend ausgewaehlte Transaktion aber auch nach dieser Zeit noch
// keine talerUri hat, brechen wir SICHTBAR mit einem Fehler ab, statt die
// bereits committete Zahlung stillschweigend als CANCELLED zu melden.
//
// Fix (UX-Befund: "Timed out waiting for payment URI" tritt in der Praxis
// auf): 15s waren gegen den oeffentlichen, geteilten
// exchange.demo.taler.net zu knapp bemessen - die Zahlung committet dabei
// tatsaechlich (Purse wird angelegt), nur die URI-Bestaetigung braucht
// laenger als das Timeout.
//
// Fix (Live-Debugging 2026-09-01, mit Logcat-Beweis): 45s war IMMER NOCH
// zu knapp - nicht wegen des Merge-Schritts (der laeuft ohnehin asynchron
// im Hintergrund weiter, talerUri braucht ihn nicht), sondern weil schon
// der GET .../purses/<id>/create-Request selbst - der allererste Schritt,
// ohne den es ueberhaupt keine talerUri geben kann - gegen den oeffentlichen
// exchange.demo.taler.net gemessen 39-47s brauchte (zwei unabhaengige
// Live-Messungen: 46666ms und 39154ms, siehe BackendManager-Logcat
// task-observability-event/http-fetch-start|finish-success fuer
// ".../purses/<pursePub>/create"). Das Timeout feuerte dabei zuverlaessig
// 1-2s VOR Abschluss des Requests: Taler meldete "cancelled" an Signal
// (keine Nachricht kommt an), waehrend die Zahlung im Hintergrund trotzdem
// committete und als "ausstehend" liegen blieb - exakt das urspruenglich
// gemeldete Symptom. Bewusst grosszuegig neu gesetzt statt nur leicht
// erhoeht, da die Streuung der Live-Messwerte zeigt, dass ein knapp
// bemessener fester Wert hier strukturell unzuverlaessig ist; das
// Sicherheitsnetz bleibt trotzdem bestehen, nur mit sehr viel mehr Luft,
// damit ein echter, dauerhafter Haenger weiterhin sichtbar wird statt
// endlos zu laden.
private const val TALER_URI_TIMEOUT_MS = 600_000L // 10 Minuten

/**
 * Bestaetigungs-Screen fuer einen von Signal ueber prepareSend vorbereiteten
 * Versand (docs/API.md 2.7). Der Betrag wird NICHT mehr von Signal
 * vorausgefuellt (frueher: PrepareSendRequest.amount/currency) - Signal
 * darf keine Betraege kennen/anzeigen, also fragt Taler den Betrag jetzt
 * selbst ab. Deshalb Wiederverwendung von OutgoingPushComposable (derselbe
 * "Betrag eingeben -> Gebuehr live pruefen -> Zweck -> bestaetigen"-Flow wie
 * beim normalen "Send"-Bildschirm aus dem Aktionen-Menue), statt einer
 * eigenen Betrags-UI - null Aenderungen an OutgoingPushComposable/
 * AmountScopeField selbst, nur Wiederverwendung von aussen. Die eigentliche
 * ausgehende Zahlung entsteht ausschliesslich durch initiatePeerPushDebit im
 * onSend-Callback unten - ausgeloest erst durch einen Tap des Nutzers,
 * niemals automatisch.
 */
@Composable
fun ComposeSendScreen(
    model: MainViewModel,
    correlationId: String,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
    // Fix (Regression aus Final-Review C2-Fix): NUR fuer den Erfolgs-Pfad
    // unten (selectedTransaction.collect, talerUri gefunden) - siehe
    // Kommentar dort fuer die vollstaendige Begruendung, warum das
    // geteilte onNavigateBack() an dieser einen Stelle NICHT verwendet
    // werden darf. navController.popBackStack() manipuliert direkt die
    // interne backQueue/den Navigator (siehe NavControllerImpl.popBack-
    // StackInternal in androidx.navigation:navigation-runtime:2.9.8,
    // dessen Quelle keinerlei Bezug zu OnBackPressedCallback/-Dispatcher
    // hat) und routet damit NICHT ueber den OnBackPressedDispatcher, auf
    // dem der obige BackHandler registriert ist - im Gegensatz zu
    // onNavigateBack(), das ueber onBackPressedDispatcher.onBackPressed()
    // laeuft (siehe WalletNavHost.kt). GlobalScaffold's Zurueck-Pfeil unten
    // nutzt bewusst weiterhin das geteilte onNavigateBack() - dort ist das
    // gewuenschte Blockieren durch den BackHandler waehrend des Commit-
    // Fensters (C2) weiterhin korrekt und erwuenscht.
    navController: NavController,
) {
    val peerManager = model.peerManager
    val transactionManager = model.transactionManager
    val context = LocalContext.current

    // take() ist single-use - genau einmal bei der ersten Komposition lesen,
    // damit eine Recomposition nicht versehentlich ein zweites Mal "nimmt"
    // (und dabei null bekommt, weil der erste Aufruf den Eintrag schon
    // entfernt hat). Gleiches Muster wie IncomingPushPaymentScreen.myCallback.
    val request = remember { PendingSendStore.take(correlationId) }

    var fired by remember { mutableStateOf(false) }
    var currentPaymentData by remember { mutableStateOf<TalerPaymentData?>(null) }
    
    fun fireReturn(status: ReturnStatus, talerUri: String? = null, talerPaymentData: TalerPaymentData? = null) {
        if (fired || request == null) return
        fired = true
        ReturnIntentSender.fire(context, request.returnUri, request.correlationId, status, talerUri, talerPaymentData)
    }

    if (request == null) {
        // Unbekannte/abgelaufene correlationId - kommentarlos abbrechen,
        // gleiches Prinzip wie TalerReturnActivity auf Signal-Seite.
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    // Fix round 1 (Task-A6 Review, Critical #1): peerManager.pushState ist
    // prozessweiter, geteilter Zustand (genau wie transactionManager.selected-
    // Transaction). Ohne Reset bei Betreten dieses Screens koennte hier ein
    // stehengebliebener OutgoingResponse/OutgoingError einer FRUEHEREN
    // compose-send-Session (anderer correlationId, z.B. eine bereits
    // ausgelieferte Zahlung) sofort verarbeitet werden - noch bevor der
    // Nutzer ueberhaupt einen Betrag gesehen oder Send getippt hat. remember
    // fuehrt den Reset synchron waehrend der ERSTEN Komposition aus (bevor
    // pushState unten ueberhaupt gelesen wird); der DisposableEffect
    // spiegelt exakt das Muster aus OutgoingPushScreen.kt fuer den Fall, dass
    // der Nutzer den Screen ohne Tap wieder verlaesst.
    remember {
        peerManager.resetPushPayment()
        peerManager.resetGroupPushState()
    }
    DisposableEffect(Unit) {
        onDispose {
            peerManager.resetPushPayment()
            peerManager.resetGroupPushState()
            // Nutzer verlaesst den Screen (Zurueck/Home), ohne dass zuvor
            // schon READY gefeuert wurde - gleiches Cancel-Prinzip wie
            // IncomingPushPaymentScreen. Der fired-Guard in fireReturn
            // verhindert ein doppeltes Feuern, falls READY bereits ausgeloest
            // wurde, bevor dieser Screen disposed wird.
            //
            // Fix (Final-Review I3): dieser Hook liegt bewusst VOR den
            // amount-Validierungs-Checks weiter unten (die bei Fehlschlag
            // frueh zurueckkehren), damit auch der Ungueltiger-Betrag-Pfad
            // ueber denselben, ERST-BEIM-TATSAECHLICHEN-VERLASSEN
            // ausgeloesten Ruecksprung laeuft, statt fireReturn synchron mit
            // onShowError im selben Tick aufzurufen. fireReturn wechselt per
            // startActivity() zu Signal - wuerde es sofort nach onShowError
            // gefeuert, kaeme Signal in den Vordergrund, noch bevor der
            // Nutzer Talers eigenes ErrorBottomSheet (gerendert unabhaengig
            // von diesem Screen in MainActivity) je zu Gesicht bekommt.
            //
            // Ausnahme (gleiches Muster wie ComposeRefundScreen.kt):
            // MainActivity.emitComposeSend() navigiert mit
            // popUpTo<ComposeSend>{inclusive=true}, was diese Instanz
            // ERSETZT statt sie zu verlassen (z.B. erneuter Send-Tap,
            // waehrend die App im Hintergrund lief und dieser Screen nie
            // disposed wurde). Ist die aktuelle Backstack-Entry bereits die
            // NEUE ComposeSend-Instanz, darf CANCELLED nicht feuern - sonst
            // reisst der startActivity()-Ruecksprung zu Signal
            // (ReturnIntentSender) den Fokus vom gerade erst gezeigten
            // neuen Screen wieder weg.
            val supersededByNewInstance = navController.currentBackStackEntry
                ?.destination?.hasRoute<WalletDestination.ComposeSend>() == true
            if (!supersededByNewInstance) {
                fireReturn(ReturnStatus.CANCELLED)
            }
        }
    }

    // Neu (Betrag kommt nicht mehr von Signal): OutgoingPushIntroComposable
    // greift bei der ersten Komposition auf scopes[0] zu (defaultScope ?:
    // scopes[0]) - hat der Nutzer noch nie ein Taler-Guthaben empfangen, ist
    // diese Liste leer und das wuerde crashen. Signal kann das vorher nicht
    // wissen (Regel 2: keine Balance-Abfrage von Signal aus), also hier
    // sichtbar abfangen statt crashen zu lassen - gleiches Prinzip wie die
    // fruehere amount==null-Pruefung: nur Fehler zeigen, Ruecksprung dem
    // onDispose-Hook oben ueberlassen.
    // Fix (Bug: "You don't have any Taler balance yet" trotz vorhandenem
    // Guthaben): dieser Screen wird per talerlink://compose-send Deep-Link
    // direkt aus Signal angesprungen (MainActivity.emitComposeSend) und
    // liegt im Nav-Graph NICHT unter MainScreen (WalletNavHost.kt) - dessen
    // LaunchedEffect ist aber die einzige Stelle, die
    // balanceManager.loadAssets() beim App-Start aufruft. Bei einem
    // Kaltstart ueber diesen Deep-Link (App vorher nicht offen) wurde die
    // Balance also NIE geladen: der vorherige einmalige
    // remember{getScopes(true)}-Snapshot las balances.value in seinem noch
    // leeren Initialzustand und blieb das fuer die Lebensdauer dieser
    // Komposition - scopes war dadurch immer leer, unabhaengig vom
    // tatsaechlichen Guthaben. Jetzt: Ladung selbst anstossen und reaktiv
    // auf das Ergebnis warten statt einen Snapshot des Anfangszustands zu
    // nehmen.
    val balanceState by model.balanceManager.state.observeAsState(BalanceState.None)
    val scopes by model.balanceManager.balances
        .map { list -> list.filter { !it.disablePeerPayments }.map { it.scopeInfo } }
        .observeAsState(emptyList())

    LaunchedEffect(Unit) {
        if (balanceState is BalanceState.None) {
            model.balanceManager.loadAssets()
        }
    }

    if (balanceState !is BalanceState.Success) {
        LoadingScreen(Modifier.fillMaxSize())
        return
    }

    if (scopes.isEmpty()) {
        LaunchedEffect(Unit) {
            onShowError(
                TalerErrorInfo.makeCustomError(
                    context.getString(R.string.compose_send_error_no_balance),
                )
            )
        }
        return
    }

    val devMode by model.devMode.observeAsState(false)
    val pushState by peerManager.pushState.collectAsStateLifecycleAware()
    val groupPushState by peerManager.groupPushState.collectAsStateLifecycleAware()

    // Fix round 1 (Task-A6 Review, Critical #1 Teil b): beide reaktiven
    // Effekte unten reagieren erst NACH einem tatsaechlichen Tap auf den
    // Send-Button (initiated = true, gesetzt im onClick unten). Vor diesem
    // Zeitpunkt darf nichts aus pushState/selectedTransaction verarbeitet
    // werden - das schliesst die im Review beschriebene Race komplett aus,
    // unabhaengig vom exakten Timing des obigen Resets.
    var initiated by remember { mutableStateOf(false) }

    // Fix (Final-Review C2): waehrend initiatePeerPushDebit unterwegs ist
    // (OutgoingCreating) oder die Transaktion bereits erzeugt, aber noch
    // nicht mit talerUri abgeschlossen ist (OutgoingResponse), ist die
    // Zahlung entweder gerade dabei zu committen oder hat bereits committet -
    // Back darf in diesem Fenster NICHT mehr zum onDispose-CANCELLED-Pfad
    // fuehren, sonst meldet Taler Signal "cancelled", waehrend die
    // wallet-core-Coroutine im Hintergrund trotzdem fertig laeuft (Geld
    // bewegt sich, Signal zeigt nichts, Nutzer glaubt abgebrochen zu haben).
    // Gleiches Muster wie DepositScreen.kt/MainScreen.kt.
    BackHandler(
        initiated && (
            pushState is OutgoingCreating || pushState is OutgoingResponse ||
                groupPushState is GroupPushState.InProgress
            )
    ) {
        // absichtlich leer: Back wird in diesem Fenster geschluckt.
    }

    // Gruppen-Split-Versand (Meilenstein 3): eigener, vom obigen Einzel-Pfad
    // (pushState) unabhaengiger Effekt - initiatePeerPushDebitGroupAsync hat
    // N Purses gleichzeitig in Flug, das bestehende OutgoingResponse/
    // selectedTransaction-Muster oben kennt nur eine.
    LaunchedEffect(groupPushState, initiated) {
        if (!initiated) return@LaunchedEffect
        val s = groupPushState
        if (s is GroupPushState.Done) {
            val results = s.results
            if (results.allSucceeded()) {
                val uris = results.successfulUris()
                val finalPaymentData = currentPaymentData?.copy(uri = uris)
                // Jede der N URIs ist eine eigenstaendige ausgehende Zahlung
                // (Regel 3 PROMPT.md: die URI ist ein Inhaberpapier) - alle
                // einzeln nachverfolgen, nicht nur die erste, sonst wuerden
                // Anteile 2..N spaeter faelschlich als fremd erkannt.
                val successes = results.filterIsInstance<ShareResult.Success>()
                val groupShareStore = GroupShareStore(context)
                successes.forEachIndexed { index, share ->
                    OwnUriTracker(context).track(share.talerUri, share.transactionId)
                    // Reine lokale UI-Zusatzinfo fuer die Transaktionsliste
                    // (Sammelzeile "Gruppenzahlung"), bewusst getrennt vom
                    // Contract-Term-`summary` - Nutzer-Vorgabe 2026-09-02,
                    // siehe GroupShareStore.kt.
                    groupShareStore.save(
                        share.transactionId,
                        GroupShareInfo(request.correlationId, index, successes.size),
                    )
                }
                fireReturn(ReturnStatus.READY, uris.firstOrNull(), finalPaymentData)
                navController.popBackStack()
            } else {
                // Regel 11 (PROMPT_parallel_group_split.md): kein Alleingang
                // bei Teil-Fehlschlag - weder automatisch mit weniger als N
                // URIs senden noch bereits erfolgreiche Purses automatisch
                // per abortTransaction zurueckrollen. Erfolgreiche Purses
                // bleiben bestehen (sichtbar/verwaltbar in Talers eigener
                // Transaktionsliste, retry/abort dort moeglich) - nur der
                // Ruecksprung zu Signal bleibt aus, damit kein unvollstaen-
                // diges JSON-Paket (weniger URIs als Mitglieder) verschickt
                // wird.
                peerManager.resetGroupPushState()
                initiated = false
                onShowError(
                    TalerErrorInfo.makeCustomError(
                        context.getString(
                            R.string.compose_send_group_partial_failure,
                            results.failures().size,
                            results.size,
                        )
                    )
                )
            }
        }
    }

    LaunchedEffect(pushState, initiated) {
        if (!initiated) return@LaunchedEffect
        val s = pushState
        if (s is OutgoingResponse) {
            transactionManager.selectTransaction(s.transactionId)
            // Fix round 1 (Critical #2 Sicherheitsnetz): falls die unten
            // ausgewaehlte Transaktion auch nach dieser Zeit noch keine
            // talerUri hat, nicht endlos warten, aber die bereits committete
            // Zahlung auch nicht stillschweigend als CANCELLED melden -
            // sichtbarer Fehler statt Stille (siehe Review Critical #2).
            delay(TALER_URI_TIMEOUT_MS)
            if (!fired) {
                // Fix (Timeout-Pfad): resetPushPayment und fireReturn(CANCELLED)
                // aufrufen, damit der BackHandler nicht dauerhaft aktiv bleibt.
                // Siehe REVIEW.md offener Befund: ohne Reset bleibt der Nutzer
                // im UI gefangen, da BackHandler(initiated && (OutgoingCreating
                // || OutgoingResponse)) weiterhin enabled ist.
                peerManager.resetPushPayment()
                fireReturn(ReturnStatus.CANCELLED)
                // Fix (Final-Review I3): kein sofortiges onNavigateBack nach onShowError
                // - siehe Begruendung am onDispose-Hook oben. Der obere Screen bleibt
                // (Loading- bzw. Formular-Ansicht) sichtbar komponiert, die GlobalScaffold-
                // TopBar mit Zurueck-Pfeil bleibt bedienbar; erst wenn der Nutzer
                // darueber tatsaechlich verlaesst, feuert onDispose CANCELLED.
                // Nach fireReturn(CANCELLED) darf onNavigateBack sofort aufgerufen werden,
                // da der BackHandler durch den Reset von pushState nicht mehr blockiert.
                onShowError(
                    TalerErrorInfo.makeCustomError(
                        context.getString(R.string.compose_send_error_uri_timeout),
                    )
                )
                onNavigateBack()
            }
        } else if (s is OutgoingError) {
            // Fix (Final-Review I3): gleiches Prinzip wie oben - nur zeigen,
            // Ruecksprung dem onDispose-Sicherheitsnetz ueberlassen. Gleiches
            // Muster wie OutgoingPushScreen.kt/IncomingPushPaymentScreen.kt,
            // die bei OutgoingError/IncomingError ebenfalls nur onShowError
            // aufrufen, ohne selbst zu navigieren.
            onShowError(s.info)
        }
    }

    LaunchedEffect(initiated) {
        if (!initiated) return@LaunchedEffect
        transactionManager.selectedTransaction.collect { tx ->
            val expectedId = (pushState as? OutgoingResponse)?.transactionId
            if (tx is TransactionPeerPushDebit && tx.transactionId == expectedId) {
                // Fix round 1 (Task-A6 Review, Critical #2): talerUri kann bei
                // der ERSTEN Momentaufnahme noch null sein (Purse-Erstellung
                // evtl. noch nicht ganz abgeschlossen, wenn getTransactionById
                // in transactionManager.selectTransaction() aufgerufen wird).
                // onNavigateBack() darf deshalb NUR innerhalb dieses lets
                // passieren - sonst wuerde eine bereits committete Zahlung als
                // CANCELLED gemeldet, ohne dass die URI je geliefert wurde.
                // Bleibt talerUri null, bleiben wir composed und warten
                // weiter: MainViewModel.onNotificationReceived ruft bei jeder
                // wallet-core-Transaktions-Notification fuer diese Id
                // transactionManager.updateTransactionIfSelected(id) auf, was
                // hier eine neue, spaetere Emission auf selectedTransaction
                // ausloest, sobald die Purse tatsaechlich fertig ist (mit
                // Timeout-Sicherheitsnetz siehe oben).
                tx.talerUri?.let { uri ->
                    // Anmerkung (Review Important #5, urspruenglich ausserhalb
                    // dieses Tasks): transactionManager.selectTransaction(...)
                    // oben laesst MainActivity.kt's vorhandenen
                    // selectedTransaction.collect-Block erneut laufen. Der
                    // dortige Log.d loggte frueher die volle taler://-URI mit
                    // - per Final-Review C1 behoben (MainActivity.kt loggt
                    // jetzt nur noch die transactionId).
                    OwnUriTracker(context).track(uri, tx.transactionId)
                    // Wenn currentPaymentData existiert (z.B. von Gruppen-Split),
                    // füge die URI hinzu und sende es als JSON-Objekt
                    val finalPaymentData = currentPaymentData?.copy(uri = listOf(uri))
                    fireReturn(ReturnStatus.READY, uri, finalPaymentData)
                    // Fix (Regression aus Final-Review C2-Fix): NICHT das
                    // geteilte onNavigateBack() - das wuerde ueber densel-
                    // ben OnBackPressedDispatcher laufen, auf dem der oben
                    // registrierte BackHandler(initiated && (OutgoingCreating
                    // || OutgoingResponse)) in genau diesem Moment noch
                    // enabled ist (pushState ist hier IMMER NOCH
                    // OutgoingResponse - PeerManager kennt keinen "fertig"-
                    // Zustand danach, und initiated bleibt true). Der
                    // Dispatcher wuerde den programmatischen Zurueck-Aufruf
                    // deshalb an den eigenen, absichtlich leeren BackHandler-
                    // Callback verteilen statt an NavControllers Pop-Callback
                    // weiter hinten in derselben Dispatcher-Kette - Taler
                    // bliebe stumm auf dem LoadingScreen haengen (siehe
                    // Review-Befund). navController.popBackStack() manipu-
                    // liert stattdessen direkt die interne backQueue (siehe
                    // Kommentar am navController-Parameter oben) und ist
                    // damit unabhaengig vom Timing, wann der BackHandler
                    // seinen neuen enabled-Wert tatsaechlich uebernimmt.
                    navController.popBackStack()
                }
            }
        }
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            title = { Text(stringResource(R.string.compose_send_title)) },
            onNavigateBack = onNavigateBack,
        ) { paddingValues ->
            // Fix round 1 (Task-A6 Review, Important #2): waehrend
            // initiatePeerPushDebit unterwegs ist (OutgoingCreating) oder die
            // Transaktion bereits erzeugt, aber noch nicht mit talerUri
            // abgeschlossen ist (OutgoingResponse, s.o.): OutgoingPushComposable
            // rendert dafuer bereits selbst einen LoadingScreen ueber sein
            // eigenes when(state) (siehe OutgoingPushComposable.kt Zeile
            // ~85f.) - kein eigener Zweig hier noetig, sonst doppelt.
            // Doppel-Tap-Schutz bleibt trotzdem erhalten: initiatePeerPushDebit
            // setzt synchron OutgoingCreating, wodurch die Composable beim
            // naechsten Recompose auf LoadingScreen wechselt - identisches
            // Muster wie im unveraenderten OutgoingPushScreen.kt.
            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                // Fix (Final-Review I4, weiterhin gueltig): recipientHint ist
                // der Anzeigename des REMOTEN Signal-Kontakts - aus Talers
                // Sicht angreifer-kontrolliert. Eigene Text-Composable,
                // maxLines=1, Steuerzeichen (inkl. Zeilenumbrueche) vor der
                // Anzeige entfernt.
                request.recipientHint
                    ?.filter { it.isDefined() && !it.isISOControl() }
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            stringResource(R.string.compose_send_recipient_hint_format, it),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                // OutgoingPushComposable deckt Intro/Checked/Error (Formular
                // bzw. ErrorComposable) UND Checking/Creating/Response
                // (LoadingScreen) bereits vollstaendig ueber sein eigenes
                // when(state) ab (siehe OutgoingPushComposable.kt) - kein
                // eigener Error-/Loading-Zweig hier noetig. Der Gruppen-Pfad
                // (groupPushState) lebt AUSSERHALB dieses when(state) (eigener,
                // von pushState unabhaengiger Zustand) - deshalb hier ein
                // eigenes Gate, statt OutgoingPushComposable per zusaetzlichem
                // Parameter um einen zweiten Zustands-Input zu erweitern.
                if (groupPushState is GroupPushState.InProgress) {
                    LoadingScreen(Modifier.fillMaxSize())
                } else {
                OutgoingPushComposable(
                    state = pushState,
                    defaultScope = null,
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
                    onSendGroup = { amountScope, count, summary, hours ->
                        initiated = true
                        peerManager.initiatePeerPushDebitGroupAsync(
                            count = count,
                            amountPerShare = amountScope.amount,
                            summary = summary,
                            expirationHours = hours,
                            restrictScope = amountScope.scope,
                        )
                    },
                    onSplitDataChanged = { includeSelf, totalAmount ->
                        // JSON-Paket (TalerPaymentData) jetzt fuer JEDEN
                        // Versand gebaut, nicht nur Gruppen-Split (Nutzer-
                        // Vorgabe 2026-09-04: legacyText-Fallback auch im
                        // 1:1-Chat) - includeSelf/totalAmount bleiben null,
                        // wenn kein Split aktiv ist (splitEnabled=false ruft
                        // onSplitDataChanged(null, null) auf, siehe
                        // OutgoingPushComposable.kt), das JSON traegt dann
                        // nur die URI plus den Klartext-Fallback.
                        currentPaymentData = TalerPaymentData(
                            legacyText = context.getString(R.string.taler_payment_legacy_text),
                            version = 1,
                            includeSelf = includeSelf,
                            // amountStr statt toString(): letzteres ist
                            // Amount.toString(showSymbol=true), also
                            // locale-formatiert MIT Waehrungssymbol (z.B.
                            // "5,00 EUR") - inkonsistent mit jedem
                            // anderen Betrag in dieser Pipeline
                            // (record.amount/PaymentPreviewResult.amount
                            // nutzen ueberall amountStr, ein reiner
                            // Dezimalstring ohne Waehrung/Locale). Der
                            // Signal-Fork validiert totalAmount gegen
                            // den Pro-Anteil-Betrag (GroupSplitCard.
                            // computeVerifiedTotal) - das braucht einen
                            // verlaesslich parsbaren BigDecimal-String,
                            // keinen localeabhaengigen Anzeigetext.
                            totalAmount = totalAmount?.amountStr,
                            uri = emptyList()
                        )
                    },
                    // Fix (Signal-Fork UX-Befund #3): Gueltigkeit des
                    // Zahlungslinks an den disappearing-messages-Timer des
                    // Chats koppeln, statt sie unabhaengig vom (bereits
                    // angezeigten) Hinweistext laufen zu lassen. Aufrunden
                    // auf volle Stunden, mindestens 1h (0 Stunden waere ein
                    // sofort abgelaufener Link).
                    initialExpirationHours = request.disappearingMessagesSeconds
                        .takeIf { it > 0 }
                        ?.let { seconds -> ((seconds + 3599) / 3600).toLong() },
                    groupSplitMemberCount = request.memberCount.takeIf { request.isGroup },
                    // Fix (UX-Befund: "Einschieber von oben ist immer noch
                    // da"): Gruppen-Hinweis/-Warnung und der disappearing-
                    // messages-Hinweis sind ebenfalls Teil des frueheren
                    // Banners - nicht nur der Aufteilungs-Rechner. Wandern
                    // deshalb genau wie GroupSplitHint jetzt INNERHALB des
                    // Formulars direkt unter das Betragsfeld, statt als
                    // Textblock vor dem gesamten Formular zu stehen. Nur
                    // recipientHint oben (wer ist der Empfaenger) bleibt
                    // bewusst vor dem Formular - das ist Kontext, den man
                    // braucht, BEVOR man ueberhaupt einen Betrag eintippt.
                    contextContent = {
                        if (request.isGroup) {
                            request.memberCount?.let { count ->
                                Text(stringResource(R.string.compose_send_group_hint, count))
                            }
                            Text(stringResource(R.string.compose_send_group_warning))
                        }
                        if (request.disappearingMessagesSeconds > 0) {
                            Text(
                                stringResource(
                                    R.string.compose_send_disappearing_hint,
                                    formatDuration(context, request.disappearingMessagesSeconds),
                                )
                            )
                        }
                    },
                )
                }
            }
        }
    }
}

private fun formatDuration(context: android.content.Context, seconds: Int): String = when {
    seconds < 60 -> context.resources.getQuantityString(R.plurals.compose_send_duration_seconds, seconds, seconds)
    seconds < 3_600 -> (seconds / 60).let {
        context.resources.getQuantityString(R.plurals.compose_send_duration_minutes, it, it)
    }
    seconds < 86_400 -> (seconds / 3_600).let {
        context.resources.getQuantityString(R.plurals.compose_send_duration_hours, it, it)
    }
    seconds < 604_800 -> (seconds / 86_400).let {
        context.resources.getQuantityString(R.plurals.compose_send_duration_days, it, it)
    }
    else -> (seconds / 604_800).let {
        context.resources.getQuantityString(R.plurals.compose_send_duration_weeks, it, it)
    }
}
