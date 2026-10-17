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

import net.taler.common.Amount

/**
 * Fuer die Sammelkarte, die EnterLinkTab (ScanQrScreen.kt) zeigt, wenn eine
 * manuell eingefuegte Zwischenablage mehrere Taler-URIs enthaelt (z.B. ein
 * per Signal-Transkript kopierter Gruppen-Split, siehe TalerUriExtractor).
 *
 * WICHTIG (Begruendung siehe Bugreport-Plan/PROMPT_parallel_group_split.md):
 * ein Betrachter, der eine FREMDE Anteils-URI prueft, kann "wurde von jemand
 * ANDEREM angenommen" nicht von "wurde abgebrochen" oder "ist abgelaufen"
 * unterscheiden - preparePeerPushCredit auf einer bereits geclaimten Purse
 * liefert wallet-core-seitig denselben WALLET_PEER_PUSH_CREDIT_PURSE_GONE-
 * Fehler fuer beide Faelle (siehe pay-peer-push-credit.ts,
 * TalerPaymentPreviewer.errorResult). Diese Sammelkarte zeigt deshalb bewusst
 * NICHT "X von N angenommen", sondern "X von N nicht mehr verfuegbar" -
 * derselbe Informationsgehalt, ohne einen Wert vorzutaeuschen, den die Wallet
 * nicht kennen kann. Ein echtes "X von N angenommen" ueber fremde Anteile
 * hinweg ist nur auf dem Absender-Geraet moeglich (OwnUriTracker dort kennt
 * die eigene TransactionPeerPushDebit direkt, siehe TalerPaymentPreviewer).
 */
data class ShareSummary(
    val uri: String,
    /** true = eigene ausgehende Zahlung (OwnUriTracker.isOwn), kein Anteil aus Empfaenger-Sicht. */
    val isOwn: Boolean,
    /** null = kein verwertbares Preview-Ergebnis (Fehler, oder isOwn/kein PAY_PUSH -> nicht abgefragt). */
    val preview: PaymentPreviewResult?,
)

data class MultiUriAggregate(
    val total: Int,
    /** Angenommen von jemand anderem, abgebrochen oder abgelaufen - nicht unterscheidbar. */
    val unavailable: Int,
    /** Eigene(r) Anteil(e), bereits von DIESEM Geraet angenommen (Status ANGENOMMEN). */
    val acceptedByMe: Int,
    /** Noch offen, fuer diesen Betrachter einloesbar. */
    val open: List<ShareSummary>,
    /** Summe ueber offene + eigene angenommene Anteile, nur wenn ALLE Anteile Betraege derselben Waehrung lieferten. */
    val totalAmount: Amount?,
)

/**
 * Fragt fuer jede URI (ausser eigenen ausgehenden) eine Preview ab und
 * aggregiert das Ergebnis. Nur PAY_PUSH wird abgefragt - die anderen
 * URI-Arten (PAY_PULL, PAY, WITHDRAW, REFUND) haben in einem Gruppen-Split
 * keine Bedeutung (siehe PROMPT_parallel_group_split.md: N unabhaengige
 * peer-push-debit-Purses, eine pro Empfaenger-Anteil) und werden daher nicht
 * abgefragt, um keine ungewollten Nebenwirkungen (z.B. ein prepare* fuer
 * einen fremden Withdraw-Vorgang) auszuloesen.
 */
suspend fun buildAggregate(
    uris: List<String>,
    previewer: PaymentPreviewer,
    ownUriChecker: OwnUriChecker,
): MultiUriAggregate {
    val shares = uris.distinct().map { uri ->
        val isOwn = ownUriChecker.isOwn(uri)
        val kind = TalerUriParser.classify(uri)
        val preview = if (!isOwn && kind == TalerUriKind.PAY_PUSH) {
            runCatching { previewer.preview(kind, uri) }.getOrNull()
        } else {
            null
        }
        ShareSummary(uri, isOwn, preview)
    }

    // Eigene ausgehende Anteile (isOwn) zaehlen nicht in der Empfaenger-Sicht
    // mit - das ist der Absender, der seine eigene URI zurueckgespielt bekommen hat.
    val relevant = shares.filterNot { it.isOwn }
    val acceptedByMe = relevant.count { it.preview?.status == TalerOperationStatus.ANGENOMMEN }
    val open = relevant.filter { it.preview?.status == TalerOperationStatus.OFFEN }
    val unavailable = relevant.size - acceptedByMe - open.size

    // Nur offene + von mir bereits angenommene Anteile fliessen in die Summe
    // ein - fuer "nicht mehr verfuegbare" Anteile liefert errorResult() ohnehin
    // keinen Betrag (amount = null), UND selbst wenn er bekannt waere, ist er
    // nicht "meiner": das Geld ging an jemand anderen oder existiert nicht
    // mehr. Ohne diese Einschraenkung wuerde die Summe verschwinden, sobald
    // auch nur EIN Anteil nicht mehr verfuegbar ist - der Normalfall, den
    // diese Karte gerade abbilden soll.
    val amountRelevantShares = open + relevant.filter { it.preview?.status == TalerOperationStatus.ANGENOMMEN }

    return MultiUriAggregate(
        total = relevant.size,
        unavailable = unavailable,
        acceptedByMe = acceptedByMe,
        open = open,
        totalAmount = computeTotalAmount(amountRelevantShares),
    )
}

/**
 * Nur wenn JEDER uebergebene Anteil einen Betrag lieferte UND alle dieselbe
 * Waehrung tragen, sonst null (die Karte zeigt dann keinen Gesamtbetrag statt
 * einer falschen/unvollstaendigen Summe - Regel 4, PROMPT.md: fail-safe statt
 * falscher Zahl).
 */
private fun computeTotalAmount(shares: List<ShareSummary>): Amount? {
    if (shares.isEmpty()) return null
    val amounts = shares.map { share ->
        val preview = share.preview ?: return null
        val amountStr = preview.amount ?: return null
        val currency = preview.currency ?: return null
        runCatching { Amount.fromString(currency, amountStr) }.getOrNull() ?: return null
    }
    val currency = amounts.first().currency
    if (amounts.any { it.currency != currency }) return null
    return amounts.reduce { a, b -> a + b }
}
