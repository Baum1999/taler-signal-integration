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

package net.taler.cashier.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import net.taler.common.Timestamp
import okhttp3.Credentials

@Serializable
data class TokenSuccessResponse(
    val expiration: Timestamp,

    @SerialName("access_token")
    val accessToken: String,
)

sealed class TokenResult {
    data class Success(
        val expiration: Timestamp,
        val accessToken: String,
    ): TokenResult()

    data class TanRequired(
        val challenges: List<Challenge>,
        val combiAnd: Boolean,
    ): TokenResult()

    data class Error(
        val authError: Boolean,
        val msg: String,
    ): TokenResult()
}

data class Config(
    val bankUrl: String,
    val username: String,
    val password: String = "",
    val accessToken: String? = null,
    val expiration: Timestamp? = null,
) {
    val basicAuth: String get() = Credentials.basic(username, password)
    val bearerAuth: String? get() = accessToken?.let { "Bearer $it" }

    fun isTokenValid(): Boolean {
        if (accessToken == null || expiration == null) return false
        return expiration > Timestamp.now()
    }
}

@Serializable
data class ConfigResponse(
    val version: String,
    val currency: String,
)

@Serializable
enum class TanChannel {
    @SerialName("sms")
    SMS,

    @SerialName("email")
    EMAIL,
}

@Serializable
data class Challenge(
    @SerialName("challenge_id")
    val challengeId: String,

    @SerialName("tan_channel")
    val tanChannel: TanChannel,

    @SerialName("tan_info")
    val tanInfo: String,
)

@Serializable
data class ChallengesResponse(
    val challenges: List<Challenge>,

    // True if **all** challenges must be solved (AND), false if
    // it is sufficient to solve one of them (OR).
    @SerialName("combi_and")
    val combiAnd: Boolean,
)

@Serializable
data class ChallengeConfirmRequest(
    val tan: String,
)

@Serializable
data class TokenRequest(
    val scope: String,
    val duration: TokenDuration
)

@Serializable(with = TokenDuration.Serializer::class)
sealed class TokenDuration {
    data object Forever : TokenDuration()
    data class Micros(val us: Long) : TokenDuration()

    object Serializer : KSerializer<TokenDuration> {
        // describe an object with a single property "d_us"
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TokenDuration") {
            element<JsonElement>("d_us")
        }

        override fun serialize(encoder: Encoder, value: TokenDuration) {
            // we need a Json-specific encoder
            val jsonEncoder = encoder as? JsonEncoder
                ?: throw SerializationException("Can be serialized only by JSON")
            // build the JSON object
            val obj = when (value) {
                is Forever -> buildJsonObject {
                    // for "forever", we still emit an object,
                    // here storing the literal string under "d_us"
                    put("d_us", JsonPrimitive("forever"))
                }
                is Micros -> buildJsonObject {
                    put("d_us", JsonPrimitive(value.us))
                }
            }
            jsonEncoder.encodeJsonElement(obj)
        }

        override fun deserialize(decoder: Decoder): TokenDuration {
            // we need a Json-specific decoder
            val jsonDecoder = decoder as? JsonDecoder
                ?: throw SerializationException("Can be deserialized only by JSON")
            val element = jsonDecoder.decodeJsonElement()
            if (element !is JsonObject) {
                throw SerializationException("Expected JSON object for TokenDuration, got: $element")
            }
            // look up our single field
            val field = element["d_us"]
                ?: throw SerializationException("Missing 'd_us' field in $element")
            return when {
                field is JsonPrimitive && field.longOrNull != null ->
                    Micros(field.long)
                field is JsonPrimitive && field.isString && field.content == "forever" ->
                    Forever
                else ->
                    throw SerializationException("Invalid 'd_us' value: $field")
            }
        }
    }
}

sealed class ConfigResult {
    data class Error(val authError: Boolean, val msg: String) : ConfigResult()
    data object Offline : ConfigResult()
    data object Success : ConfigResult()
    data object Unknown : ConfigResult()
    data class TanRequired(val challenges: List<Challenge>, val combiAnd: Boolean): ConfigResult()
}
