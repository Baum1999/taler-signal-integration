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

import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import net.taler.merchantpos.R
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.config.ConfigurationReceiver
import net.taler.merchantpos.config.PosConfig
import net.taler.merchantpos.order.RestartState.ENABLED

class OrderManager(private val context: Context) : ConfigurationReceiver {

    companion object {
        val TAG: String = OrderManager::class.java.simpleName
    }

    private lateinit var currency: String
    private var orderCounter: Int = 0
    private val mCurrentOrderId = MutableLiveData<Int>()
    internal val currentOrderId: LiveData<Int> = mCurrentOrderId

    private val productsByCategory = HashMap<Category, ArrayList<ConfigProduct>>()

    private val orders = LinkedHashMap<Int, MutableLiveOrder>()

    private val mProducts = MutableLiveData<List<ConfigProduct>>()
    internal val products: LiveData<List<ConfigProduct>> = mProducts

    private val mCategories = MutableLiveData<List<Category>>()
    internal val categories: LiveData<List<Category>> = mCategories

    override suspend fun onConfigurationReceived(posConfig: PosConfig, currency: String): String? {
        // parse categories
        if (posConfig.categories.isEmpty()) {
            Log.e(TAG, "No valid category found.")
            return context.getString(R.string.config_error_category)
        }
        // pre-select the first category
        posConfig.categories[0].selected = true

        // group products by categories
        productsByCategory.clear()
        val unknownCategory = Category(-1, context.getString(R.string.product_category_uncategorized))
        posConfig.categories.forEach { category ->
            productsByCategory[category] = ArrayList()
        }
        posConfig.products.forEach { product ->
            val productCurrency = product.price.currency
            if (productCurrency != currency) {
                Log.e(TAG, "Product $product has currency $productCurrency, $currency expected")
                return context.getString(
                    R.string.config_error_currency, product.description, productCurrency, currency
                )
            }
            product.categories.forEach { categoryId ->
                val category = posConfig.categories.find { it.id == categoryId } ?: let {
                    Log.e(TAG, "Product $product has unknown category $categoryId")
                    unknownCategory
                }

                productsByCategory.getOrPut(category) { ArrayList() }.add(product)
            }
        }
        this.currency = currency
        mCategories.postValue(posConfig.categories +
                if(productsByCategory.containsKey(unknownCategory)) {
                    listOf(unknownCategory)
                } else {
                    emptyList()
                })
        mProducts.postValue(productsByCategory[posConfig.categories[0]] ?: emptyList())
        orders.clear()
        orderCounter = 0
        orders[0] = MutableLiveOrder(0, currency, productsByCategory)
        mCurrentOrderId.postValue(0)
        return null // success, no error string
    }

    @UiThread
    internal fun getOrder(orderId: Int): LiveOrder {
        return orders[orderId] ?: throw IllegalArgumentException("Order not found: $orderId")
    }

    @UiThread
    internal fun nextOrder() {
        val currentId = currentOrderId.value!!
        var foundCurrentOrder = false
        var nextId: Int? = null
        for (orderId in orders.keys) {
            if (foundCurrentOrder) {
                nextId = orderId
                break
            }
            if (orderId == currentId) foundCurrentOrder = true
        }
        if (nextId == null) {
            nextId = ++orderCounter
            orders[nextId] = MutableLiveOrder(nextId, currency, productsByCategory)
        }
        val currentOrder = order(currentId)
        if (currentOrder.isEmpty()) orders.remove(currentId)
        else currentOrder.lastAddedProduct = null  // not needed anymore and it would get selected
        mCurrentOrderId.value = requireNotNull(nextId)
    }

    @UiThread
    internal fun previousOrder() {
        val currentId = currentOrderId.value!!
        var previousId: Int? = null
        var foundCurrentOrder = false
        for (orderId in orders.keys) {
            if (orderId == currentId) {
                foundCurrentOrder = true
                break
            }
            previousId = orderId
        }
        if (previousId == null || !foundCurrentOrder) {
            throw AssertionError("Could not find previous order for $currentId")
        }
        val currentOrder = order(currentId)
        // remove current order if empty, or lastAddedProduct as it is not needed anymore
        // and would get selected when navigating back instead of last selection
        if (currentOrder.isEmpty()) orders.remove(currentId)
        else currentOrder.lastAddedProduct = null
        mCurrentOrderId.value = requireNotNull(previousId)
    }

    fun hasPreviousOrder(currentOrderId: Int): Boolean {
        return currentOrderId != orders.keys.first()
    }

    fun hasNextOrder(currentOrderId: Int) = order(currentOrderId).restartState.map { state ->
        state == ENABLED || currentOrderId != orders.keys.last()
    }

    internal fun setCurrentCategory(category: Category) {
        val newCategories = categories.value?.apply {
            forEach { if (it.selected) it.selected = false }
            category.selected = true
        }
        mCategories.postValue(newCategories ?: emptyList())
        mProducts.postValue(productsByCategory[category])
    }

    @UiThread
    internal fun addProduct(orderId: Int, product: ConfigProduct) {
        order(orderId).addProduct(product)
    }

    @UiThread
    internal fun onOrderPaid(orderId: Int) {
        if (currentOrderId.value == orderId) {
            if (hasPreviousOrder(orderId)) previousOrder()
            else nextOrder()
        }
        orders.remove(orderId)
    }

    private fun order(orderId: Int): MutableLiveOrder {
        return orders[orderId] ?: throw IllegalStateException()
    }

}
