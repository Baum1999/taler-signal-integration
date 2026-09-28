/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.taler.common.Amount
import net.taler.common.RelativeTime
import net.taler.common.Timestamp
import net.taler.wallet.main.TAG
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletResponse
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.ExchangeManager
import net.taler.wallet.exchanges.ExchangeTosStatus
import net.taler.wallet.payment.InsufficientBalanceHint
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPushCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit
import org.json.JSONObject
import java.util.concurrent.TimeUnit.HOURS
import net.taler.wallet.peer.CheckPeerPushDebitResponse.*

const val MAX_LENGTH_SUBJECT = 100
val DEFAULT_EXPIRY = ExpirationOption.DAYS_1

/**
 * Basis-Backoff (mit dem Versuchszaehler multipliziert) fuer Retries nach
 * einem INSUFFICIENT_BALANCE-Fehlschlag bei einer Gruppen-Split-Share -
 * siehe initiatePeerPushDebitShareWithRetry.
 */
private const val INSUFFICIENT_BALANCE_BACKOFF_MS = 2_000L

sealed class CheckFeeResult {
    abstract val maxDepositAmountEffective: Amount?
    abstract val maxDepositAmountRaw: Amount?

    data class None(
        override val maxDepositAmountEffective: Amount? = null,
        override val maxDepositAmountRaw: Amount? = null,
    ): CheckFeeResult()

    data class InsufficientBalance(
        val maxAmountEffective: Amount?,
        val maxAmountRaw: Amount?,
        val causeHint: InsufficientBalanceHint? = null,
        override val maxDepositAmountEffective: Amount? = null,
        override val maxDepositAmountRaw: Amount? = null,
    ): CheckFeeResult()

    data class Success(
        val amountRaw: Amount,
        val amountEffective: Amount,
        val exchangeBaseUrl: String,
        val defaultExpiration: RelativeTime,
        override val maxDepositAmountEffective: Amount? = null,
        override val maxDepositAmountRaw: Amount? = null,
    ): CheckFeeResult()
}

@Serializable
data class GetMaxPeerPushDebitAmountResponse(
    val effectiveAmount: Amount,
    val rawAmount: Amount,
    val exchangeBaseUrl: String? = null,
)

/**
 * Echte Endzustaende der wallet-core-Transaktions-Zustandsmaschine (siehe
 * TransactionState.kt) - dieselbe Klassifikation wie
 * TalerTransactionStateMapper.statusFromMajorState (dort Failed/Aborted/
 * Deleted -> UNGUELTIG, Expired -> ABGELAUFEN). Alles ausserhalb dieser Menge
 * ist Uebergangszustand, in dem waitForShareTalerUri weiterpollen muss statt
 * abzubrechen.
 */
private val TERMINAL_FAILURE_STATES = setOf(
    TransactionMajorState.Failed,
    TransactionMajorState.Aborted,
    TransactionMajorState.Deleted,
    TransactionMajorState.Expired,
)

class PeerManager(
    private val api: WalletBackendApi,
    private val exchangeManager: ExchangeManager,
    private val scope: CoroutineScope,
) {

    private val _outgoingPullState = MutableStateFlow<OutgoingState>(OutgoingIntro)
    val pullState: StateFlow<OutgoingState> = _outgoingPullState

    private val _outgoingPushState = MutableStateFlow<OutgoingState>(OutgoingIntro)
    val pushState: StateFlow<OutgoingState> = _outgoingPushState

    private val _groupPushState = MutableStateFlow<GroupPushState>(GroupPushState.Idle)
    val groupPushState: StateFlow<GroupPushState> = _groupPushState

    private val _groupPullState = MutableStateFlow<GroupPullState>(GroupPullState.Idle)
    val groupPullState: StateFlow<GroupPullState> = _groupPullState

    private val _incomingPullState = MutableStateFlow<IncomingState>(IncomingChecking)
    val incomingPullState: StateFlow<IncomingState> = _incomingPullState

    private val _incomingPushState = MutableStateFlow<IncomingState>(IncomingChecking)
    val incomingPushState: StateFlow<IncomingState> = _incomingPushState

    /**
     * Ruecksprung-Info fuer den aktuell laufenden "Annehmen"-Vorgang
     * (docs/API.md 2.10) - gesetzt von HandleUriScreen vor preparePeerPushCredit,
     * gelesen+geloescht von IncomingPushPaymentScreen beim Abschluss/Verlassen
     * des Bestaetigungsbildschirms. Absichtlich kein Taler-interner Zustand
     * (keine Persistenz noetig) - ein neuer preparePeerPushCredit-Aufruf
     * ueberschreibt ihn ohnehin.
     */
    var pendingReturnCallback: net.taler.wallet.link.ReturnCallbackInfo? = null

    suspend fun checkPeerPullCredit(
        amount: Amount,
        scopeInfo: ScopeInfo,
        loading: Boolean = false,
    ): CheckPeerPullCreditResult? {
        var response: CheckPeerPullCreditResult? = null
        val exchangeItem = exchangeManager.findExchange(scopeInfo) ?: return null

        if (loading) {
            _outgoingPullState.value = OutgoingChecking
        }

        if (!exchangeItem.tosStatus.isAccepted()) {
            _outgoingPullState.value = OutgoingIntro
            return CheckPeerPullCreditResult(
                tosStatus = exchangeItem.tosStatus,
                exchangeBaseUrl = exchangeItem.exchangeBaseUrl,
            )
        } else if (amount.isZero()) {
            _outgoingPullState.value = OutgoingIntro
            return CheckPeerPullCreditResult(
                tosStatus = exchangeItem.tosStatus,
                exchangeBaseUrl = exchangeItem.exchangeBaseUrl,
                amountRaw = amount,
                amountEffective = amount,
            )
        }

        api.request("checkPeerPullCredit", CheckPeerPullCreditResponse.serializer()) {
            put("restrictScope", JSONObject(BackendManager.json.encodeToString(scopeInfo)))
            put("amount", amount.toJSONString())
        }.onSuccess {
            response = CheckPeerPullCreditResult(
                amountEffective = it.amountEffective,
                amountRaw = it.amountRaw,
                exchangeBaseUrl = it.exchangeBaseUrl,
                tosStatus = exchangeItem.tosStatus,
            )
        }.onError { error ->
            Log.e(TAG, "got checkPeerPullCredit error result $error")
        }

        if (loading) {
            _outgoingPullState.value = OutgoingIntro
        }

        return response
    }

    fun initiatePeerPullCredit(amount: Amount, summary: String, expirationHours: Long, exchangeBaseUrl: String) {
        _outgoingPullState.value = OutgoingCreating
        scope.launch(Dispatchers.IO) {
            requestInitiatePeerPullCredit(amount, summary, expirationHours, exchangeBaseUrl)
                .onSuccess {
                    _outgoingPullState.value = OutgoingResponse(it.transactionId)
                }.onError { error ->
                    Log.e(TAG, "got initiatePeerPullCredit error result $error")
                    _outgoingPullState.value = OutgoingError(error)
                }
        }
    }

    fun resetPullPayment() {
        _outgoingPullState.value = OutgoingIntro
    }

    /**
     * Reiner Netzwerk-Aufruf ohne Seiteneffekt auf _outgoingPullState - von
     * initiatePeerPullCredit (Einzel-Anfordern-Pfad, ein Aufruf) UND
     * initiatePeerPullCreditGroup (Gruppen-Split-Anfordern-Pfad, N parallele
     * Aufrufe, siehe unten) genutzt, damit die Anfrage-Konstruktion nur an
     * einer Stelle steht - gleiches Muster wie requestInitiatePeerPushDebit.
     */
    private suspend fun requestInitiatePeerPullCredit(
        amount: Amount,
        summary: String,
        expirationHours: Long,
        exchangeBaseUrl: String,
    ): WalletResponse<InitiatePeerPullPaymentResponse> {
        val expiry = Timestamp.fromMillis(System.currentTimeMillis() + HOURS.toMillis(expirationHours))
        return api.request("initiatePeerPullCredit", InitiatePeerPullPaymentResponse.serializer()) {
            put("exchangeBaseUrl", exchangeBaseUrl)
            put("partialContractTerms", JSONObject().apply {
                put("amount", amount.toJSONString())
                put("summary", summary)
                put("purse_expiration", JSONObject(Json.encodeToString(expiry)))
            })
        }
    }

    /**
     * Pendant zu [initiatePeerPushDebitGroup] fuer den "Anfordern"-Pfad:
     * erzeugt [count] unabhaengige peer-pull-credit-Invoices parallel (eine
     * pro Gruppen-Mitglied). Gleiche Retry-/Teilerfolg-Semantik wie beim
     * Push-Pendant - siehe dortige Kommentare.
     */
    suspend fun initiatePeerPullCreditGroup(
        count: Int,
        amountPerShare: Amount,
        summary: String,
        expirationHours: Long,
        exchangeBaseUrl: String,
        maxRetries: Int = 2,
    ): List<ShareResult> {
        require(count > 0) { "count must be positive, was $count" }
        return coroutineScope {
            (0 until count).map {
                async(Dispatchers.IO) {
                    initiatePeerPullCreditShareWithRetry(
                        amountPerShare, summary, expirationHours, exchangeBaseUrl, maxRetries,
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * UI-Einstiegspunkt fuer den Gruppen-Split-Anfordern-Pfad - Fire-and-
     * forget-Wrapper um [initiatePeerPullCreditGroup], analog zu
     * [initiatePeerPushDebitGroupAsync] fuer den Push-Pfad.
     */
    fun initiatePeerPullCreditGroupAsync(
        count: Int,
        amountPerShare: Amount,
        summary: String,
        expirationHours: Long,
        exchangeBaseUrl: String,
    ) {
        _groupPullState.value = GroupPullState.InProgress(count)
        scope.launch(Dispatchers.IO) {
            val results = initiatePeerPullCreditGroup(count, amountPerShare, summary, expirationHours, exchangeBaseUrl)
            _groupPullState.value = GroupPullState.Done(results)
        }
    }

    fun resetGroupPullState() {
        _groupPullState.value = GroupPullState.Idle
    }

    private suspend fun initiatePeerPullCreditShareWithRetry(
        amount: Amount,
        summary: String,
        expirationHours: Long,
        exchangeBaseUrl: String,
        maxRetries: Int,
    ): ShareResult {
        var transactionId: String? = null
        var attempt = 0
        while (true) {
            val result: ShareResult = if (transactionId == null) {
                when (val response = requestInitiatePeerPullCredit(amount, summary, expirationHours, exchangeBaseUrl)) {
                    is WalletResponse.Success -> {
                        val id = response.result.transactionId
                        transactionId = id
                        waitForPullShareTalerUri(id)
                    }
                    is WalletResponse.Error -> ShareResult.Failure(null, response.error)
                }
            } else {
                when (val retryResponse = api.request<Unit>("retryTransaction") {
                    put("transactionId", transactionId)
                }) {
                    is WalletResponse.Success -> waitForPullShareTalerUri(transactionId)
                    is WalletResponse.Error -> ShareResult.Failure(transactionId, retryResponse.error)
                }
            }
            if (result is ShareResult.Success) return result
            result as ShareResult.Failure
            transactionId = result.transactionId ?: transactionId
            attempt++
            if (attempt > maxRetries) return ShareResult.Failure(transactionId, result.error)
        }
    }

    /** Pendant zu [waitForShareTalerUri] fuer TransactionPeerPullCredit. */
    private suspend fun waitForPullShareTalerUri(
        transactionId: String,
        pollIntervalMs: Long = 2_000L,
        timeoutMs: Long = 600_000L,
    ): ShareResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (val response = api.request("getTransactionById", Transaction.serializer()) {
                put("transactionId", transactionId)
            }) {
                is WalletResponse.Success -> {
                    val tx = response.result
                    val talerUri = (tx as? TransactionPeerPullCredit)?.talerUri
                    if (talerUri != null) return ShareResult.Success(transactionId, talerUri)
                    if (tx.txState.major in TERMINAL_FAILURE_STATES) {
                        return ShareResult.Failure(
                            transactionId,
                            tx.error ?: TalerErrorInfo.makeCustomError(
                                "Transaction ended in terminal state ${tx.txState.major} (group share)",
                            ),
                        )
                    }
                }
                is WalletResponse.Error -> return ShareResult.Failure(transactionId, response.error)
            }
            delay(pollIntervalMs)
        }
        return ShareResult.Failure(
            transactionId,
            TalerErrorInfo.makeCustomError("Timed out waiting for payment URI (group share)"),
        )
    }

    suspend fun checkPeerPushFees(
        amount: Amount,
        exchangeBaseUrl: String? = null,
        restrictScope: ScopeInfo? = null,
    ): CheckFeeResult {
        val max = getMaxPeerPushDebitAmount(amount.currency, exchangeBaseUrl, restrictScope = restrictScope)
        var response: CheckFeeResult = CheckFeeResult.None(
            maxDepositAmountEffective = max?.effectiveAmount,
            maxDepositAmountRaw = max?.rawAmount,
        )

        api.request("checkPeerPushDebitV2", CheckPeerPushDebitResponse.serializer()) {
            exchangeBaseUrl?.let { put("exchangeBaseUrl", it) }
            restrictScope?.let { put("restrictScope", JSONObject(BackendManager.json.encodeToString(it))) }
            put("amount", amount.toJSONString())
        }.onSuccess { res ->
            response = when (val r = res) {
                is CheckPeerPushDebitOkResponse -> CheckFeeResult.Success(
                    amountRaw = r.amountRaw,
                    amountEffective = r.amountEffective,
                    maxDepositAmountEffective = max?.effectiveAmount,
                    maxDepositAmountRaw = max?.rawAmount,
                    exchangeBaseUrl = r.exchangeBaseUrl,
                    defaultExpiration = r.defaultExpiration,
                )

                is CheckPeerPushDebitInsufficientBalanceResponse -> CheckFeeResult.InsufficientBalance(
                    maxAmountEffective = r.insufficientBalanceDetails.maxEffectiveSpendAmount,
                    maxAmountRaw = r.insufficientBalanceDetails.balanceAvailable,
                    maxDepositAmountEffective = max?.effectiveAmount,
                    maxDepositAmountRaw = max?.rawAmount,
                    causeHint = r.insufficientBalanceDetails.causeHint,
                )
            }
        }.onError { error ->
            Log.e(TAG, "got checkPeerPushDebit error result $error")
        }

        return response
    }

    private suspend fun getMaxPeerPushDebitAmount(
        currency: String,
        exchangeBaseUrl: String? = null,
        restrictScope: ScopeInfo? = null,
    ): GetMaxPeerPushDebitAmountResponse? {
        var response: GetMaxPeerPushDebitAmountResponse? = null
        api.request("getMaxPeerPushDebitAmount", GetMaxPeerPushDebitAmountResponse.serializer()) {
            exchangeBaseUrl?.let { put("exchangeBaseUrl", it) }
            restrictScope?.let { put("restrictScope", JSONObject(BackendManager.json.encodeToString(it))) }
            put("currency", currency)
        }.onError { error ->
            Log.e(TAG, "got getMaxPeerPushDebitAmount error result $error")
        }.onSuccess {
            response = it
        }

        return response
    }

    fun initiatePeerPushDebit(
        amount: Amount,
        summary: String,
        expirationHours: Long,
        restrictScope: ScopeInfo? = null,
    ) {
        _outgoingPushState.value = OutgoingCreating
        scope.launch(Dispatchers.IO) {
            requestInitiatePeerPushDebit(amount, summary, expirationHours, restrictScope)
                .onSuccess { response ->
                    _outgoingPushState.value = OutgoingResponse(response.transactionId)
                }.onError { error ->
                    Log.e(TAG, "got initiatePeerPushDebit error result $error")
                    _outgoingPushState.value = OutgoingError(error)
                }
        }
    }

    fun resetPushPayment() {
        _outgoingPushState.value = OutgoingIntro
    }

    /**
     * Reiner Netzwerk-Aufruf ohne Seiteneffekt auf _outgoingPushState - von
     * initiatePeerPushDebit (Einzel-Sende-Pfad, ein Aufruf) UND
     * initiatePeerPushDebitGroup (Gruppen-Split-Pfad, N parallele Aufrufe,
     * siehe unten) genutzt, damit die Anfrage-Konstruktion nur an einer
     * Stelle steht.
     */
    private suspend fun requestInitiatePeerPushDebit(
        amount: Amount,
        summary: String,
        expirationHours: Long,
        restrictScope: ScopeInfo?,
    ): WalletResponse<InitiatePeerPushDebitResponse> {
        val expiry = Timestamp.fromMillis(System.currentTimeMillis() + HOURS.toMillis(expirationHours))
        return api.request("initiatePeerPushDebit", InitiatePeerPushDebitResponse.serializer()) {
            restrictScope?.let { put("restrictScope", JSONObject(BackendManager.json.encodeToString(it))) }
            put("amount", amount.toJSONString())
            put("partialContractTerms", JSONObject().apply {
                put("amount", amount.toJSONString())
                put("summary", summary)
                put("purse_expiration", JSONObject(Json.encodeToString(expiry)))
            })
        }
    }

    /**
     * Erzeugt [count] unabhaengige peer-push-debit-Purses parallel (eine pro
     * Gruppen-Empfaenger-Anteil, siehe PROMPT_parallel_group_split.md).
     * Jeder Anteil hat seinen eigenen Retry-Zaehler - ein Fehlschlag bei
     * einem Anteil bricht die anderen nicht ab (Regel 9 dort). Gibt NUR das
     * Ergebnis zurueck, trifft keine Entscheidung ueber Teilerfolg (Regel
     * 11) - das ist Sache des Aufrufers (ComposeSendScreen, Meilenstein 3).
     *
     * Bewusst unabhaengig von _outgoingPushState/OutgoingState: dieser Pfad
     * kann N Purses gleichzeitig in Flug haben, das bestehende Single-Item-
     * State-Modell kann das nicht abbilden (Regel 8 aus PROMPT.md: minimale
     * Eingriffe in bestehende Dateien/Zustaende).
     */
    suspend fun initiatePeerPushDebitGroup(
        count: Int,
        amountPerShare: Amount,
        summary: String,
        expirationHours: Long,
        restrictScope: ScopeInfo? = null,
        maxRetries: Int = 2,
    ): List<ShareResult> {
        require(count > 0) { "count must be positive, was $count" }

        // Guenstiger Vorab-Check des Gesamtbetrags: echten Geldmangel sofort
        // erkennen, bevor ueberhaupt eine Purse erzeugt wird - vermeidet, dass
        // alle N Shares erst ihre Retries verbrauchen, obwohl das Ergebnis von
        // vornherein feststeht (deckt nur "insgesamt zu wenig Guthaben" ab,
        // nicht die transiente Race um einzelne Muenzen - dafuer siehe den
        // Mutex weiter unten).
        val totalAmount = amountPerShare * count
        val feeCheck = checkPeerPushFees(totalAmount, restrictScope = restrictScope)
        if (feeCheck is CheckFeeResult.InsufficientBalance) {
            val error = TalerErrorInfo.makeCustomError(
                "Insufficient balance for group split: need $totalAmount, " +
                    "max effective spend is ${feeCheck.maxAmountEffective}",
            )
            return (0 until count).map { ShareResult.Failure(null, error) }
        }

        // Gemeinsamer Lock nur fuer den Retry-Pfad bei INSUFFICIENT_BALANCE
        // (siehe initiatePeerPushDebitShareWithRetry): verhindert, dass
        // mehrere Shares gleichzeitig auf denselben, noch laufenden Refresh
        // (Melt+Reveal) einer Geschwister-Share warten und sich dabei
        // gegenseitig unnoetige, aussichtslose Retry-Versuche liefern.
        val insufficientBalanceMutex = Mutex()
        return coroutineScope {
            (0 until count).map {
                async(Dispatchers.IO) {
                    initiatePeerPushDebitShareWithRetry(
                        amountPerShare, summary, expirationHours, restrictScope, maxRetries,
                        insufficientBalanceMutex,
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * UI-Einstiegspunkt fuer den Gruppen-Split-Versand (Meilenstein 3,
     * PROMPT_parallel_group_split.md) - Fire-and-forget-Wrapper um
     * [initiatePeerPushDebitGroup], analog zu [initiatePeerPushDebit] fuer
     * den Einzel-Pfad. Eigener [_groupPushState], bewusst getrennt von
     * [_outgoingPushState] (Regel 8 PROMPT.md: der Einzel-Pfad kennt genau
     * EINE Purse gleichzeitig, das Gruppen-Modell N).
     */
    fun initiatePeerPushDebitGroupAsync(
        count: Int,
        amountPerShare: Amount,
        summary: String,
        expirationHours: Long,
        restrictScope: ScopeInfo? = null,
    ) {
        _groupPushState.value = GroupPushState.InProgress(count)
        scope.launch(Dispatchers.IO) {
            val results = initiatePeerPushDebitGroup(count, amountPerShare, summary, expirationHours, restrictScope)
            _groupPushState.value = GroupPushState.Done(results)
        }
    }

    fun resetGroupPushState() {
        _groupPushState.value = GroupPushState.Idle
    }

    private suspend fun initiatePeerPushDebitShareWithRetry(
        amount: Amount,
        summary: String,
        expirationHours: Long,
        restrictScope: ScopeInfo?,
        maxRetries: Int,
        insufficientBalanceMutex: Mutex,
    ): ShareResult {
        var transactionId: String? = null
        var attempt = 0
        while (true) {
            val result: ShareResult = if (transactionId == null) {
                when (val response = requestInitiatePeerPushDebit(amount, summary, expirationHours, restrictScope)) {
                    is WalletResponse.Success -> {
                        val id = response.result.transactionId
                        transactionId = id
                        waitForShareTalerUri(id)
                    }
                    is WalletResponse.Error -> ShareResult.Failure(null, response.error)
                }
            } else {
                // Purse existiert schon (voriger Versuch ist an der
                // Merge-Bestaetigung gescheitert/getimeoutet, nicht an der
                // Purse-Erstellung selbst) - retryTransaction statt einer
                // weiteren, ueberfluessigen Purse (sonst haeufen sich
                // verwaiste Purses bei jedem Retry an).
                when (val retryResponse = api.request<Unit>("retryTransaction") {
                    put("transactionId", transactionId)
                }) {
                    is WalletResponse.Success -> waitForShareTalerUri(transactionId)
                    is WalletResponse.Error -> ShareResult.Failure(transactionId, retryResponse.error)
                }
            }
            if (result is ShareResult.Success) return result
            result as ShareResult.Failure
            transactionId = result.transactionId ?: transactionId
            attempt++
            if (attempt > maxRetries) return ShareResult.Failure(transactionId, result.error)

            // INSUFFICIENT_BALANCE ist bei knappem Muenzpuffer erwartbar-
            // transient: eine Geschwister-Share laesst gerade ihre grosse
            // Muenze per Refresh (Melt+Reveal) beim Exchange aufteilen,
            // danach existieren die fuer diese Share noetigen Muenzen.
            // Sofortiges Retry hat hier praktisch keine Erfolgschance -
            // stattdessen ueber den gemeinsamen Mutex serialisieren (nur
            // eine Share "recovert" gleichzeitig) und mit steigendem
            // Backoff warten, statt alle wartenden Shares parallel gegen
            // den Exchange retryen zu lassen. Andere Fehlercodes (Netzwerk
            // etc.) behalten das bisherige Sofort-Retry-Verhalten.
            if (result.error.code == TalerErrorCode.WALLET_PEER_PUSH_PAYMENT_INSUFFICIENT_BALANCE) {
                insufficientBalanceMutex.withLock {
                    delay(INSUFFICIENT_BALANCE_BACKOFF_MS * attempt)
                }
            }
        }
    }

    /**
     * Aktives Polling statt reinem Warten auf eine Notification (Regel 10 in
     * PROMPT_parallel_group_split.md) - im Live-Debugging vom 2026-09-01
     * beobachtet, dass eine Transaktion mit bereits gesetzter talerUri
     * trotzdem auf einen laengst veralteten, nie automatisch erneut
     * versuchten Merge-Fehler stehen bleiben kann; reines Notification-
     * Warten wuerde diesen Zustand nie bemerken.
     */
    private suspend fun waitForShareTalerUri(
        transactionId: String,
        pollIntervalMs: Long = 2_000L,
        timeoutMs: Long = 600_000L, // 10 Minuten, konsistent mit TALER_URI_TIMEOUT_MS in
                                     // ComposeSendScreen.kt/ComposeRefundScreen.kt (39-47s Exchange-Latenz)
    ): ShareResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (val response = api.request("getTransactionById", Transaction.serializer()) {
                put("transactionId", transactionId)
            }) {
                is WalletResponse.Success -> {
                    val tx = response.result
                    val talerUri = (tx as? TransactionPeerPushDebit)?.talerUri
                    if (talerUri != null) return ShareResult.Success(transactionId, talerUri)
                    // tx.error ist nur der letzte Fehler aus dem juengsten Hintergrundversuch,
                    // kein "endgueltig gescheitert"-Flag - kann transient/ueberholt sein, waehrend
                    // die Transaktion parallel erfolgreich weiterlaeuft (Live-Debugging
                    // 2026-09-01). Massgeblich ist stattdessen tx.txState.major, die verifizierte
                    // Zustandsmaschine von wallet-core (siehe TransactionState.kt und dieselbe
                    // Klassifikation in TalerTransactionStateMapper.statusFromMajorState): nur
                    // Failed/Aborted/Deleted/Expired sind echte Endzustaende, alles andere
                    // (Pending, Finalizing, Suspended*, Aborting, Unknown, None) ist Uebergang -
                    // dort wird weitergepollt statt abzubrechen.
                    if (tx.txState.major in TERMINAL_FAILURE_STATES) {
                        return ShareResult.Failure(
                            transactionId,
                            tx.error ?: TalerErrorInfo.makeCustomError(
                                "Transaction ended in terminal state ${tx.txState.major} (group share)",
                            ),
                        )
                    }
                }
                is WalletResponse.Error -> return ShareResult.Failure(transactionId, response.error)
            }
            delay(pollIntervalMs)
        }
        return ShareResult.Failure(
            transactionId,
            TalerErrorInfo.makeCustomError("Timed out waiting for payment URI (group share)"),
        )
    }

    fun preparePeerPullDebit(
        talerUri: String? = null,
        transactionId: String? = null,
    ) {
        _incomingPullState.value = IncomingChecking
        scope.launch(Dispatchers.IO) {
            api.request("preparePeerPullDebit", PreparePeerPullDebitResponse.serializer()) {
                talerUri?.let { put("talerUri", talerUri) }
                transactionId?.let { put("transactionId", transactionId) }
                this
            }.onSuccess { response ->
                scope.launch(Dispatchers.IO) a@{
                    val exchange = exchangeManager.findExchangeByUrl(response.exchangeBaseUrl)

                    if (exchange == null) {
                        Log.d(TAG, "exchange entry for ${response.exchangeBaseUrl} was not found")
                        _incomingPullState.value = IncomingError(
                            TalerErrorInfo.makeCustomError( // TODO: localize error
                                "No provider with URL ${cleanExchange(response.exchangeBaseUrl)} was found in the wallet",
                            )
                        )
                        return@a
                    }

                    _incomingPullState.value = if (exchange.tosStatus.isAccepted()) {
                        IncomingTerms(
                            amountRaw = response.amountRaw,
                            amountEffective = response.amountEffective,
                            contractTerms = response.contractTerms,
                            id = response.transactionId,
                        )
                    } else {
                        IncomingTosReview(
                            amountRaw = response.amountRaw,
                            amountEffective = response.amountEffective,
                            contractTerms = response.contractTerms,
                            exchangeBaseUrl = response.exchangeBaseUrl,
                            id = response.transactionId,
                        )
                    }
                }
            }.onError { error ->
                Log.e(TAG, "got preparePeerPullDebit error result $error")
                _incomingPullState.value = IncomingError(error)
            }
        }
    }

    fun confirmPeerPullDebit(terms: IncomingTerms) {
        _incomingPullState.value = IncomingAccepting(terms)
        scope.launch(Dispatchers.IO) {
            api.request<Unit>("confirmPeerPullDebit") {
                put("transactionId", terms.id)
            }.onSuccess {
                _incomingPullState.value = IncomingAccepted(terms.id)
            }.onError { error ->
                Log.e(TAG, "got confirmPeerPullDebit error result $error")
                _incomingPullState.value = IncomingError(error)
            }
        }
    }

    @UiThread
    fun refreshPeerPullDebitTos(exchanges: List<ExchangeItem>) = scope.launch {
        _incomingPullState.update { state ->
            var newState = state
            if (state is IncomingTosReview) {
                exchanges.find { it.exchangeBaseUrl == state.exchangeBaseUrl }?.let { exchange ->
                    // only an actual acceptance may lift a review we asked for: a list
                    // fetched before the provider was known still says missing-tos,
                    // which isAccepted() treats as good enough
                    if (exchange.tosStatus == ExchangeTosStatus.Accepted) {
                        newState = IncomingTerms(
                            amountRaw = state.amountRaw,
                            amountEffective = state.amountEffective,
                            contractTerms = state.contractTerms,
                            id = state.id,
                        )
                    }
                } ?: run {
                    Log.d(TAG, "could not refresh ToS status, exchange ${state.exchangeBaseUrl} was not found")
                }
            }
            newState
        }
    }

    /**
     * Loest eine EMPFANGENE pay-push-URI zu ihrer Transaktion auf und gibt
     * deren transactionId zurueck, oder null, wenn die URI keine auffindbare
     * PeerPushCredit-Transaktion dieser Wallet bezeichnet.
     *
     * Lag frueher im Binder-Service, den Signal vor dem Oeffnen des
     * Refund-Screens aufrief; seit Signal nur noch die URI im Deep-Link
     * mitgibt, loest der Screen selbst auf (siehe ComposeRefundScreen).
     * Anders als [preparePeerPushCredit] veraendert diese Funktion keinen
     * geteilten UI-Zustand - sie beantwortet nur eine Frage.
     */
    suspend fun resolveReceivedPeerPushCreditId(talerUri: String): String? {
        if (api.awaitInit() is WalletResponse.Error) return null

        val prepared = api.request("preparePeerPushCredit", PreparePeerPushCreditResponse.serializer()) {
            put("talerUri", talerUri)
        }
        val transactionId = when (prepared) {
            is WalletResponse.Success -> prepared.result.transactionId
            is WalletResponse.Error -> return null
        }

        val response = api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", transactionId)
        }
        return when (response) {
            is WalletResponse.Success -> transactionId.takeIf { response.result is TransactionPeerPushCredit }
            is WalletResponse.Error -> null
        }
    }

    fun preparePeerPushCredit(
        talerUri: String? = null,
        transactionId: String? = null,
    ) {
        _incomingPushState.value = IncomingChecking
        scope.launch(Dispatchers.IO) a@ {
            api.request("preparePeerPushCredit", PreparePeerPushCreditResponse.serializer()) {
                talerUri?.let { put("talerUri", talerUri) }
                transactionId?.let { put("transactionId", transactionId) }
                this
            }.onSuccess { response ->
                scope.launch(Dispatchers.IO) b@ {
                    val exchange = exchangeManager.findExchangeByUrl(response.exchangeBaseUrl)

                    if (exchange == null) {
                        Log.d(TAG, "exchange entry for ${response.exchangeBaseUrl} was not found")
                        _incomingPushState.value = IncomingError(
                            TalerErrorInfo.makeCustomError( // TODO: localize error
                                "No provider with URL ${cleanExchange(response.exchangeBaseUrl)} was found in the wallet",
                            )
                        )
                        return@b
                    }

                    _incomingPushState.value = if (exchange.tosStatus.isAccepted()) {
                        IncomingTerms(
                            amountRaw = response.amountRaw,
                            amountEffective = response.amountEffective,
                            contractTerms = response.contractTerms,
                            id = response.transactionId,
                        )
                    } else {
                        IncomingTosReview(
                            amountRaw = response.amountRaw,
                            amountEffective = response.amountEffective,
                            contractTerms = response.contractTerms,
                            exchangeBaseUrl = response.exchangeBaseUrl,
                            id = response.transactionId,
                        )
                    }
                }
            }.onError { error ->
                Log.e(TAG, "got preparePeerPushCredit error result $error")
                _incomingPushState.value = IncomingError(error)
            }
        }
    }

    fun confirmPeerPushCredit(terms: IncomingTerms) {
        _incomingPushState.value = IncomingAccepting(terms)
        scope.launch(Dispatchers.IO) {
            api.request<Unit>("confirmPeerPushCredit") {
                put("transactionId", terms.id)
            }.onSuccess {
                _incomingPushState.value = IncomingAccepted(terms.id)
            }.onError { error ->
                Log.e(TAG, "got confirmPeerPushCredit error result $error")
                _incomingPushState.value = IncomingError(error)
            }
        }
    }

    @UiThread
    fun refreshPeerPushCreditTos(exchanges: List<ExchangeItem>) = scope.launch {
        _incomingPushState.update { state ->
            var newState = state
            if (state is IncomingTosReview) {
                exchanges.find { it.exchangeBaseUrl == state.exchangeBaseUrl }?.let { exchange ->
                    // only an actual acceptance may lift a review we asked for: a list
                    // fetched before the provider was known still says missing-tos,
                    // which isAccepted() treats as good enough
                    if (exchange.tosStatus == ExchangeTosStatus.Accepted) {
                        newState = IncomingTerms(
                            amountRaw = state.amountRaw,
                            amountEffective = state.amountEffective,
                            contractTerms = state.contractTerms,
                            id = state.id,
                        )
                    }
                } ?: run {
                    Log.d(TAG, "could not refresh ToS status, exchange ${state.exchangeBaseUrl} was not found")
                }
            }
            newState
        }
    }
}
