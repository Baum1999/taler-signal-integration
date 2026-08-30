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
}
