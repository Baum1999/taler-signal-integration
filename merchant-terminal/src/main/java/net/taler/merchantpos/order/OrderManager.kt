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
import net.taler.common.CurrencySpecification
import net.taler.merchantpos.R
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.config.ConfigurationReceiver
import net.taler.merchantpos.config.PosConfig
import net.taler.merchantpos.order.RestartState.ENABLED

class OrderManager(private val context: Context) : ConfigurationReceiver {

    companion object {
        val TAG: String = OrderManager::class.java.simpleName
        private const val ALL_PRODUCTS_CATEGORY_ID = -1
        private const val UNCATEGORIZED_CATEGORY_ID = -2
        private const val LEGACY_DEFAULT_CATEGORY_NAME = "Default"
    }

    private lateinit var currency: String
    private var currencySpec: CurrencySpecification? = null
    private var orderCounter: Int = 0
    private val mCurrentOrderId = MutableLiveData<Int>()
    internal val currentOrderId: LiveData<Int> = mCurrentOrderId

    private val productsByCategory = HashMap<Category, ArrayList<ConfigProduct>>()
    private val productsById = HashMap<String, ConfigProduct>()
    private val orders = LinkedHashMap<Int, MutableLiveOrder>()

    private val mProducts = MutableLiveData<List<ConfigProduct>>()
    internal val products: LiveData<List<ConfigProduct>> = mProducts

    private val mCategories = MutableLiveData<List<Category>>()
    internal val categories: LiveData<List<Category>> = mCategories
    private var currentCategory: Category? = null

    override suspend fun onConfigurationReceived(
        posConfig: PosConfig,
        currency: String,
        currencySpec: CurrencySpecification?,
    ): String? = applyConfiguration(posConfig, currency, currencySpec, resetOrders = true)

    override suspend fun onInventoryUpdated(
        posConfig: PosConfig,
        currency: String,
        currencySpec: CurrencySpecification?,
    ): String? = applyConfiguration(posConfig, currency, currencySpec, resetOrders = false)

    private fun applyConfiguration(
        posConfig: PosConfig,
        currency: String,
        currencySpec: CurrencySpecification?,
        resetOrders: Boolean,
    ): String? {
        val existingProductsByStableKey = productsById.values.associateBy { it.stableKey }
        if (posConfig.categories.isEmpty()) {
            Log.e(TAG, "No valid category found.")
            return context.getString(R.string.config_error_category)
        }

        val selectedCategoryId = if (resetOrders) ALL_PRODUCTS_CATEGORY_ID else currentCategory?.id
        val allProductsCategory = Category(
            ALL_PRODUCTS_CATEGORY_ID,
            context.getString(R.string.product_category_all_objects)
        )
        val uncategorizedCategory = Category(
            UNCATEGORIZED_CATEGORY_ID,
            context.getString(R.string.product_category_uncategorized)
        )
        val visibleCategories = posConfig.categories.filterNot(::isLegacyDefaultCategory)
        val legacyDefaultCategoryIds = posConfig.categories
            .filter(::isLegacyDefaultCategory)
            .map { it.id }
            .toSet()

        productsByCategory.clear()
        productsById.clear()
        productsByCategory[allProductsCategory] = ArrayList()
        visibleCategories.forEach { category ->
            category.selected = false
            productsByCategory[category] = ArrayList()
        }
        productsByCategory[uncategorizedCategory] = ArrayList()

        posConfig.products.forEach { product ->
            val productCurrency = product.price.currency
            val currencyMismatch = productCurrency != currency
            if (currencyMismatch) {
                Log.w(TAG, "Product $product has currency $productCurrency, $currency expected")
            }
            val remainingStock = product.stockLimit
            val productWithSpec = product.copy(
                id = existingProductsByStableKey[product.stableKey]?.id ?: product.id,
                price = product.price.withSpec(currencySpec),
                availableToSell = !currencyMismatch && (remainingStock == null || remainingStock > 0),
                remainingStock = remainingStock,
                currencyMismatch = currencyMismatch,
            )
            productsById[productWithSpec.id] = productWithSpec
            productsByCategory.getValue(allProductsCategory).add(productWithSpec)
            if (product.categories.isEmpty()) {
                productsByCategory.getValue(uncategorizedCategory).add(productWithSpec)
            }
            product.categories.forEach { categoryId ->
                if (categoryId in legacyDefaultCategoryIds) {
                    productsByCategory.getValue(uncategorizedCategory).add(productWithSpec)
                    return@forEach
                }
                val category = visibleCategories.find { it.id == categoryId }
                if (category == null) {
                    Log.e(TAG, "Product $product has unknown category $categoryId")
                    productsByCategory.getValue(uncategorizedCategory).add(productWithSpec)
                } else {
                    productsByCategory.getValue(category).add(productWithSpec)
                }
            }
        }

        this.currency = currency
        this.currencySpec = currencySpec
        val categoryList = buildList {
            add(allProductsCategory)
            addAll(visibleCategories)
            if (productsByCategory.getValue(uncategorizedCategory).isNotEmpty()) {
                add(uncategorizedCategory)
            } else {
                productsByCategory.remove(uncategorizedCategory)
            }
        }
        val selectedCategory =
            categoryList.firstOrNull { it.id == selectedCategoryId } ?: allProductsCategory
        categoryList.forEach { it.selected = it.id == selectedCategory.id }
        currentCategory = selectedCategory
        mCategories.postValue(categoryList)
        mProducts.postValue(getVisibleProducts())

        if (resetOrders) {
            orders.clear()
            orderCounter = 0
            orders[0] = createOrder(0)
            mCurrentOrderId.postValue(0)
        } else {
            trimOrdersToStockLimits()
        }
        return null
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
            orders[nextId] = createOrder(nextId)
        }
        val currentOrder = order(currentId)
        if (currentOrder.isEmpty()) orders.remove(currentId)
        else currentOrder.lastAddedProduct = null
        mCurrentOrderId.value = requireNotNull(nextId)
        updateVisibleProducts()
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
        if (currentOrder.isEmpty()) orders.remove(currentId)
        else currentOrder.lastAddedProduct = null
        mCurrentOrderId.value = requireNotNull(previousId)
        updateVisibleProducts()
    }

    fun hasPreviousOrder(currentOrderId: Int): Boolean {
        return currentOrderId != orders.keys.first()
    }

    fun hasNextOrder(currentOrderId: Int) = order(currentOrderId).restartState.map { state ->
        state == ENABLED || currentOrderId != orders.keys.last()
    }

    internal fun setCurrentCategory(category: Category) {
        currentCategory = category
        val currentCategories = categories.value.orEmpty()
        val newCategories = currentCategories.map { existing ->
            existing.copy().also { copied ->
                copied.selected = existing.id == category.id
            }
        }
        currentCategory = newCategories.firstOrNull { it.id == category.id } ?: category
        mCategories.postValue(newCategories)
        updateVisibleProducts()
    }

    @UiThread
    internal fun addProduct(orderId: Int, product: ConfigProduct) {
        order(orderId).addProduct(product)
    }

    @UiThread
    internal fun debugSeedCurrentOrder(productIds: List<String>) {
        val orderId = currentOrderId.value ?: return
        val liveOrder = order(orderId)
        productIds.forEach { productId ->
            productsById[productId]?.let(liveOrder::addProduct)
        }
        updateVisibleProducts()
    }

    @UiThread
    internal fun onOrderPaid(orderId: Int) {
        if (currentOrderId.value == orderId) {
            if (hasPreviousOrder(orderId)) previousOrder()
            else nextOrder()
        }
        orders.remove(orderId)
        updateVisibleProducts()
    }

    @UiThread
    internal fun deleteCurrentOrder() {
        val currentId = currentOrderId.value ?: return
        val orderIds = orders.keys.toList()
        val currentIndex = orderIds.indexOf(currentId)
        if (currentIndex == -1) return

        orders.remove(currentId)
        val replacementId = when {
            orders.isEmpty() -> {
                orders[currentId] = createOrder(currentId)
                currentId
            }
            currentIndex < orderIds.lastIndex -> orderIds[currentIndex + 1]
            else -> orderIds[currentIndex - 1]
        }
        mCurrentOrderId.value = replacementId
        updateVisibleProducts()
    }

    private fun order(orderId: Int): MutableLiveOrder {
        return orders[orderId] ?: throw IllegalStateException()
    }

    private fun isLegacyDefaultCategory(category: Category): Boolean {
        return category.name.equals(LEGACY_DEFAULT_CATEGORY_NAME, ignoreCase = true)
    }

    private fun createOrder(orderId: Int): MutableLiveOrder {
        return MutableLiveOrder(
            orderId,
            currency,
            currencySpec,
            productsByCategory,
            ::canAddProduct,
            ::updateVisibleProducts,
        )
    }

    private fun getVisibleProducts(): List<ConfigProduct> {
        val category = currentCategory ?: return emptyList()
        return productsByCategory[category].orEmpty().map(::decorateProduct)
    }

    private fun updateVisibleProducts() {
        mProducts.postValue(getVisibleProducts())
    }

    private fun decorateProduct(product: ConfigProduct): ConfigProduct {
        val remainingStock = remainingStock(product)
        return product.copy(
            availableToSell = !product.currencyMismatch && (remainingStock == null || remainingStock > 0),
            remainingStock = remainingStock,
        )
    }

    private fun canAddProduct(product: ConfigProduct): Boolean {
        return remainingStock(product)?.let { it > 0 } ?: true
    }

    private fun remainingStock(product: ConfigProduct): Int? {
        val stockLimit = productsById[product.id]?.stockLimit ?: product.stockLimit ?: return null
        val reserved = orders.values.sumOf { liveOrder ->
            liveOrder.order.value
                ?.products
                ?.find { it.id == product.id }
                ?.quantity
                ?: 0
        }
        return (stockLimit - reserved).coerceAtLeast(0)
    }

    private fun trimOrdersToStockLimits() {
        for (liveOrder in orders.values) {
            val order = liveOrder.order.value ?: continue
            var modified = false
            val trimmedProducts = order.products.mapNotNull { orderProduct ->
                val stockLimit = productsById[orderProduct.id]?.stockLimit ?: return@mapNotNull orderProduct
                if (orderProduct.quantity <= stockLimit) return@mapNotNull orderProduct
                modified = true
                if (stockLimit <= 0) null
                else orderProduct.copy(quantity = stockLimit)
            }
            if (modified) {
                liveOrder.order.postValue(order.copy(products = trimmedProducts))
            }
        }
    }
}
