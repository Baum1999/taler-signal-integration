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

    @Test
    fun `stockLimit subtracts sold and lost from total stock`() {
        val product = ConfigProduct(
            description = "finite_quantity",
            price = Amount("KUDOS", 10, 0),
            categories = emptyList(),
            totalStock = 10,
            unitTotalStock = "10",
            totalSold = 0,
            totalLost = 2,
        )

        assertEquals(8, product.stockLimit)
    }

    @Test
    fun `stockLimit subtracts unit sold and lost`() {
        val product = ConfigProduct(
            description = "test",
            price = Amount("KUDOS", 5, 0),
            categories = emptyList(),
            unitTotalStock = "10",
            unitTotalSold = "2",
            unitTotalLost = "3",
        )

        assertEquals(5, product.stockLimit)
    }

    @Test
    fun `stockLimit with no sold or lost returns total stock`() {
        val product = ConfigProduct(
            description = "test",
            price = Amount("KUDOS", 5, 0),
            categories = emptyList(),
            totalStock = 10,
        )

        assertEquals(10, product.stockLimit)
    }

    @Test
    fun `stockLimit returns null for unlimited stock`() {
        val product = ConfigProduct(
            description = "test",
            price = Amount("KUDOS", 5, 0),
            categories = emptyList(),
            totalStock = -1,
        )

        assertNull(product.stockLimit)
    }

    @Test
    fun `stockLimit clamps to zero when sold and lost exceed stock`() {
        val product = ConfigProduct(
            description = "test",
            price = Amount("KUDOS", 5, 0),
            categories = emptyList(),
            totalStock = 5,
            totalSold = 3,
            totalLost = 5,
        )

        assertEquals(0, product.stockLimit)
    }
}
