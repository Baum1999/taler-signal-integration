/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
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

package net.taler.merchantpos.order

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.taler.common.Amount
import net.taler.merchantlib.MerchantConfig
import net.taler.merchantpos.R
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.config.PosConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper.shadowMainLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Config(sdk = [28]) // API 29 needs at least Java 9
@RunWith(AndroidJUnit4::class)
class OrderManagerTest {

    private val app: Application = getApplicationContext()
    private val orderManager = OrderManager(app)
    private val posConfig = PosConfig(
        merchantConfig = MerchantConfig(
            baseUrl = "http://example.org",
            apiKey = "sandbox"
        ),
        categories = listOf(
            Category(1, "one"),
            Category(2, "two")
        ),
        products = listOf(
            ConfigProduct(
                description = "foo",
                price = Amount("KUDOS", 1, 0),
                categories = listOf(1)
            ),
            ConfigProduct(
                description = "bar",
                price = Amount("KUDOS", 1, 50000),
                categories = listOf(2)
            )
        )
    )

    @Test
    fun `config test missing categories`() = runBlocking {
        val config = posConfig.copy(categories = emptyList())
        val result = orderManager.onConfigurationReceived(config, "KUDOS", null)
        assertEquals(app.getString(R.string.config_error_category), result)
    }

    @Test
    fun `currency mismatch product is accepted but unavailable`() = runBlocking {
        val products = listOf(posConfig.products[0].copy(price = Amount("WRONGCUR", 1, 0)))
        val config = posConfig.copy(products = products)
        val result = orderManager.onConfigurationReceived(config, "KUDOS", null)
        shadowMainLooper().idle()

        assertNull(result)
        val product = orderManager.products.awaitValue().single()
        assertEquals("WRONGCUR", product.price.currency)
        assertTrue(product.currencyMismatch)
        assertFalse(product.availableToSell)
    }

//    TODO: re-enable test based on orderManager.categories contents!
//      challenge: LiveData is not easily observable in unit tests
//    @Test
//    fun `config test unknown category ID`() = runBlocking {
//        val products = listOf(posConfig.products[0].copy(categories = listOf(42)))
//        val config = posConfig.copy(products = products)
//        val result = orderManager.onConfigurationReceived(config, "KUDOS", null)
//        val expectedStr = app.getString(
//            R.string.config_error_product_category_id, "foo", 42
//        )
//        assertEquals(expectedStr, result)
//    }

    @Test
    fun `config test valid config gets accepted`() = runBlocking {
        val result = orderManager.onConfigurationReceived(posConfig, "KUDOS", null)
        assertNull(result)
    }

    @Test
    fun `all objects is selected by default and shown first`() = runBlocking {
        orderManager.onConfigurationReceived(posConfig, "KUDOS", null)
        shadowMainLooper().idle()

        val categories = orderManager.categories.awaitValue()
        val products = orderManager.products.awaitValue()

        assertNotNull(categories)
        assertNotNull(products)
        assertEquals(app.getString(R.string.product_category_all_objects), categories[0].name)
        assertTrue(categories[0].selected)
        assertFalse(categories[1].selected)
        assertEquals(posConfig.products, products)
    }

    @Test
    fun `uncategorized is appended at the end when needed`() = runBlocking {
        val uncategorizedProduct = ConfigProduct(
            description = "baz",
            price = Amount("KUDOS", 2, 0),
            categories = emptyList()
        )
        val config = posConfig.copy(products = posConfig.products + uncategorizedProduct)

        orderManager.onConfigurationReceived(config, "KUDOS", null)
        shadowMainLooper().idle()

        val categories = orderManager.categories.awaitValue()
        val uncategorized = categories.last()

        assertEquals(app.getString(R.string.product_category_uncategorized), uncategorized.name)
        orderManager.setCurrentCategory(uncategorized)
        shadowMainLooper().idle()
        assertEquals(listOf(uncategorizedProduct), orderManager.products.awaitValue())
    }

    @Test
    fun `legacy default category is hidden and mapped to uncategorized`() = runBlocking {
        val defaultCategory = Category(3, "Default")
        val defaultProduct = ConfigProduct(
            description = "legacy",
            price = Amount("KUDOS", 3, 0),
            categories = listOf(3)
        )
        val config = posConfig.copy(
            categories = posConfig.categories + defaultCategory,
            products = posConfig.products + defaultProduct
        )

        orderManager.onConfigurationReceived(config, "KUDOS", null)
        shadowMainLooper().idle()

        val categories = orderManager.categories.awaitValue()
        assertFalse(categories.any { it.name == "Default" })
        assertEquals(app.getString(R.string.product_category_uncategorized), categories.last().name)

        orderManager.setCurrentCategory(categories.last())
        shadowMainLooper().idle()
        assertEquals(listOf(defaultProduct), orderManager.products.awaitValue())
    }

}

private fun <T> LiveData<T>.awaitValue(timeout: Long = 2, unit: TimeUnit = TimeUnit.SECONDS): T {
    val latch = CountDownLatch(1)
    var result: T? = null
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            result = value
            latch.countDown()
            removeObserver(this)
        }
    }
    observeForever(observer)
    if (this.value != null) {
        result = this.value
        removeObserver(observer)
    } else if (!latch.await(timeout, unit)) {
        removeObserver(observer)
        throw AssertionError("LiveData value was never set.")
    }
    return requireNotNull(result)
}
