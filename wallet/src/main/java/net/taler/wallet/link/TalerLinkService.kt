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

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.taler.common.Timestamp
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletCoreSingleton
import net.taler.wallet.backend.WalletResponse
import net.taler.wallet.backend.WalletRunConfig
import net.taler.wallet.payment.PreparePayV2Response
import net.taler.wallet.peer.PreparePeerPullDebitResponse
import net.taler.wallet.peer.PreparePeerPushCreditResponse
import net.taler.wallet.refund.StartRefundQueryForUriResponse
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionPayment
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPullDebit
import net.taler.wallet.transactions.TransactionPeerPushCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit
import net.taler.wallet.transactions.TransactionRefund
import net.taler.wallet.transactions.TransactionWithdrawal
import net.taler.wallet.withdraw.PrepareBankIntegratedWithdrawalResponse
import net.taler.wallet.withdraw.WithdrawExchangeResponse

/**
 * Gebundener Service, der [ITalerLink] fuer Signal exportiert. Absichtlich
 * keine `android:permission` im Manifest - siehe docs/API.md Abschnitt 2.1.
 * Die Zugriffskontrolle sitzt komplett im Code, s. [CallerVerification].
 */
class TalerLinkService : Service() {

    private val consentStore by lazy { ConsentStore(applicationContext) }
    private lateinit var api: WalletBackendApi

    private val binder = object : ITalerLink.Stub() {

        override fun getConnectionState(): ConnectionStateResult {
            val callerPackage = CallerVerification.assertCallerAllowed(this@TalerLinkService)
            val certSha256 = CallerVerification.signingCertSha256(packageManager, callerPackage)
                ?: throw SecurityException(
                    "Signatur des Aufrufers ($callerPackage) nicht ermittelbar"
                )
            val state = if (consentStore.isGranted(certSha256)) {
                ConnectionState.VERBUNDEN
            } else {
                ConnectionState.NICHT_VERBUNDEN
            }
            return ConnectionStateResult(state)
        }

        override fun validateUri(uri: String): UriValidationResult {
            assertConnected()
            val validity = if (TalerUriParser.classify(uri) != null) {
                TalerUriValidity.GUELTIG
            } else {
                TalerUriValidity.UNGUELTIG
            }
            return UriValidationResult(validity)
        }

        // B2 (REVIEW.md), Teil "kein prepare* beim Polling mehr": previewForUri
        // und statusForUri rufen bei JEDEM Aufruf erneut preparePeerPushCredit/
        // preparePeerPullDebit auf, auch beim wiederholten Poll derselben URI.
        // Bewusst so belassen in diesem Meilenstein, nicht vergessen:
        // - Offene Frage 1 ist inzwischen empirisch beantwortet (REVIEW.md,
        //   Meilenstein 0): preparePeerPushCredit ist idempotent, wiederholter
        //   Aufruf legt keine neue Transaktion/keinen neuen Purse an.
        // - Der im Review empfohlene Weg (Taler-seitiges In-Memory
        //   uri->transactionId in diesem Service) ist auf dem Testgeraet
        //   NICHT tragfaehig: TalerLinkClient (Signal-Seite) bindet pro
        //   Aufruf neu und unbindet sofort danach; im gemessenen Poll-Lauf
        //   (9 Zyklen a 20s) hat das System TalerLinkService bei jedem
        //   einzelnen Zyklus tatsaechlich zerstoert und neu erzeugt (adb
        //   logcat: destroyService/bindService-Paar pro Zyklus) - eine
        //   In-Memory-Map in dieser Klasse wuerde also bei jedem Poll leer
        //   sein und nichts sparen.
        // - Die im Review genannte Alternative (transactionId in Signals
        //   TalerPaymentTable persistieren + neue AIDL-Methode) wuerde
        //   funktionieren, ist aber bewusst zurueckgestellt: neue
        //   Binder-Flaeche + neue Migration fuer einen Punkt, den der Review
        //   selbst bei bestaetigter Idempotenz als "weniger dringend"
        //   einstuft. Cap+TTL+Backoff (unten/TalerPollingCoordinator) binden
        //   die Aufrufzahl bereits nach oben, unabhaengig davon, ob jeder
        //   einzelne Aufruf intern prepare* macht.
        override fun previewForUri(uri: String): PaymentPreviewResult {
            assertConnected()
            // B1 (REVIEW.md): die URI ist ein Inhaberpapier und darf nicht in
            // die Exception-Message - die geht ueber den Binder und landet im
            // Stacktrace des Aufrufers (Signal-Seite, ggf. Logs/Crash-Reports
            // dort).
            val kind = TalerUriParser.classify(uri)
                ?: throw IllegalArgumentException("Kein erkanntes Taler-URI-Schema")
            return runBlocking(Dispatchers.IO) { preview(kind, uri) }
        }

        override fun statusForUri(uri: String): OperationStatusResult {
            assertConnected()
            val kind = TalerUriParser.classify(uri)
                ?: throw IllegalArgumentException("Kein erkanntes Taler-URI-Schema")
            return OperationStatusResult(runBlocking(Dispatchers.IO) { preview(kind, uri) }.status)
        }

        override fun prepareSend(request: PrepareSendRequest): PrepareSendResult {
            assertConnected()
            PendingSendStore.put(request)
            val deepLink = "talerlink://compose-send?correlationId=${Uri.encode(request.correlationId)}"
            return PrepareSendResult(deepLink)
        }

        /**
         * Meilenstein 6. Anders als prepareSend: originalUri wird hier schon
         * am Aufrufzeitpunkt aufgeloest (nicht erst im Compose-Screen), damit
         * eine ungueltige originalUri (keine pay-push-URI, selbst versendet,
         * unbekannte Transaktion) sofort und synchron mit dem
         * prepareRefund-Aufruf scheitert, statt Signal erst einen deepLink zu
         * geben und dann im Compose-Screen zu scheitern. Bewusst kein
         * Betrag/Zweck-Feld in [PrepareRefundRequest] - Taler leitet beides
         * aus der eigenen Transaktion zu originalUri ab (docs/API.md 2.4).
         */
        override fun prepareRefund(request: PrepareRefundRequest): PrepareRefundResult {
            assertConnected()
            val transactionId = runBlocking(Dispatchers.IO) {
                resolveReceivedPeerPushCreditId(request.originalUri)
            }
            PendingRefundStore.put(request, transactionId)
            val deepLink = "talerlink://compose-refund?correlationId=${Uri.encode(request.correlationId)}"
            return PrepareRefundResult(deepLink)
        }

        /**
         * Wirft [IllegalStateException], falls der Aufrufer zwar in der
         * Allowlist steht, aber noch kein Consent erteilt wurde - siehe
         * docs/API.md Abschnitt 2.6. [getConnectionState] ist bewusst die
         * einzige Methode ohne diese Pruefung.
         */
        private fun assertConnected() {
            val callerPackage = CallerVerification.assertCallerAllowed(this@TalerLinkService)
            val certSha256 = CallerVerification.signingCertSha256(packageManager, callerPackage)
                ?: throw SecurityException(
                    "Signatur des Aufrufers ($callerPackage) nicht ermittelbar"
                )
            if (!consentStore.isGranted(certSha256)) {
                throw IllegalStateException("Consent fehlt fuer $callerPackage")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Config bewusst ohne Dev-Mode/Observability - der Service ist kein
        // Debug-UI-Kontext. Siehe WalletCoreSingleton: wird nur tatsaechlich
        // verwendet, falls der Service als Erster (vor MainActivity) die
        // wallet-core-Instanz startet.
        val config = WalletRunConfig(
            testing = WalletRunConfig.Testing(
                emitObservabilityEvents = false,
                devModeActive = false,
            ),
            features = WalletRunConfig.Features(
                enableV1Contracts = true,
            ),
            logLevel = "INFO",
        )
        api = WalletCoreSingleton.acquire(application, config)
    }

    override fun onDestroy() {
        WalletCoreSingleton.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "gebunden von uid=${Binder.getCallingUid()}")
        return binder
    }

    /**
     * Fuehrt den zu [kind] passenden wallet-core-Aufruf aus und bildet das
     * Ergebnis auf [PaymentPreviewResult] ab.
     *
     * Nur PAY_PUSH und PAY_PULL sind in diesem Schritt vollstaendig an
     * wallet-core angebunden (deckt den DoD-Ablauf ab: pay-push senden,
     * empfangen, annehmen). PAY braucht zusaetzlich einen zweiten Aufruf
     * (getChoicesForPayment) fuer Betrag/Zweck, WITHDRAW/REFUND haben andere
     * Response-Formen - beides hier bewusst nicht geraten, sondern als
     * offener Punkt zurueckgestellt (siehe Statusbericht/docs/API.md).
     */
    private suspend fun preview(kind: TalerUriKind, uri: String): PaymentPreviewResult =
        when (kind) {
            TalerUriKind.PAY_PUSH -> previewPeerPushCredit(uri)
            TalerUriKind.PAY_PULL -> previewPeerPullDebit(uri)
            TalerUriKind.PAY -> previewPay(uri)
            TalerUriKind.WITHDRAW -> previewWithdraw(uri)
            TalerUriKind.REFUND -> previewRefund(uri)
        }

    /**
     * preparePeerPushCredit liefert selbst schon exchangeBaseUrl, aber kein
     * Ablaufdatum und keinen belastbaren Status (die Transaktion existiert
     * danach nur im Dialog-Zustand). Deshalb der Umweg ueber
     * getTransactionById(transactionId) - dort steht in [TransactionPeerPushCredit]
     * alles inklusive echtem txState. Gleiches Muster wie preparePeerPullDebit
     * unten, damit previewForUri/statusForUri auch bei erneutem Aufruf (Polling)
     * den tatsaechlichen, ggf. inzwischen in der Taler-UI veraenderten Zustand
     * sehen - nicht nur den Moment des prepare-Aufrufs selbst.
     *
     * Fix (reproduzierbar: Karte zeigt "Zahlung fehlgeschlagen"/0 KUDOS beim
     * Absender, sobald die Gegenseite annimmt, obwohl Talers eigene
     * Transaktionshistorie die eigene peer-push-debit-Transaktion korrekt als
     * "erfolgreich durchgefuehrt" mit dem echten Betrag zeigt): fuer eine URI,
     * die diese Wallet-Instanz selbst per initiatePeerPushDebit erzeugt hat,
     * darf NIE preparePeerPushCredit aufgerufen werden - das praepariert eine
     * NEUE, eingehende Credit-Transaktion fuer dieselbe Purse, nicht die
     * eigene ausgehende. Sobald die Gegenseite die Purse bereits
     * gemerged/geclaimt hat, schlaegt dieser zweite, ueberfluessige
     * prepare-Aufruf auf derselben Purse fehl - unabhaengig vom Netzwerk.
     * [OwnUriTracker] haelt fuer genau diesen Fall schon die echte
     * transactionId der eigenen [TransactionPeerPushDebit] vor (siehe dort);
     * die per getTransactionById direkt nachschlagen statt ueber
     * preparePeerPushCredit. [detailsFor] erkennt den Transaktionstyp dabei
     * selbst und setzt isOwnPayment korrekt - der vorherige nachtraegliche
     * isOwnPayment-Override ueber OwnUriTracker.isOwn() entfaellt damit.
     */
    private suspend fun previewPeerPushCredit(uri: String): PaymentPreviewResult {
        OwnUriTracker.transactionIdFor(uri)?.let { ownTransactionId ->
            return detailsFor(TalerUriKind.PAY_PUSH, ownTransactionId)
        }

        val prepared = api.request(
            "preparePeerPushCredit",
            PreparePeerPushCreditResponse.serializer(),
        ) {
            put("talerUri", uri)
        }
        val transactionId = when (prepared) {
            is WalletResponse.Success -> prepared.result.transactionId
            is WalletResponse.Error -> return errorResult(TalerUriKind.PAY_PUSH, prepared.error)
        }
        return detailsFor(TalerUriKind.PAY_PUSH, transactionId)
    }

    /**
     * Loest originalUri auf die transactionId einer tatsaechlich EMPFANGENEN
     * (nicht selbst versendeten) pay-push-Transaktion auf - fuer
     * prepareRefund. Prueft das eigenstaendig nach, unabhaengig davon, was
     * Signal ueber die Karte behauptet hat (eiserne Regel 4): Signal darf
     * "Refund" nur fuer eine eigene, angenommene, eingehende Zahlung anbieten,
     * aber Taler ist hier die massgebliche Quelle, nicht der Aufrufer.
     *
     * Wirft IllegalStateException bei jeder Art von Nicht-Aufloesbarkeit
     * (falscher URI-Typ, selbst versendet, unbekannte/nicht auffindbare
     * Transaktion) - eine echte, differenzierte Fehler-Rueckgabe ist mangels
     * Fehlervariante in PrepareRefundResult (docs/API.md 2.4) hier nicht
     * vorgesehen; siehe Meilenstein-6-Bericht fuer diese offene Frage. Die
     * Meldungen selbst nennen bewusst weder URI noch Betrag (B1 - geht ueber
     * den Binder in den Stacktrace des Aufrufers).
     */
    private suspend fun resolveReceivedPeerPushCreditId(uri: String): String {
        if (TalerUriParser.classify(uri) != TalerUriKind.PAY_PUSH) {
            throw IllegalStateException("originalUri ist keine pay-push-URI")
        }
        if (OwnUriTracker.isOwn(uri)) {
            throw IllegalStateException("originalUri ist eine selbst versendete Zahlung, keine empfangene")
        }
        val prepared = api.request(
            "preparePeerPushCredit",
            PreparePeerPushCreditResponse.serializer(),
        ) {
            put("talerUri", uri)
        }
        val transactionId = when (prepared) {
            is WalletResponse.Success -> prepared.result.transactionId
            is WalletResponse.Error -> throw IllegalStateException("originalUri nicht aufloesbar: ${prepared.error.code}")
        }
        val response = api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", transactionId)
        }
        val tx = when (response) {
            is WalletResponse.Success -> response.result
            is WalletResponse.Error -> throw IllegalStateException("Transaktion nicht auffindbar: ${response.error.code}")
        }
        if (tx !is TransactionPeerPushCredit) {
            throw IllegalStateException("originalUri ist keine PeerPushCredit-Transaktion")
        }
        return transactionId
    }

    /**
     * Bug 4 Fix: anders als preparePeerPushCredit ist preparePeerPullDebit nicht
     * als idempotent nach einer bereits abgeschlossenen Zahlung verifiziert -
     * ein erneuter Aufruf auf eine schon bezahlte pay-pull-URI (z.B. beim
     * naechsten Routine-Poll, nachdem der Nutzer ausserhalb von Signal direkt
     * in Taler bezahlt hat) kann eine neue, fehlgeschlagene Transaktion
     * erzeugen statt die bestehende zurueckzugeben. Deshalb: ist fuer diese
     * URI bereits eine transactionId bekannt (PeerPullDebitCache, siehe dort),
     * wird preparePeerPullDebit fuer sie nicht mehr aufgerufen - der aktuelle
     * Zustand kommt dann nur noch aus getTransactionById.
     */
    private suspend fun previewPeerPullDebit(uri: String): PaymentPreviewResult {
        val cachedTransactionId = PeerPullDebitCache.cachedTransactionId(uri)
        if (cachedTransactionId != null) {
            return detailsFor(TalerUriKind.PAY_PULL, cachedTransactionId)
        }
        val prepared = api.request(
            "preparePeerPullDebit",
            PreparePeerPullDebitResponse.serializer(),
        ) {
            put("talerUri", uri)
        }
        val transactionId = when (prepared) {
            is WalletResponse.Success -> prepared.result.transactionId
            is WalletResponse.Error -> return errorResult(TalerUriKind.PAY_PULL, prepared.error)
        }
        PeerPullDebitCache.remember(uri, transactionId)
        return detailsFor(TalerUriKind.PAY_PULL, transactionId)
    }

    private suspend fun detailsFor(kind: TalerUriKind, transactionId: String): PaymentPreviewResult {
        val response = api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", transactionId)
        }
        return when (response) {
            is WalletResponse.Success -> when (val tx = response.result) {
                is TransactionPeerPushCredit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = false, // PeerPushCredit = incoming (we received money from someone else)
                )
                is TransactionPeerPushDebit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = true, // PeerPushDebit = outgoing (we sent money to someone else)
                )
                is TransactionPeerPullDebit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = true, // PeerPullDebit = outgoing (we paid someone's invoice)
                )
                is TransactionPeerPullCredit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = false, // PeerPullCredit = incoming (someone paid our invoice)
                )
                else -> errorResult(
                    kind,
                    TalerErrorInfo(TalerErrorCode.UNKNOWN, message = "Unerwarteter Transaktionstyp fuer $transactionId")
                )
            }
            is WalletResponse.Error -> errorResult(kind, response.error)
        }
    }

    /** Timestamp.never() ist intern t_s = -1 (siehe Time.kt) - "laeuft nie ab". */
    private fun expirationMillisOrNull(timestamp: Timestamp?): Long? {
        if (timestamp == null || timestamp.ms < 0) return null
        return timestamp.ms
    }

    /**
     * Nur EXCHANGE_GENERIC_PURSE_EXPIRED ist als Fehlercode fuer "abgelaufen"
     * verifiziert (TalerErrorCode.kt: "The purse has expired.") - greift, wenn
     * schon der prepare*-Aufruf selbst fehlschlaegt (also bevor ueberhaupt ein
     * txState existiert). Alle anderen Fehler bewusst konservativ als
     * UNBEKANNT_OFFLINE behandelt, statt weitere Fehlercodes zu erraten.
     */
    private fun errorResult(kind: TalerUriKind, error: TalerErrorInfo): PaymentPreviewResult {
        val status = if (error.code == TalerErrorCode.EXCHANGE_GENERIC_PURSE_EXPIRED) {
            TalerOperationStatus.ABGELAUFEN
        } else {
            TalerOperationStatus.UNBEKANNT_OFFLINE
        }
        return PaymentPreviewResult(
            uriKind = kind,
            status = status,
            amount = null,
            currency = null,
            exchangeBaseUrl = null,
            summary = null,
            expirationTimestamp = null,
            isOwnPayment = false,
        )
    }

    /**
     * Vorschau fuer eine taler://pay/-URI. Ruft preparePayForUriV2 auf, um die
     * Transaktions-ID zu erhalten, dann getTransactionById fuer die Details.
     * Pay-Transaktionen sind immer ausgehende Zahlungen (der Nutzer zahlt).
     */
    private suspend fun previewPay(uri: String): PaymentPreviewResult {
        val prepared = api.request(
            "preparePayForUriV2",
            PreparePayV2Response.serializer(),
        ) {
            put("talerPayUri", uri)
        }
        val transactionId = when (prepared) {
            is WalletResponse.Success -> prepared.result.transactionId
            is WalletResponse.Error -> return errorResult(TalerUriKind.PAY, prepared.error)
        }
        val response = api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", transactionId)
        }
        return when (response) {
            is WalletResponse.Success -> when (val tx = response.result) {
                is TransactionWithdrawal -> errorResult(TalerUriKind.PAY, TalerErrorInfo(TalerErrorCode.UNKNOWN))
                is TransactionRefund -> errorResult(TalerUriKind.PAY, TalerErrorInfo(TalerErrorCode.UNKNOWN))
                is TransactionPayment -> PaymentPreviewResult(
                    uriKind = TalerUriKind.PAY,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = null,
                    summary = tx.info?.summary,
                    expirationTimestamp = null,
                    isOwnPayment = true, // Pay = outgoing payment
                )
                else -> errorResult(TalerUriKind.PAY, TalerErrorInfo(TalerErrorCode.UNKNOWN, message = "Unerwarteter Transaktionstyp: ${tx::class.simpleName}"))
            }
            is WalletResponse.Error -> errorResult(TalerUriKind.PAY, response.error)
        }
    }

    /**
     * Vorschau fuer eine taler://withdraw/ oder taler://withdraw-exchange/-URI.
     * Unterscheidet zwischen den beiden URI-Typen und ruft den passenden
     * wallet-core-Endpunkt auf.
     * Withdraw-Transaktionen sind immer eingehend im Sinne von
     * "Geld fließt in die Wallet" (der Nutzer holt Geld von einem Exchange ab),
     * aber semantisch ist es KEINE eingehende Zahlung von jemand anderem -
     * isOwnPayment bleibt false, aber die UI sollte klar machen, dass es um
     * "Geld abheben" geht, nicht um "Geld empfangen".
     */
    private suspend fun previewWithdraw(uri: String): PaymentPreviewResult {
        val normalized = uri.trim().lowercase(Locale.ROOT)
        val action = when {
            normalized.startsWith("taler://") -> normalized.removePrefix("taler://")
            normalized.startsWith("ext+taler://") -> normalized.removePrefix("ext+taler://")
            normalized.startsWith("taler+http://") -> normalized.removePrefix("taler+http://")
            else -> normalized
        }
        
        return if (action.startsWith("withdraw-exchange/")) {
            previewWithdrawExchange(uri, action)
        } else {
            previewWithdrawStandard(uri)
        }
    }

    private suspend fun previewWithdrawStandard(uri: String): PaymentPreviewResult {
        val response = api.request(
            "prepareBankIntegratedWithdrawal",
            PrepareBankIntegratedWithdrawalResponse.serializer(),
        ) {
            put("talerWithdrawUri", uri)
        }
        return when (response) {
            is WalletResponse.Success -> {
                val info = response.result.info
                PaymentPreviewResult(
                    uriKind = TalerUriKind.WITHDRAW,
                    status = TalerOperationStatus.OFFEN,
                    amount = info.amount?.amountStr,
                    currency = info.currency,
                    exchangeBaseUrl = info.defaultExchangeBaseUrl,
                    summary = "Withdraw",
                    expirationTimestamp = null,
                    isOwnPayment = false,
                )
            }
            is WalletResponse.Error -> errorResult(TalerUriKind.WITHDRAW, response.error)
        }
    }

    private suspend fun previewWithdrawExchange(uri: String, action: String): PaymentPreviewResult {
        val response = api.request(
            "prepareWithdrawExchange",
            WithdrawExchangeResponse.serializer(),
        ) {
            put("talerWithdrawUri", uri)
        }
        return when (response) {
            is WalletResponse.Success -> {
                val result = response.result
                PaymentPreviewResult(
                    uriKind = TalerUriKind.WITHDRAW,
                    status = TalerOperationStatus.OFFEN,
                    amount = result.amount?.amountStr,
                    currency = result.amount?.currency,
                    exchangeBaseUrl = result.exchangeBaseUrl,
                    summary = "Withdraw",
                    expirationTimestamp = null,
                    isOwnPayment = false,
                )
            }
            is WalletResponse.Error -> errorResult(TalerUriKind.WITHDRAW, response.error)
        }
    }

    /**
     * Vorschau fuer eine taler://refund/-URI. Ruft startRefundQueryForUri auf, um
     * die Transaktions-ID zu erhalten, dann getTransactionById fuer die Details.
     * Refund-Transaktionen sind immer ausgehende Aktionen (der Nutzer gibt Geld
     * zurueck), auch wenn sie technisch eine eigene Transaktion erstellen.
     */
    private suspend fun previewRefund(uri: String): PaymentPreviewResult {
        val prepared = api.request(
            "startRefundQueryForUri",
            StartRefundQueryForUriResponse.serializer(),
        ) {
            put("talerRefundUri", uri)
        }
        val transactionId = when (prepared) {
            is WalletResponse.Success -> prepared.result.transactionId
            is WalletResponse.Error -> return errorResult(TalerUriKind.REFUND, prepared.error)
        }
        val response = api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", transactionId)
        }
        return when (response) {
            is WalletResponse.Success -> when (val tx = response.result) {
                is TransactionRefund -> PaymentPreviewResult(
                    uriKind = TalerUriKind.REFUND,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = null,
                    summary = tx.paymentInfo?.summary,
                    expirationTimestamp = null,
                    isOwnPayment = true, // Refund = outgoing (user is giving money back)
                )
                else -> errorResult(TalerUriKind.REFUND, TalerErrorInfo(TalerErrorCode.UNKNOWN, message = "Unerwarteter Transaktionstyp: ${tx::class.simpleName}"))
            }
            is WalletResponse.Error -> errorResult(TalerUriKind.REFUND, response.error)
        }
    }

    companion object {
        private const val TAG = "TalerLinkService"
    }
}
