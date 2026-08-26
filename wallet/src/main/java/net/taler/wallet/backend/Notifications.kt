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

package net.taler.wallet.backend

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.taler.wallet.events.ObservabilityEvent
import net.taler.wallet.transactions.TransactionState

@Serializable(with = NotificationPayloadSerializer::class)
sealed class NotificationPayload {
    @Serializable
    @SerialName("balance-change")
    data class BalanceChange(
        /**
         * If set to true, the balance change is internal
         * to the wallet and not visible to the user.
         */
        val isInternal: Boolean? = null,
        val hintTransactionId: String? = null,
    ) : NotificationPayload()

    @Serializable
    @SerialName("transaction-state-transition")
    data class TransactionStateTransition(
        val transactionId: String? = null,
        val causeHint: String? = null,
        val oldTxState: TransactionState? = null,
        val newTxState: TransactionState? = null,
        val errorInfo: TalerErrorInfo? = null,
    ) : NotificationPayload()

    @Serializable
    @SerialName("task-observability-event")
    data class TaskObservabilityEvent(
        val taskId: String? = null,
        val event: ObservabilityEvent? = null,
    ) : NotificationPayload()

    @Serializable
    @SerialName("request-observability-event")
    data class RequestObservabilityEvent(
        val requestId: Int,
        val operation: String? = null,
        val event: ObservabilityEvent? = null,
    ) : NotificationPayload()

    @Serializable
    @SerialName("database-maintenance-progress")
    data class DatabaseMaintenanceProgress(
        val operation: String,
        val phase: String,
        val progressToken: String? = null,
        val completionPercent: Int? = null,
        val error: TalerErrorInfo? = null,
    ) : NotificationPayload()

    @Serializable(with = UnknownPayloadSerializer::class)
    data class Unknown(
        val type: String? = null,
        val raw: JsonObject,
    ) : NotificationPayload()
}

object NotificationPayloadSerializer :
    JsonContentPolymorphicSerializer<NotificationPayload>(NotificationPayload::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<NotificationPayload> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.contentOrNull

        val serializer: DeserializationStrategy<NotificationPayload> = when (type) {
            "balance-change" -> NotificationPayload.BalanceChange.serializer()
            "transaction-state-transition" -> NotificationPayload.TransactionStateTransition.serializer()
            "task-observability-event" -> NotificationPayload.TaskObservabilityEvent.serializer()
            "request-observability-event" -> NotificationPayload.RequestObservabilityEvent.serializer()
            "database-maintenance-progress" -> NotificationPayload.DatabaseMaintenanceProgress.serializer()
            else -> NotificationPayload.Unknown.serializer()
        }

        return serializer
    }
}

object UnknownPayloadSerializer : KSerializer<NotificationPayload.Unknown> {
    // Delegate descriptor to JsonObject.serializer().descriptor so kotlinx.serialization
    // knows this serializer operates directly on a raw JSON object structure.
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): NotificationPayload.Unknown {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("UnknownPayloadSerializer can only be used with Json")

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.contentOrNull

        return NotificationPayload.Unknown(
            type = type,
            raw = jsonObject,
        )
    }

    override fun serialize(encoder: Encoder, value: NotificationPayload.Unknown) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("UnknownPayloadSerializer can only be used with Json")

        jsonEncoder.encodeJsonElement(value.raw)
    }
}
