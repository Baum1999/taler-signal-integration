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

package net.taler.common

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class ContractVersion(val version: Int) {
    V0(0),
    V1(1),
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = ContractTermsSerializer::class)
sealed class ContractTerms {
    abstract val version: ContractVersion
    abstract val summary: String
    abstract val summaryI18n: Map<String, String>?
    abstract val orderId: String
    abstract val fulfillmentUrl: String?
    abstract val fulfillmentMessage: String?
    abstract val fulfillmentMessageI18n: Map <String, String>?
    abstract val products: List<ContractProduct>?
    abstract val refundDeadline: Timestamp?
    abstract val payDeadline: Timestamp?
    abstract val wireTransferDeadline: Timestamp?
    abstract val merchantBaseUrl: String
    abstract val merchant: Merchant
    abstract val exchanges: List<Exchange>
    abstract val deliveryLocation: Location?
    abstract val deliveryDate: Timestamp?

    @Serializable
    @JsonClassDiscriminator("version")
    data class V0 (
        override val summary: String,

        @SerialName("summary_i18n")
        override val summaryI18n: Map<String, String>? = null,

        @SerialName("order_id")
        override val orderId: String,

        @SerialName("fulfillment_url")
        override val fulfillmentUrl: String? = null,

        @SerialName("fulfillment_message")
        override val fulfillmentMessage: String? = null,

        @SerialName("fulfillment_message_i18n")
        override val fulfillmentMessageI18n: Map<String, String>? = null,

        override val products: List<ContractProduct>? = null,

        @SerialName("refund_deadline")
        override val refundDeadline: Timestamp? = null,

        @SerialName("pay_deadline")
        override val payDeadline: Timestamp? = null,

        @SerialName("wire_transfer_deadline")
        override val wireTransferDeadline: Timestamp? = null,

        @SerialName("merchant_base_url")
        override val merchantBaseUrl: String,

        override val merchant: Merchant,

        override val exchanges: List<Exchange> = listOf(),

        @SerialName("delivery_location")
        override val deliveryLocation: Location? = null,

        @SerialName("delivery_date")
        override val deliveryDate: Timestamp? = null,

        val amount: Amount,

        @SerialName("max_fee")
        val maxFee: Amount,
    ) : ContractTerms() {
        override val version: ContractVersion = ContractVersion.V0
    }

    @Serializable
    @JsonClassDiscriminator("version")
    data class V1 (
        override val summary: String,

        @SerialName("summary_i18n")
        override val summaryI18n: Map<String, String>? = null,

        @SerialName("order_id")
        override val orderId: String,

        @SerialName("fulfillment_url")
        override val fulfillmentUrl: String? = null,

        @SerialName("fulfillment_message")
        override val fulfillmentMessage: String? = null,

        @SerialName("fulfillment_message_i18n")
        override val fulfillmentMessageI18n: Map<String, String>? = null,

        override val products: List<ContractProduct>? = null,

        @SerialName("refund_deadline")
        override val refundDeadline: Timestamp? = null,

        @SerialName("pay_deadline")
        override val payDeadline: Timestamp? = null,

        @SerialName("wire_transfer_deadline")
        override val wireTransferDeadline: Timestamp? = null,

        @SerialName("merchant_base_url")
        override val merchantBaseUrl: String,

        override val merchant: Merchant,

        override val exchanges: List<Exchange> = listOf(),

        @SerialName("delivery_location")
        override val deliveryLocation: Location? = null,

        @SerialName("delivery_date")
        override val deliveryDate: Timestamp? = null,

        val choices: List<ContractChoice>,

        @SerialName("token_families")
        val tokenFamilies: Map<String, ContractTokenFamily>,
    ) : ContractTerms() {
        override val version: ContractVersion = ContractVersion.V1
    }
}

@Serializable
data class Merchant(
    val name: String,
    val email: String? = null,
    val website: String? = null,
    val logo: String? = null,
    val address: Location? = null,
    val jurisdiction: Location? = null
)

@Serializable
data class Location(
    val country: String? = null,
    @SerialName("country_subdivision")
    val countrySubdivision: String? = null,
    val district: String? = null,
    val town: String? = null,
    @SerialName("town_location")
    val townLocation: String? = null,
    @SerialName("post_code")
    val postCode: String? = null,
    val street: String? = null,
    @SerialName("building_name")
    val buildingName: String? = null,
    @SerialName("building_number")
    val buildingNumber: String? = null,
    @SerialName("address_lines")
    val addressLines: List<String>? = null,
)

@Serializable
data class ContractProduct(
    @SerialName("product_id")
    override val productId: String? = null,
    @SerialName("product_name")
    override val productName: String? = null,
    override val description: String,
    @SerialName("description_i18n")
    override val descriptionI18n: Map<String, String>? = null,
    override val price: Amount? = null,
    @SerialName("delivery_location")
    override val location: String? = null,
    override val image: String? = null,
    override val taxes: Set<Tax>? = null,
    val quantity: Int = 1,
) : OrderProduct() {
    val totalPrice: Amount? by lazy {
        price?.let { price * quantity }
    }
}

@Serializable
data class Exchange(
    val url: String,
)

@Serializable
data class Tax(
    val name: String,
    val tax: Amount,
)

@Serializable
data class ContractChoice(
    val amount: Amount,
    val description: String? = null,
    @SerialName("description_i18n")
    val descriptionI18n: Map<String, String>? = null,
    val inputs: List<ContractInput>,
    val outputs: List<ContractOutput>,
    @SerialName("max_fee")
    val maxFee: Amount,
)

@Serializable
enum class ContractInputType {
    @SerialName("token")
    Token,
}

@Serializable
sealed class ContractInput {
    abstract val type: ContractInputType

    @Serializable
    @SerialName("token")
    data class Token(
        @SerialName("token_family_slug")
        val tokenFamilySlug: String,
        val count: Int = 1,
    ): ContractInput() {
        override val type: ContractInputType = ContractInputType.Token
    }
}

@Serializable
enum class ContractOutputType {
    @SerialName("token")
    Token,
}

@Serializable
sealed class ContractOutput {
    abstract val type: ContractOutputType

    @Serializable
    @SerialName("token")
    data class Token(
        @SerialName("token_family_slug")
        val tokenFamilySlug: String,
        val count: Int = 1,
    ): ContractOutput() {
        override val type: ContractOutputType = ContractOutputType.Token
    }
}

@Serializable
data class ContractTokenFamily(
    val name: String,
    val description: String,
    val descriptionI18n: Map<String, String>? = null,
    val details: ContractTokenDetails,
    val critical: Boolean,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("class")
sealed class ContractTokenDetails {
    @Serializable
    @SerialName("subscription")
    data object Subscription: ContractTokenDetails()

    @Serializable
    @SerialName("discount")
    data object Discount: ContractTokenDetails()
}

object ContractTermsSerializer : JsonContentPolymorphicSerializer<ContractTerms>(ContractTerms::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ContractTerms> {
        return when(val type = element.jsonObject["version"]?.jsonPrimitive?.intOrNull) {
            null, 0 -> ContractTerms.V0.serializer()
            1 -> ContractTerms.V1.serializer()
            else -> error("unknown contract version $type")
        }
    }
}