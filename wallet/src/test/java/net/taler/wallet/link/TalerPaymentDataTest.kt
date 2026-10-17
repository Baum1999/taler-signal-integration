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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TalerPaymentDataTest {

    @Test
    fun `single payment json carries only the uri`() {
        val json = Json.encodeToString(TalerPaymentData(uri = listOf("taler://pay-push/e/A")))
        assertEquals("""{"uri":["taler://pay-push/e/A"]}""", json)
    }

    @Test
    fun `split json carries split fields but no legacyText`() {
        val json = Json.encodeToString(
            TalerPaymentData(includeSelf = true, totalAmount = "30", uri = listOf("taler://pay-push/e/A", "taler://pay-push/e/B"))
        )
        assertEquals("""{"includeSelf":true,"totalAmount":"30","uri":["taler://pay-push/e/A","taler://pay-push/e/B"]}""", json)
        assertFalse(json.contains("legacyText"))
    }
}
