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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletCoreSingleton
import net.taler.wallet.backend.WalletResponse
import net.taler.wallet.backend.WalletRunConfig
import net.taler.wallet.peer.PreparePeerPushCreditResponse
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionPeerPushCredit

/**
 * Gebundener Service, der [ITalerLink] fuer Signal exportiert. Absichtlich
 * keine `android:permission` im Manifest - siehe docs/API.md Abschnitt 2.1.
 * Die Zugriffskontrolle sitzt komplett im Code, s. [CallerVerification].
 */
class TalerLinkService : Service() {

    private val consentStore by lazy { ConsentStore(applicationContext) }
    private val ownUriTracker by lazy { OwnUriTracker(applicationContext) }
    private lateinit var api: WalletBackendApi

    // Preview-Logik (preview()/previewPeerPushCredit()/.../errorResult()) ist
    // nach TalerPaymentPreviewer.kt ausgelagert - wird nicht nur vom
    // AIDL-Binder hier gebraucht, sondern auch prozessintern von der
    // Sammelkarte fuer mehrere manuell eingefuegte URIs (EnterLinkTab /
    // MultiUriSummary.kt), siehe dortige Kommentare.
    private val previewer by lazy { TalerPaymentPreviewer(api, ownUriTracker) }

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
            return runBlocking(Dispatchers.IO) { previewer.preview(kind, uri) }
        }

        override fun statusForUri(uri: String): OperationStatusResult {
            assertConnected()
            val kind = TalerUriParser.classify(uri)
                ?: throw IllegalArgumentException("Kein erkanntes Taler-URI-Schema")
            return OperationStatusResult(runBlocking(Dispatchers.IO) { previewer.preview(kind, uri) }.status)
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
        // Siehe Kommentar in TalerPaymentPreviewer.preview() zu awaitInit():
        // prepareRefund kann wie previewForUri der allererste Aufruf nach dem
        // Binden sein.
        val init = api.awaitInit()
        if (init is WalletResponse.Error) {
            throw IllegalStateException("wallet-core nicht bereit: ${init.error.code}")
        }
        if (TalerUriParser.classify(uri) != TalerUriKind.PAY_PUSH) {
            throw IllegalStateException("originalUri ist keine pay-push-URI")
        }
        if (ownUriTracker.isOwn(uri)) {
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

    companion object {
        private const val TAG = "TalerLinkService"
    }
}
