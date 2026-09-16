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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TalerUriExtractorTest {

    @Test
    fun extractsPlainUriUnchanged() {
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("taler://pay/merchant.example/ABC"),
        )
    }

    @Test
    fun trimsWhitespaceAroundPlainUri() {
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("  taler://pay/merchant.example/ABC  \n"),
        )
    }

    @Test
    fun extractsUriPrecededByFreeText() {
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("Zahlung: taler://pay/merchant.example/ABC"),
        )
    }

    @Test
    fun extractsPaytoUriPrecededByFreeText() {
        assertEquals(
            "payto://iban/DE1234567890",
            TalerUriExtractor.extract("Bitte hier ueberweisen: payto://iban/DE1234567890"),
        )
    }

    @Test
    fun extractsUriFromJsonObject() {
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("""{"uri": "taler://pay/merchant.example/ABC"}"""),
        )
    }

    @Test
    fun extractsUriFromJsonRegardlessOfKeyName() {
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("""{"talerPaymentLink": "taler://pay/merchant.example/ABC"}"""),
        )
    }

    @Test
    fun extractsUriFromNestedJson() {
        assertEquals(
            "taler://withdraw/exchange.example/ABC",
            TalerUriExtractor.extract(
                """{"data": {"link": "taler://withdraw/exchange.example/ABC"}}""",
            ),
        )
    }

    @Test
    fun extractsUriFromJsonArray() {
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("""["taler://pay/merchant.example/ABC"]"""),
        )
    }

    @Test
    fun fallsBackToRegexWhenJsonIsMalformed() {
        // Kein gueltiges JSON (unquoted key), aber die URI steckt trotzdem drin.
        assertEquals(
            "taler://pay/merchant.example/ABC",
            TalerUriExtractor.extract("{uri: taler://pay/merchant.example/ABC}"),
        )
    }

    @Test
    fun returnsNullForJsonWithoutUri() {
        assertNull(TalerUriExtractor.extract("""{"amount": "KUDOS:5", "note": "hi"}"""))
    }

    @Test
    fun returnsNullForGarbageText() {
        assertNull(TalerUriExtractor.extract("not a uri at all"))
    }

    @Test
    fun returnsNullForEmptyString() {
        assertNull(TalerUriExtractor.extract(""))
        assertNull(TalerUriExtractor.extract("   "))
    }

    // --- extractAll(): Regressionstest fuer den Transkript-Kopieren-Bug ---
    // (Root Cause: startsWithSupportedScheme() im alten extract() behandelte
    // "beginnt mit dem Schema" als "ist komplett eine URI" - der Signal-Fork
    // verschickt aber "taler://...\n\n<Begleittext>", siehe TalerReturnActivity.kt.)

    @Test
    fun extractsUriFollowedByFreeText() {
        val body = "taler://pay-push/exchange.demo.taler.net/ABC" +
            "\n\nThis is a GNU Taler link. Update Signal or copy the URI below " +
            "into the GNU Taler app to make the payment."
        assertEquals(
            listOf("taler://pay-push/exchange.demo.taler.net/ABC"),
            TalerUriExtractor.extractAll(body),
        )
        assertEquals(
            "taler://pay-push/exchange.demo.taler.net/ABC",
            TalerUriExtractor.extract(body),
        )
    }

    @Test
    fun extractAllFindsEveryUriInGroupSplitTranscript() {
        val body = "taler://pay-push/exchange.example/AAA" +
            "\n\ntaler://pay-push/exchange.example/BBB" +
            "\n\nThis is a GNU Taler link. Update Signal or copy the URI below " +
            "into the GNU Taler app to make the payment."
        assertEquals(
            listOf(
                "taler://pay-push/exchange.example/AAA",
                "taler://pay-push/exchange.example/BBB",
            ),
            TalerUriExtractor.extractAll(body),
        )
    }

    @Test
    fun extractAllDeduplicates() {
        val body = "taler://pay-push/exchange.example/AAA taler://pay-push/exchange.example/AAA"
        assertEquals(
            listOf("taler://pay-push/exchange.example/AAA"),
            TalerUriExtractor.extractAll(body),
        )
    }

    @Test
    fun singleUriContainingCommaIsNotTruncated() {
        // Schuetzt den Zweck des whitespace-freien Sonderfalls in extractAll:
        // eine allein stehende URI darf Zeichen enthalten, die
        // EMBEDDED_URI_REGEX sonst als Begleitzeichen ausschliesst (,;)]}"').
        assertEquals(
            listOf("payto://iban/DE1234567890?message=a,b"),
            TalerUriExtractor.extractAll("payto://iban/DE1234567890?message=a,b"),
        )
    }

    @Test
    fun extractAllFindsUrisInJsonArray() {
        assertEquals(
            listOf(
                "taler://pay/merchant.example/ABC",
                "taler://pay/merchant.example/DEF",
            ),
            TalerUriExtractor.extractAll(
                """{"uri": ["taler://pay/merchant.example/ABC", "taler://pay/merchant.example/DEF"]}""",
            ),
        )
    }

    @Test
    fun extractAllReturnsEmptyForGarbage() {
        assertEquals(emptyList<String>(), TalerUriExtractor.extractAll("not a uri at all"))
        assertEquals(emptyList<String>(), TalerUriExtractor.extractAll(""))
    }
}
