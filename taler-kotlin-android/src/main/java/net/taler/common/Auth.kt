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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Serializable
data class TokenSuccessResponse(
    val expiration: Timestamp,

    @SerialName("access_token")
    val accessToken: String,
)

@Serializable
data class ChallengesResponse(
    val challenges: List<Challenge>,

    @SerialName("combi_and")
    val combiAnd: Boolean,
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
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TokenDuration") {
            element<JsonElement>("d_us")
        }

        override fun serialize(encoder: Encoder, value: TokenDuration) {
            val jsonEncoder = encoder as? JsonEncoder
                ?: throw SerializationException("Can be serialized only by JSON")
            val obj = when (value) {
                is Forever -> buildJsonObject {
                    put("d_us", JsonPrimitive("forever"))
                }
                is Micros -> buildJsonObject {
                    put("d_us", JsonPrimitive(value.us))
                }
            }
            jsonEncoder.encodeJsonElement(obj)
        }

        override fun deserialize(decoder: Decoder): TokenDuration {
            val jsonDecoder = decoder as? JsonDecoder
                ?: throw SerializationException("Can be deserialized only by JSON")
            val element = jsonDecoder.decodeJsonElement()
            if (element !is JsonObject) {
                throw SerializationException("Expected JSON object for TokenDuration, got: $element")
            }
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

@Serializable(with = TokenExpiration.Serializer::class)
sealed class TokenExpiration {
    data class Seconds(val t_s: Long) : TokenExpiration()
    data object Never : TokenExpiration()

    object Serializer : KSerializer<TokenExpiration> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("TokenExpiration") {
                element<JsonElement>("t_s")
            }

        override fun serialize(encoder: Encoder, value: TokenExpiration) {
            val jsonEncoder = encoder as? JsonEncoder
                ?: throw SerializationException("TokenExpiration can be serialized only by JSON")
            val obj = when (value) {
                is Seconds ->
                    buildJsonObject { put("t_s", JsonPrimitive(value.t_s)) }
                Never ->
                    buildJsonObject { put("t_s", JsonPrimitive("never")) }
            }
            jsonEncoder.encodeJsonElement(obj)
        }

        override fun deserialize(decoder: Decoder): TokenExpiration {
            val jsonDecoder = decoder as? JsonDecoder
                ?: throw SerializationException("TokenExpiration can be deserialized only by JSON")
            val element = jsonDecoder.decodeJsonElement()
            if (element !is JsonObject) {
                throw SerializationException("Expected JSON object for TokenExpiration, got: $element")
            }
            val field = element["t_s"]
                ?: throw SerializationException("Missing 't_s' in TokenExpiration: $element")

            return when {
                field is JsonPrimitive && field.longOrNull != null ->
                    Seconds(field.long)
                field is JsonPrimitive && field.isString && field.content == "never" ->
                    Never
                else ->
                    throw SerializationException("Invalid 't_s' value in TokenExpiration: $field")
            }
        }
    }
}
