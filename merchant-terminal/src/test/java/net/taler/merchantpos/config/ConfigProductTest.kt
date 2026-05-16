package net.taler.merchantpos.config

import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigProductTest {

    @Test
    fun `display description is hidden when same as product name`() {
        val product = ConfigProduct(
            productName = "Coffee",
            description = "Coffee",
            price = Amount("KUDOS", 2, 0),
            categories = listOf(1)
        )

        assertEquals("Coffee", product.displayName)
        assertNull(product.displayDescription)
    }

    @Test
    fun `display price formats amount`() {
        val product = ConfigProduct(
            description = "Coffee",
            price = Amount("KUDOS", 2, 50000000),
            categories = listOf(1)
        )

        assertEquals("2.50 KUDOS", product.displayPrice)
    }

    @Test
    fun `display price uses currency spec symbol`() {
        val product = ConfigProduct(
            description = "Coffee",
            price = Amount("CHF", 2, 50000000).withSpec(
                CurrencySpecification(
                    name = "Swiss Francs",
                    numFractionalInputDigits = 2,
                    numFractionalNormalDigits = 2,
                    numFractionalTrailingZeroDigits = 2,
                    altUnitNames = mapOf(0 to "Fr."),
                )
            ),
            categories = listOf(1)
        )

        assertEquals("Fr.2.50", product.displayPrice)
    }
}
