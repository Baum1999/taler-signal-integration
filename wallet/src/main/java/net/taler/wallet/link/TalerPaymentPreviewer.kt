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

import java.util.Locale
import net.taler.common.Timestamp
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletResponse
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
 * Schmale Schnittstelle, extrahiert damit [buildAggregate]
 * (MultiUriSummary.kt) ohne wallet-core/Android-Kontext mit einem
 * Test-Double testbar ist (siehe MultiUriSummaryTest.kt) - [TalerPaymentPreviewer]
 * ist die einzige echte Implementierung.
 */
interface PaymentPreviewer {
    suspend fun preview(kind: TalerUriKind, uri: String): PaymentPreviewResult
}

/**
 * Ausgelagert aus [TalerLinkService] (gleiches Prinzip wie
 * [TalerUriParser]/[TalerTransactionStateMapper]: testbar ohne
 * wallet-core/Service-Kontext, hier zusaetzlich wiederverwendbar von
 * Aufrufern INNERHALB desselben Prozesses, die nicht ueber den AIDL-Binder
 * gehen wollen - z.B. die Sammelkarte fuer mehrere manuell eingefuegte URIs
 * in EnterLinkTab/MultiUriSummary.kt, die denselben Preview-Aufruf fuer
 * mehrere URIs hintereinander braucht, ohne fuer jede einen eigenen
 * previewForUri-Binder-Rundlauf zu machen (beide laufen ohnehin im selben
 * Prozess).
 *
 * Reine Verschiebung aus TalerLinkService, kein Verhaltensunterschied fuer
 * den bestehenden AIDL-Pfad (previewForUri/statusForUri delegieren jetzt an
 * eine Instanz dieser Klasse statt an private Methoden im Binder-Objekt).
 */
class TalerPaymentPreviewer(
    private val api: WalletBackendApi,
    private val ownUriTracker: OwnUriTracker,
) : PaymentPreviewer {

    /**
     * Fuehrt den zu [kind] passenden wallet-core-Aufruf aus und bildet das
     * Ergebnis auf [PaymentPreviewResult] ab.
     *
     * Nur PAY_PUSH und PAY_PULL sind vollstaendig an wallet-core angebunden
     * (deckt den DoD-Ablauf ab: pay-push senden, empfangen, annehmen). PAY
     * braucht zusaetzlich einen zweiten Aufruf (getChoicesForPayment) fuer
     * Betrag/Zweck, WITHDRAW/REFUND haben andere Response-Formen - beides
     * bewusst nicht geraten, sondern als offener Punkt zurueckgestellt (siehe
     * Statusbericht/docs/API.md).
     */
    override suspend fun preview(kind: TalerUriKind, uri: String): PaymentPreviewResult {
        // Fix "Fehler-Screen beim Start ueber Signal" (siehe WalletBackendApi.
        // awaitInit): der Aufrufer (z.B. TalerLinkService) kann der Erste
        // sein, der wallet-core in diesem Prozess startet, und preview()
        // kann unmittelbar danach eintreffen, bevor "init" durch ist. Ohne
        // dieses Warten geht der erste prepare*-Aufruf unten an ein noch
        // nicht initialisiertes wallet-core.
        val init = api.awaitInit()
        if (init is WalletResponse.Error) return errorResult(kind, init.error)
        return when (kind) {
            TalerUriKind.PAY_PUSH -> previewPeerPushCredit(uri)
            TalerUriKind.PAY_PULL -> previewPeerPullDebit(uri)
            TalerUriKind.PAY -> previewPay(uri)
            TalerUriKind.WITHDRAW -> previewWithdraw(uri)
            TalerUriKind.REFUND -> previewRefund(uri)
        }
    }

    /**
     * preparePeerPushCredit liefert selbst schon exchangeBaseUrl, aber kein
     * Ablaufdatum und keinen belastbaren Status (die Transaktion existiert
     * danach nur im Dialog-Zustand). Deshalb der Umweg ueber
     * getTransactionById(transactionId) - dort steht in [TransactionPeerPushCredit]
     * alles inklusive echtem txState. Gleiches Muster wie previewPeerPullDebit
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
        ownUriTracker.transactionIdFor(uri)?.let { ownTransactionId ->
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
     * Bug 4 Fix: anders als preparePeerPushCredit ist preparePeerPullDebit nicht
     * als idempotent nach einer bereits abgeschlossenen Zahlung verifiziert -
     * ein erneuter Aufruf auf eine schon bezahlte pay-pull-URI (z.B. beim
     * naechsten Routine-Poll, nachdem der Nutzer ausserhalb von Signal direkt
     * in Taler bezahlt hat) kann eine neue, fehlgeschlagene Transaktion
     * erzeugen statt die bestehende zurueckzugeben. Deshalb: ist fuer diese
     * URI bereits eine transactionId bekannt (PeerPullDebitCache, siehe dort),
     * wird preparePeerPullDebit fuer sie nicht mehr aufgerufen - der aktuelle
     * Zustand kommt dann nur noch aus getTransactionById.
     *
     * Fix (analog zu previewPeerPushCredit oben, jetzt real ausgeloest seit
     * Signal auch pay-pull-URIs ueber prepareSend/direction=PAY_PULL selbst
     * erzeugt und deren Klassifizierung sofort per previewForUri abfragt,
     * siehe TalerReturnActivity.sendComposedPayment): fuer eine URI, die diese
     * Wallet-Instanz selbst per initiatePeerPullCredit erzeugt hat, darf NIE
     * preparePeerPullDebit aufgerufen werden - das praepariert eine NEUE
     * Zahlung GEGEN diese Purse (also "ich bezahle meine eigene Rechnung"),
     * nicht die eigene ausgehende Anfrage. ownUriTracker kennt die eigene
     * transactionId bereits (ComposeSendScreen.kt trackt sie im selben Moment
     * wie beim Push-Pfad), also zuerst dort nachsehen, bevor ueberhaupt ein
     * Cache-Miss zu preparePeerPullDebit fuehren kann.
     */
    private suspend fun previewPeerPullDebit(uri: String): PaymentPreviewResult {
        ownUriTracker.transactionIdFor(uri)?.let { ownTransactionId ->
            return detailsFor(TalerUriKind.PAY_PULL, ownTransactionId)
        }

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

    /**
     * amountRaw statt amountEffective fuer [PaymentPreviewResult.amount]: der
     * Betrag, den die Gegenseite tatsaechlich in der Purse/URI sieht (dieselbe
     * Groesse, die Talers eigene Transaction*Composable.kt ueberall als
     * "transaction_order_total" primaer anzeigt). amountEffective ist die um
     * Gebuehren verschobene Belastung/Gutschrift DIESER Wallet-Instanz -
     * dadurch wich die Gruppen-Split-Summe (aufaddierte Gebuehren ueber alle
     * Anteile) und der einzelne Anteilsbetrag (Karte zeigte 0,37, URI/
     * Empfaenger 0,36) vom tatsaechlichen Zahlungsbetrag ab.
     */
    private suspend fun detailsFor(kind: TalerUriKind, transactionId: String): PaymentPreviewResult {
        val response = api.request("getTransactionById", Transaction.serializer()) {
            put("transactionId", transactionId)
        }
        return when (response) {
            is WalletResponse.Success -> when (val tx = response.result) {
                is TransactionPeerPushCredit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountRaw.amountStr,
                    currency = tx.amountRaw.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = false, // PeerPushCredit = incoming (we received money from someone else)
                )
                is TransactionPeerPushDebit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountRaw.amountStr,
                    currency = tx.amountRaw.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = true, // PeerPushDebit = outgoing (we sent money to someone else)
                )
                is TransactionPeerPullDebit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountRaw.amountStr,
                    currency = tx.amountRaw.currency,
                    exchangeBaseUrl = tx.exchangeBaseUrl,
                    summary = tx.info.summary,
                    expirationTimestamp = expirationMillisOrNull(tx.info.expiration),
                    isOwnPayment = true, // PeerPullDebit = outgoing (we paid someone's invoice)
                )
                is TransactionPeerPullCredit -> PaymentPreviewResult(
                    uriKind = kind,
                    status = TalerTransactionStateMapper.statusFromMajorState(tx.txState.major),
                    amount = tx.amountRaw.amountStr,
                    currency = tx.amountRaw.currency,
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
     * UNBEKANNT_OFFLINE behandelt, statt weitere Fehlercodes zu erraten - das
     * gilt insbesondere fuer WALLET_PEER_PUSH_CREDIT_PURSE_GONE (Purse bereits
     * von jemand anderem geclaimt ODER Absender hat abgebrochen -
     * ununterscheidbar, siehe MultiUriSummary.kt-Dokumentation).
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
                    amount = tx.amountRaw.amountStr,
                    currency = tx.amountRaw.currency,
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
                    amount = tx.amountRaw.amountStr,
                    currency = tx.amountRaw.currency,
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
}
