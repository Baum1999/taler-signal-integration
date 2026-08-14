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
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.taler.common.Timestamp
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletCoreSingleton
import net.taler.wallet.backend.WalletResponse
import net.taler.wallet.backend.WalletRunConfig
import net.taler.wallet.peer.PreparePeerPullDebitResponse
import net.taler.wallet.peer.PreparePeerPushCreditResponse
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionPeerPullDebit
import net.taler.wallet.transactions.TransactionPeerPushCredit

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
            TalerUriKind.PAY, TalerUriKind.WITHDRAW, TalerUriKind.REFUND ->
                PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerOperationStatus.UNBEKANNT_OFFLINE,
                    amount = null,
                    currency = null,
                    exchangeBaseUrl = null,
                    summary = null,
                    expirationTimestamp = null,
                )
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
     */
    private suspend fun previewPeerPushCredit(uri: String): PaymentPreviewResult {
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

    private suspend fun previewPeerPullDebit(uri: String): PaymentPreviewResult {
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
                    status = statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                )
                is TransactionPeerPullDebit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = statusFromMajorState(tx.txState.major),
                    amount = tx.amountEffective.amountStr,
                    currency = tx.amountEffective.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                )
                else -> errorResult(
                    kind,
                    TalerErrorInfo(TalerErrorCode.UNKNOWN, message = "Unerwarteter Transaktionstyp fuer $transactionId")
                )
            }
            is WalletResponse.Error -> errorResult(kind, response.error)
        }
    }

    /**
     * TransactionMajorState (TransactionState.kt) ist die verifizierte,
     * tatsaechliche Zustandsmaschine von wallet-core - nicht geraten. Dialog
     * ist der Wartezustand vor einer Nutzerentscheidung, Done heisst
     * angenommen/abgeschlossen, Expired ist ein echter, eigener Zustand (nicht
     * nur ein Fehlercode). Pending, Finalizing, Suspended, SuspendedFinalizing,
     * SuspendedAborting, Aborting, Unknown und None sind Uebergangs- bzw.
     * unklare Zustaende - bewusst konservativ als
     * UNBEKANNT_OFFLINE behandelt statt hier weiter zu spekulieren.
     */
    private fun statusFromMajorState(major: TransactionMajorState): TalerOperationStatus = when (major) {
        TransactionMajorState.Dialog -> TalerOperationStatus.OFFEN
        TransactionMajorState.Done -> TalerOperationStatus.ANGENOMMEN
        TransactionMajorState.Expired -> TalerOperationStatus.ABGELAUFEN
        TransactionMajorState.Failed,
        TransactionMajorState.Aborted,
        TransactionMajorState.Deleted -> TalerOperationStatus.UNGUELTIG
        else -> TalerOperationStatus.UNBEKANNT_OFFLINE
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
        )
    }

    companion object {
        private const val TAG = "TalerLinkService"
    }
}
