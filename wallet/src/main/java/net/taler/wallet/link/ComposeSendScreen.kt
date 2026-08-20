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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.payment.stringResId
import net.taler.wallet.peer.CheckFeeResult
import net.taler.wallet.peer.OutgoingCreating
import net.taler.wallet.peer.OutgoingError
import net.taler.wallet.peer.OutgoingResponse
import net.taler.wallet.transactions.TransactionPeerPushDebit

private const val EXPIRATION_HOURS = 24L // wie DEFAULT_EXPIRY = ExpirationOption.DAYS_1 in PeerManager.kt

// Fix round 1 (Task-A6 Review, Critical #2): Sicherheitsnetz - falls
// initiatePeerPushDebit erfolgreich eine transactionId liefert, die
// anschliessend ausgewaehlte Transaktion aber auch nach dieser Zeit noch
// keine talerUri hat, brechen wir SICHTBAR mit einem Fehler ab, statt die
// bereits committete Zahlung stillschweigend als CANCELLED zu melden.
private const val TALER_URI_TIMEOUT_MS = 15_000L

/**
 * Bestaetigungs-Screen fuer einen von Signal ueber prepareSend vorbereiteten
 * Versand (docs/API.md 2.7). Bewusst KEINE Wiederverwendung von
 * OutgoingPushScreen/OutgoingPushComposable - jener Screen hat einen eigenen
 * Scope/Exchange-Picker anhand der Guthaben des Nutzers, der zu einem bereits
 * vorausgefuellten, betragsseitig entschiedenen Flow nicht passt. Stattdessen
 * ruft dieser kleine, eigenstaendige Screen dieselben PeerManager-Funktionen
 * direkt auf. Die eigentliche ausgehende Zahlung entsteht ausschliesslich
 * durch initiatePeerPushDebit unten - ausgeloest erst durch einen Tap des
 * Nutzers auf den Send-Button in diesem (Taler-eigenen) Screen, niemals
 * automatisch.
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
    fun fireReturn(status: ReturnStatus, talerUri: String? = null) {
        if (fired || request == null) return
        fired = true
        ReturnIntentSender.fire(context, request.returnUri, request.correlationId, status, talerUri)
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
    remember { peerManager.resetPushPayment() }
    DisposableEffect(Unit) {
        onDispose {
            peerManager.resetPushPayment()
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
            fireReturn(ReturnStatus.CANCELLED)
        }
    }

    val amount = remember(request) {
        try {
            Amount.fromString(request.currency, request.amount)
        } catch (e: AmountParserException) {
            null
        }
    }

    if (amount == null) {
        // Fix (Final-Review I3): NICHT mehr sofort fireReturn/onNavigateBack
        // hinterher - nur den Fehler zeigen, siehe Begruendung am oben
        // registrierten onDispose-Hook, der den Ruecksprung uebernimmt,
        // sobald der Nutzer den Screen tatsaechlich verlaesst.
        LaunchedEffect(Unit) {
            onShowError(
                TalerErrorInfo.makeCustomError(
                    // Fix (Final-Review I5): Rule 6 - keine hartcodierten,
                    // nutzersichtbaren Strings. ErrorComposable rendert
                    // userFacingMsg auch bei devMode=false, und der Text ist
                    // ueber "Kopieren/Teilen" exportierbar.
                    context.getString(
                        R.string.compose_send_error_invalid_amount,
                        request.currency,
                        request.amount,
                    ),
                )
            )
        }
        return
    }

    // Fix round 1 (Task-A6 Review, Important #3): Amount.checkValue begrenzt
    // nur nach oben - ein "-5" oder "0" von Signal wuerde sonst klaglos ueber
    // Amount.fromString durchgehen und direkt in initiatePeerPushDebit
    // landen. Non-positive Betraege wie einen Parse-Fehler behandeln.
    if (amount.isZero() || amount.value < 0) {
        // Fix (Final-Review I3): siehe Kommentar im amount==null-Zweig oben -
        // gleiches Prinzip, kein sofortiges fireReturn/onNavigateBack mehr.
        LaunchedEffect(Unit) {
            onShowError(
                TalerErrorInfo.makeCustomError(
                    context.getString(
                        R.string.compose_send_error_non_positive_amount,
                        request.currency,
                        request.amount,
                    ),
                )
            )
        }
        return
    }

    // Minor Fix (withSpec statt bare toString): die Ein-Parameter-Variante
    // von getSpecForCurrency ist deprecated ("Please find spec via scopeInfo
    // instead"); die Zwei-Parameter-Variante (currency, scopes) ist der
    // empfohlene Ersatz und wird genau so direkt im Composable-Body
    // aufgerufen wie in TransactionDetailScreen.kt (dort mit tx.scopes).
    // balanceManager.getScopes(true) liefert dieselben "fuer Peer-Zahlungen
    // geeigneten" Scopes, die auch OutgoingPushScreen.kt fuer denselben Zweck
    // verwendet.
    val currencySpec = remember(amount.currency) {
        model.exchangeManager.getSpecForCurrency(amount.currency, model.balanceManager.getScopes(true))
    }

    var fees by remember { mutableStateOf<CheckFeeResult?>(null) }
    LaunchedEffect(amount) {
        fees = peerManager.checkPeerPushFees(amount)
    }

    val pushState by peerManager.pushState.collectAsStateLifecycleAware()

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
    BackHandler(initiated && (pushState is OutgoingCreating || pushState is OutgoingResponse)) {
        // absichtlich leer: Back wird in diesem Fenster geschluckt.
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
                // Fix (Final-Review I3): kein sofortiges fireReturn/
                // onNavigateBack mehr nach onShowError - siehe Begruendung am
                // onDispose-Hook oben. Der obere Screen bleibt (Loading- bzw.
                // Formular-Ansicht) sichtbar komponiert, die GlobalScaffold-
                // TopBar mit Zurueck-Pfeil bleibt bedienbar; erst wenn der
                // Nutzer darueber tatsaechlich verlaesst, feuert onDispose
                // CANCELLED - Signal kommt dann erst NACH dem Fehler in den
                // Vordergrund, nicht gleichzeitig damit.
                onShowError(
                    TalerErrorInfo.makeCustomError(
                        context.getString(R.string.compose_send_error_uri_timeout),
                    )
                )
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
                    OwnUriTracker.track(uri, tx.transactionId)
                    fireReturn(ReturnStatus.READY, uri)
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
            // abgeschlossen ist (OutgoingResponse, s.o.), Ladeanzeige statt
            // tippbarem Formular zeigen - verhindert, dass ein zweiter Tap
            // waehrend des fire-and-forget-Aufrufs eine zweite, verwaiste
            // Zahlung erzeugt.
            if (initiated && (pushState is OutgoingCreating || pushState is OutgoingResponse)) {
                LoadingScreen(modifier = Modifier.padding(paddingValues))
                return@GlobalScaffold
            }

            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                Text(
                    amount.withSpec(currencySpec).toString(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Fix (Final-Review I4): recipientHint ist der Anzeigename des
                // REMOTEN Signal-Kontakts - aus Talers Sicht angreifer-
                // kontrolliert. Vorher stand er mit " -> " verkettet in
                // DERSELBEN Text-Composable wie der bestaetigte Betrag
                // (maxLines=2) - ein Profilname mit fuehrendem Zeilenumbruch
                // haette eine zweite, wie eine echte Gebuehrenzeile
                // aussehende Zeile einschleusen koennen. Jetzt: eigene
                // Text-Composable, maxLines=1 (ein eingebetteter
                // Zeilenumbruch kann keine zweite sichtbare Zeile mehr
                // erzeugen) und Steuerzeichen (inkl. Zeilenumbrueche) vor der
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
                request.purpose?.let { Text(it) }
                when (val f = fees) {
                    is CheckFeeResult.Success -> {
                        // Fix round 1 (Task-A6 Review, Important #1): die
                        // "Fee"-Beschriftung muss die tatsaechliche Gebuehr
                        // zeigen (amountEffective - amountRaw), nicht den
                        // GESAMTEN abgehenden Betrag (amountEffective) -
                        // gleiche Berechnung wie OutgoingPushComposable.kt
                        // (dort unter R.string.payment_fee).
                        //
                        // Fix round 2 (Task-A6 Review, Important #1
                        // Nachtrag): Amount.minus() wirft eine
                        // AmountOverflowException, wenn die linke Seite nicht
                        // groesser als die rechte ist - die Subtraktion darf
                        // deshalb nicht unbedingt erfolgen. Exakt derselbe
                        // Guard wie OutgoingPushComposable.kt Zeile ~193
                        // (dort: "is Success -> if (res.amountEffective >
                        // res.amountRaw) { ... }"): nur wenn effektiv wirklich
                        // mehr abgeht als roh angefragt wurde, existiert eine
                        // Gebuehr und wird gezeigt. Sind beide Betraege
                        // gleich (kein Fee), wird keine Zeile angezeigt statt
                        // einer immer sichtbaren "Fee: 0"-Zeile.
                        if (f.amountEffective > f.amountRaw) {
                            val fee = f.amountEffective - f.amountRaw
                            Text(
                                stringResource(
                                    R.string.compose_send_fee_label,
                                    fee.withSpec(currencySpec).toString(),
                                )
                            )
                        }
                        // Fix (Final-Review I2): PROMPT.md's eigener
                        // Milestone-Text verlangt "Bestaetigung zeigt
                        // X+Gebuehr" - bislang wurde nur der Rohbetrag und
                        // (getrennt) die Gebuehr gezeigt, nie die tatsaechlich
                        // vom Nutzer autorisierte Gesamtsumme. amountEffective
                        // kommt direkt aus dem echten CheckFeeResult.Success
                        // (kein clientseitiges Nachrechnen ausser der bereits
                        // vorhandenen Subtraktion oben).
                        Text(
                            stringResource(
                                R.string.compose_send_total_label,
                                f.amountEffective.withSpec(currencySpec).toString(),
                            )
                        )
                    }

                    is CheckFeeResult.InsufficientBalance -> {
                        // Fix round 1 (Task-A6 Review, Important #4): bislang
                        // wurde dieser Fall komplett verschluckt (when-Zweig
                        // war "else -> Unit") - der Nutzer sah keine Fee-Zeile
                        // und konnte trotzdem tippen, was zu einem stillen
                        // OutgoingError-Abbruch fuehrte. Mindestens sichtbare
                        // Meldung zeigen, gleiches Prinzip wie
                        // OutgoingPushComposable.kt (dort ueber
                        // res.causeHint?.stringResId()).
                        Text(
                            stringResource(
                                f.causeHint?.stringResId()
                                    ?: R.string.payment_balance_insufficient
                            )
                        )
                    }

                    is CheckFeeResult.None, null -> Unit
                }
                Button(
                    // Fix round 1 (Task-A6 Review, Important #2 + #4): Button
                    // erst enabled, wenn eine erfolgreiche Fee-Pruefung
                    // vorliegt (verhindert Tap vor Abschluss von
                    // checkPeerPushFees und bei InsufficientBalance/None), und
                    // nur solange noch nicht initiiert (verhindert Doppel-Tap
                    // waehrend initiatePeerPushDebit unterwegs ist).
                    enabled = !initiated && fees is CheckFeeResult.Success,
                    onClick = {
                        initiated = true
                        peerManager.initiatePeerPushDebit(
                            amount = amount,
                            summary = request.purpose ?: "",
                            expirationHours = EXPIRATION_HOURS,
                        )
                    },
                ) {
                    Text(stringResource(R.string.compose_send_button))
                }
            }
        }
    }
}
