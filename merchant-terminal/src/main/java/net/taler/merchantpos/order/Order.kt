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

import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct

data class Order(
    val id: Int,
    val currency: String,
    val currencySpec: CurrencySpecification?,
    val availableCategories: Map<Int, Category>,
    val products: List<ConfigProduct> = emptyList(),
) {
    val title: String = id.toString()
    val summary: String
        get() {
            if (products.size == 1) return products[0].description
            return getCategoryQuantities().map { (category: Category, quantity: Int) ->
                "$quantity x ${category.localizedName}"
            }.joinToString()
        }
    val total: Amount
        get() {
            var total = Amount.zero(currency).withSpec(currencySpec)
            products.forEach { product ->
                total += product.price * product.quantity
            }
            return total.withSpec(currencySpec)
        }

    operator fun plus(product: ConfigProduct): Order {
        val updatedProducts = products.toMutableList()
        val i = updatedProducts.indexOfFirst { it.id == product.id }
        if (i == -1) {
            updatedProducts.add(product.copy(quantity = 1))
        } else {
            val quantity = updatedProducts[i].quantity
            updatedProducts[i] = updatedProducts[i].copy(quantity = quantity + 1)
        }
        return copy(products = updatedProducts)
    }

    operator fun minus(product: ConfigProduct): Order {
        val updatedProducts = products.toMutableList()
        val i = updatedProducts.indexOfFirst { it.id == product.id }
        if (i == -1) return this
        val quantity = updatedProducts[i].quantity
        if (quantity <= 1) {
            updatedProducts.removeAt(i)
        } else {
            updatedProducts[i] = updatedProducts[i].copy(quantity = quantity - 1)
        }
        return copy(products = updatedProducts)
    }

    private fun getCategoryQuantities(): HashMap<Category, Int> {
        val categories = HashMap<Category, Int>()
        products.forEach { product ->
            val categoryId = product.categories[0]
            val category = availableCategories[categoryId] ?: return@forEach // custom products
            val oldQuantity = categories[category] ?: 0
            categories[category] = oldQuantity + product.quantity
        }
        return categories
    }

    /**
     * Returns a map of i18n summaries for each locale present in *all* given [Category]s
     * or null if there's no locale that fulfills this criteria.
     */
    private val summaryI18n: Map<String, String>?
        get() {
            if (products.size == 1) return products[0].descriptionI18n
            val categoryQuantities = getCategoryQuantities()
            // get all available locales
            val availableLocales = categoryQuantities.mapNotNull { (category, _) ->
                val nameI18n = category.nameI18n
                // if one category doesn't have locales, we can return null here already
                nameI18n?.keys ?: return null
            }.flatten().toHashSet()
            // remove all locales not supported by all categories
            categoryQuantities.forEach { (category, _) ->
                // category.nameI18n should be non-null now
                availableLocales.retainAll(category.nameI18n!!.keys)
                if (availableLocales.isEmpty()) return null
            }
            return availableLocales.associateWith { locale ->
                categoryQuantities.map { (category, quantity) ->
                    // category.nameI18n should be non-null now
                    "$quantity x ${category.nameI18n!![locale]}"
                }.joinToString()
            }
        }

    fun toContractTerms(includeProducts: Boolean = true): net.taler.common.Order {
        return net.taler.common.Order(
            summary = summary,
            summaryI18n = summaryI18n,
            amount = total,
            products = if (includeProducts) products.map { it.toContractProduct() } else null,
        )
    }

}
