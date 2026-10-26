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

package net.taler.wallet.deposit

import kotlinx.serialization.json.Json
import net.taler.common.Amount
import org.junit.Assert.assertEquals
import org.junit.Test

class DepositManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun maxDepositAmountUsesAvailableMaximum() {
        val response = json.decodeFromString(
            GetMaxDepositAmountResponse.serializer(),
            """
                {
                  "material": {
                    "instructedAmount": "EUR:5",
                    "effectiveAmount": "EUR:5",
                    "rawAmount": "EUR:4.90",
                    "fees": {
                      "coin": "EUR:0",
                      "wire": "EUR:0.10",
                      "refresh": "EUR:0"
                    }
                  },
                  "available": {
                    "instructedAmount": "EUR:10",
                    "effectiveAmount": "EUR:10",
                    "rawAmount": "EUR:9.80",
                    "fees": {
                      "coin": "EUR:0",
                      "wire": "EUR:0.20",
                      "refresh": "EUR:0"
                    }
                  },
                  "exchangeDiagnostics": {}
                }
            """.trimIndent(),
        )

        assertEquals(Amount.fromJSONString("EUR:10"), response.effectiveAmount)
        assertEquals(Amount.fromJSONString("EUR:9.80"), response.rawAmount)
        assertEquals(Amount.fromJSONString("EUR:10"), response.available.instructedAmount)
    }
}
