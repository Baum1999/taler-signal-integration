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

package net.taler.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ContractTermsSpecTest {

    @Test
    fun multiCurrencyChoicesUseTheirOwnCurrencySpec() {
        val eur = CurrencySpecification(
            name = "Euro",
            numFractionalInputDigits = 2,
            numFractionalNormalDigits = 2,
            numFractionalTrailingZeroDigits = 2,
            altUnitNames = mapOf(0 to "€"),
        )
        val usd = CurrencySpecification(
            name = "US Dollar",
            numFractionalInputDigits = 2,
            numFractionalNormalDigits = 2,
            numFractionalTrailingZeroDigits = 2,
            altUnitNames = mapOf(0 to "$"),
        )
        val terms = ContractTerms.V1(
            summary = "Order",
            orderId = "order-1",
            merchantBaseUrl = "https://merchant.example/",
            merchant = Merchant(name = "Merchant"),
            exchanges = listOf(Exchange("https://exchange.example/")),
            products = listOf(
                ContractProduct(
                    description = "EUR product",
                    price = Amount.fromJSONString("EUR:3"),
                ),
                ContractProduct(
                    description = "USD product",
                    price = Amount.fromJSONString("USD:4"),
                ),
            ),
            choices = listOf(
                ContractChoice(
                    amount = Amount.fromJSONString("EUR:10"),
                    inputs = emptyList(),
                    outputs = emptyList(),
                    maxFee = Amount.fromJSONString("EUR:0.1"),
                ),
                ContractChoice(
                    amount = Amount.fromJSONString("USD:20"),
                    inputs = emptyList(),
                    outputs = emptyList(),
                    maxFee = Amount.fromJSONString("USD:0.2"),
                ),
            ),
            tokenFamilies = emptyMap(),
        )

        val specs = mapOf("EUR" to eur, "USD" to usd)
        val enriched = terms.withSpec { specs[it] } as ContractTerms.V1

        assertEquals(eur, enriched.choices[0].amount.spec)
        assertEquals(eur, enriched.choices[0].maxFee.spec)
        assertEquals(usd, enriched.choices[1].amount.spec)
        assertEquals(usd, enriched.choices[1].maxFee.spec)
        assertEquals(eur, enriched.products!![0].price!!.spec)
        assertEquals(usd, enriched.products!![1].price!!.spec)
    }
}
