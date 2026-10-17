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

import kotlinx.coroutines.runBlocking
import net.taler.common.Amount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reines Test-Double, kein wallet-core/Robolectric noetig. */
private class FakePreviewer(private val results: Map<String, PaymentPreviewResult>) : PaymentPreviewer {
    override suspend fun preview(kind: TalerUriKind, uri: String): PaymentPreviewResult =
        results[uri] ?: error("no fake result for $uri")
}

/** Reines Test-Double, kein SharedPreferences/Android-Kontext noetig. */
private class FakeOwnUriChecker(private val ownUris: Set<String> = emptySet()) : OwnUriChecker {
    override fun isOwn(uri: String): Boolean = uri in ownUris
}

private fun previewResult(
    status: TalerOperationStatus,
    amount: String? = null,
    currency: String? = null,
): PaymentPreviewResult = PaymentPreviewResult(
    uriKind = TalerUriKind.PAY_PUSH,
    status = status,
    amount = amount,
    currency = currency,
    exchangeBaseUrl = "exchange.example",
    summary = null,
    expirationTimestamp = null,
    isOwnPayment = false,
)

class MultiUriSummaryTest {

    @Test
    fun threeSharesTwoUnavailableOneOpen() = runBlocking {
        val uris = listOf(
            "taler://pay-push/exchange.example/AAA",
            "taler://pay-push/exchange.example/BBB",
            "taler://pay-push/exchange.example/CCC",
        )
        val previewer = FakePreviewer(
            mapOf(
                uris[0] to previewResult(TalerOperationStatus.UNBEKANNT_OFFLINE),
                uris[1] to previewResult(TalerOperationStatus.UNGUELTIG),
                uris[2] to previewResult(TalerOperationStatus.OFFEN, "5", "KUDOS"),
            )
        )
        val aggregate = buildAggregate(uris, previewer, FakeOwnUriChecker())

        assertEquals(3, aggregate.total)
        assertEquals(2, aggregate.unavailable)
        assertEquals(0, aggregate.acceptedByMe)
        assertEquals(1, aggregate.open.size)
        assertEquals(uris[2], aggregate.open.single().uri)
    }

    @Test
    fun ownAcceptedShareCountsAsAcceptedByMe() = runBlocking {
        val uri = "taler://pay-push/exchange.example/AAA"
        val previewer = FakePreviewer(
            mapOf(uri to previewResult(TalerOperationStatus.ANGENOMMEN, "5", "KUDOS"))
        )
        val aggregate = buildAggregate(listOf(uri), previewer, FakeOwnUriChecker())

        assertEquals(1, aggregate.acceptedByMe)
        assertEquals(0, aggregate.unavailable)
        assertTrue(aggregate.open.isEmpty())
    }

    @Test
    fun ownOutgoingUriExcludedFromAggregate() = runBlocking {
        val ownUri = "taler://pay-push/exchange.example/OWN"
        val otherUri = "taler://pay-push/exchange.example/OTHER"
        val previewer = FakePreviewer(
            mapOf(otherUri to previewResult(TalerOperationStatus.OFFEN, "5", "KUDOS"))
        )
        val aggregate = buildAggregate(
            listOf(ownUri, otherUri),
            previewer,
            FakeOwnUriChecker(setOf(ownUri)),
        )

        // ownUri fliesst nicht in total/unavailable/open ein - der Absender
        // bekommt seine eigene URI zurueckgespielt (z.B. Gruppen-Split-Ersteller,
        // der die Nachricht an sich selbst weiterleitet/kopiert).
        assertEquals(1, aggregate.total)
        assertEquals(1, aggregate.open.size)
        assertEquals(otherUri, aggregate.open.single().uri)
    }

    @Test
    fun totalAmountOnlyWhenAllSharesReportSameCurrency() = runBlocking {
        val uris = listOf(
            "taler://pay-push/exchange.example/AAA",
            "taler://pay-push/exchange.example/BBB",
            "taler://pay-push/exchange.example/CCC",
        )
        val previewer = FakePreviewer(
            mapOf(
                uris[0] to previewResult(TalerOperationStatus.OFFEN, "5", "KUDOS"),
                uris[1] to previewResult(TalerOperationStatus.ANGENOMMEN, "3", "KUDOS"),
                uris[2] to previewResult(TalerOperationStatus.OFFEN, "2", "KUDOS"),
            )
        )
        val aggregate = buildAggregate(uris, previewer, FakeOwnUriChecker())

        assertEquals(Amount.fromString("KUDOS", "10"), aggregate.totalAmount)
    }

    @Test
    fun totalAmountSurvivesAnUnavailableShareWithNoKnownAmount() = runBlocking {
        // Regressionstest: eine "nicht mehr verfuegbare" URI liefert per
        // errorResult() KEINEN Betrag (amount = null) - die Gesamtsumme darf
        // deshalb NICHT ueber alle relevanten Anteile berechnet werden,
        // sondern nur ueber offene + von mir angenommene, sonst wuerde die
        // Summe verschwinden, sobald auch nur ein Anteil unavailable ist -
        // der Normalfall, den diese Karte gerade abbilden soll.
        val uris = listOf(
            "taler://pay-push/exchange.example/AAA",
            "taler://pay-push/exchange.example/BBB",
        )
        val previewer = FakePreviewer(
            mapOf(
                uris[0] to previewResult(TalerOperationStatus.UNBEKANNT_OFFLINE),
                uris[1] to previewResult(TalerOperationStatus.OFFEN, "5", "KUDOS"),
            )
        )
        val aggregate = buildAggregate(uris, previewer, FakeOwnUriChecker())

        assertEquals(Amount.fromString("KUDOS", "5"), aggregate.totalAmount)
    }

    @Test
    fun totalAmountNullOnCurrencyMismatch() = runBlocking {
        val uris = listOf(
            "taler://pay-push/exchange.example/AAA",
            "taler://pay-push/exchange.example/BBB",
        )
        val previewer = FakePreviewer(
            mapOf(
                uris[0] to previewResult(TalerOperationStatus.OFFEN, "5", "KUDOS"),
                uris[1] to previewResult(TalerOperationStatus.OFFEN, "5", "EUR"),
            )
        )
        val aggregate = buildAggregate(uris, previewer, FakeOwnUriChecker())

        assertNull(aggregate.totalAmount)
    }

    @Test
    fun emptyInputYieldsZeroedAggregate() = runBlocking {
        val aggregate = buildAggregate(emptyList(), FakePreviewer(emptyMap()), FakeOwnUriChecker())

        assertEquals(0, aggregate.total)
        assertEquals(0, aggregate.unavailable)
        assertEquals(0, aggregate.acceptedByMe)
        assertTrue(aggregate.open.isEmpty())
        assertNull(aggregate.totalAmount)
    }
}
