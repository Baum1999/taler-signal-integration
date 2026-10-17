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

class TalerUriParserTest {

    @Test
    fun classifiesAllFiveUriKinds() {
        assertEquals(TalerUriKind.PAY_PUSH, TalerUriParser.classify("taler://pay-push/exchange.example/ABC"))
        assertEquals(TalerUriKind.PAY_PULL, TalerUriParser.classify("taler://pay-pull/exchange.example/ABC"))
        assertEquals(TalerUriKind.PAY, TalerUriParser.classify("taler://pay/merchant.example/ABC"))
        assertEquals(TalerUriKind.WITHDRAW, TalerUriParser.classify("taler://withdraw/exchange.example/ABC"))
        assertEquals(TalerUriKind.REFUND, TalerUriParser.classify("taler://refund/merchant.example/ABC"))
    }

    @Test
    fun classifiesPayTemplateAsPay() {
        assertEquals(TalerUriKind.PAY, TalerUriParser.classify("taler://pay-template/merchant.example/ABC"))
    }

    @Test
    fun classifiesWithdrawExchangeAsWithdraw() {
        assertEquals(TalerUriKind.WITHDRAW, TalerUriParser.classify("taler://withdraw-exchange/exchange.example/ABC"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(TalerUriKind.PAY_PUSH, TalerUriParser.classify("TALER://PAY-PUSH/exchange.example/ABC"))
        assertEquals(TalerUriKind.PAY_PUSH, TalerUriParser.classify("Taler://Pay-Push/exchange.example/ABC"))
    }

    @Test
    fun recognizesExtTalerScheme() {
        assertEquals(TalerUriKind.PAY_PUSH, TalerUriParser.classify("ext+taler://pay-push/exchange.example/ABC"))
    }

    @Test
    fun recognizesTalerHttpScheme() {
        assertEquals(TalerUriKind.PAY, TalerUriParser.classify("taler+http://pay/merchant.example/ABC"))
    }

    @Test
    fun rejectsPaytoScheme() {
        // payto:// wird von Signals TalerUriDetector als Kandidat erkannt (breiterer
        // Regex), aber TalerUriParser.classify() - Talers eigene Klassifizierung -
        // kennt es nicht als Aktions-Schema. Siehe docs/API.md.
        assertNull(TalerUriParser.classify("payto://iban/DE1234567890/?message=x"))
    }

    @Test
    fun rejectsGarbageInput() {
        assertNull(TalerUriParser.classify("not a uri at all"))
        assertNull(TalerUriParser.classify("https://example.com/pay-push/x"))
        assertNull(TalerUriParser.classify("taler://unknown-action/x"))
        assertNull(TalerUriParser.classify("taler://"))
    }

    @Test
    fun classifiesDd103PayPush() {
        assertEquals(
            TalerUriKind.PAY_PUSH,
            TalerUriParser.classify("https://wallet.taler.net/link#pay-push/exchange.example/ABC")
        )
    }

    @Test
    fun classifiesDd103PayPull() {
        assertEquals(
            TalerUriKind.PAY_PULL,
            TalerUriParser.classify("https://wallet.taler.net/link#pay-pull/exchange.example/ABC")
        )
    }

    @Test
    fun rejectsHttpsLinkOnWrongDomain() {
        // Sicherheitsgrenze: nur Domains aus TalerLinkHosts werden als
        // Taler-Link akzeptiert, siehe
        // docs/superpowers/specs/2026-10-16-taler-dd103-links-design.md.
        assertNull(TalerUriParser.classify("https://evil.example/link#pay-push/exchange.example/ABC"))
    }

    @Test
    fun rejectsEmptyOrForeignFragment() {
        assertNull(TalerUriParser.classify("https://wallet.taler.net/link#"))
        assertNull(TalerUriParser.classify("https://wallet.taler.net/link#https://evil.example"))
        assertNull(TalerUriParser.classify("https://wallet.taler.net/link#javascript:alert(1)"))
    }

    @Test
    fun rejectsLegacyQueryFormat() {
        assertNull(TalerUriParser.classify("https://wallet.taler.net/r?u=taler://pay-push/exchange.example/ABC"))
    }

    @Test
    fun rejectsEmptyString() {
        assertNull(TalerUriParser.classify(""))
    }

    @Test
    fun trimsWhitespaceBeforeClassifying() {
        assertEquals(TalerUriKind.PAY_PUSH, TalerUriParser.classify("  taler://pay-push/exchange.example/ABC  "))
    }
}
