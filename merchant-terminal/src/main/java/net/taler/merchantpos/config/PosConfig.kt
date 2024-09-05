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

package net.taler.merchantpos.config

import android.os.Build.VERSION.SDK_INT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.common.ContractProduct
import net.taler.common.Product
import net.taler.common.TalerUtils
import net.taler.common.Tax
import net.taler.merchantlib.MerchantConfig
import java.util.UUID

sealed class Config {
    abstract fun isValid(): Boolean
    abstract fun hasPassword(): Boolean
    abstract fun savePassword(): Boolean

    /**
     * JSON config URL + user/password
     */
    data class Old(
        val configUrl: String,
        val username: String,
        val password: String,
        val savePassword: Boolean,
    ): Config() {
        override fun isValid() = configUrl.isNotBlank()
        override fun hasPassword() = password.isNotBlank()
        override fun savePassword() = savePassword
    }

    /**
     * Merchant URL + access token
     */
    data class New(
        val merchantUrl: String,
        val accessToken: String,
        val savePassword: Boolean,
    ): Config() {
        override fun isValid() = merchantUrl.isNotBlank()
        override fun hasPassword() = accessToken.isNotBlank()
        override fun savePassword() = savePassword
    }
}

@Serializable
data class PosConfig(
    @SerialName("config")
    val merchantConfig: MerchantConfig? = null ,
    val categories: List<Category>,
    val products: List<ConfigProduct>
)

@Serializable
data class Category(
    val id: Int,
    val name: String,
    @SerialName("name_i18n")
    val nameI18n: Map<String, String>? = null
) {
    var selected: Boolean = false
    val localizedName: String
        get() = if (SDK_INT >= 26) TalerUtils.getLocalizedString(nameI18n, name) else name
}

@Serializable
data class ConfigProduct(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("product_id")
    override val productId: String? = null,
    override val description: String,
    @SerialName("description_i18n")
    override val descriptionI18n: Map<String, String>? = null,
    override val price: Amount,
    @SerialName("delivery_location")
    override val location: String? = null,
    override val image: String? = null,
    override val taxes: Set<Tax>? = null,
    val categories: List<Int>,
    val quantity: Int = 0
) : Product() {
    val totalPrice by lazy { price * quantity }

    fun toContractProduct() = ContractProduct(
        productId = productId,
        description = description,
        descriptionI18n = descriptionI18n,
        price = price,
        location = location,
        image = image,
        taxes = taxes,
        quantity = quantity
    )

    override fun equals(other: Any?) = other is ConfigProduct && id == other.id
    override fun hashCode() = id.hashCode()
}
