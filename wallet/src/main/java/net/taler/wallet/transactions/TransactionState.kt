/*
 * This file is part of GNU Taler
 * (C) 2023 Taler Systems S.A.
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

package net.taler.wallet.transactions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = TransactionStateSerializer::class)
data class TransactionState(
    val major: TransactionMajorState,
    val minor: TransactionMinorState? = null,
    /**
     * Exact serialized name of [major] as received from wallet-core.
     * Only set when [major] is [TransactionMajorState.Unknown], so that
     * unrecognized/future states survive a serialize round trip.
     */
    val majorName: String? = null,
    /**
     * Exact serialized name of [minor] as received from wallet-core.
     * Only set when [minor] is [TransactionMinorState.Unknown].
     */
    val minorName: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        return if (other is TransactionState)
            // if other.minor is null, then ignore minor in comparison
            major == other.major && (other.minor == null || minor == other.minor)
        else false
    }

    override fun hashCode(): Int {
        var result = major.hashCode()
        result = 31 * result + (minor?.hashCode() ?: 0)
        return result
    }
}

@Serializable(with = TransactionMajorStateSerializer::class)
enum class TransactionMajorState(val serialName: String) {
    Unknown("unknown"),
    None("none"),
    Pending("pending"),
    Done("done"),
    Aborting("aborting"),
    Aborted("aborted"),
    Dialog("dialog"),
    Finalizing("finalizing"),
    Suspended("suspended"),
    SuspendedFinalizing("suspended-finalizing"),
    SuspendedAborting("suspended-aborting"),
    Failed("failed"),
    Expired("expired"),
    Deleted("deleted"),
}

object TransactionMajorStateSerializer : KSerializer<TransactionMajorState> {
    private val serialNames = TransactionMajorState.entries.associateBy { it.serialName }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransactionMajorState", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TransactionMajorState =
        fromName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: TransactionMajorState) {
        encoder.encodeString(value.serialName)
    }

    fun fromName(serialName: String): TransactionMajorState =
        serialNames[serialName] ?: TransactionMajorState.Unknown
}

@Serializable(with = TransactionMinorStateSerializer::class)
enum class TransactionMinorState(val serialName: String) {
    AbortingBank("aborting-bank"),
    AcceptRefund("accept-refund"),
    AutoRefund("auto-refund"),
    BalanceKycRequired("balance-kyc"),
    Bank("bank"),
    BankConfirmTransfer("bank-confirm-transfer"),
    BankRegisterReserve("bank-register-reserve"),
    CheckRefund("check-refund"),
    ClaimProposal("claim-proposal"),
    CompletedByOtherWallet("completed-by-other-wallet"),
    CreatePurse("create-purse"),
    DeletePurse("delete-purse"),
    Deposit("deposit"),
    Exchange("exchange"),
    ExchangeWaitReserve("exchange-wait-reserve"),
    KycAuthRequired("kyc-auth"),
    KycInit("kyc-init"),
    KycRequired("kyc"),
    Merge("merge"),
    PaidByOther("paid-by-other"),
    Proposed("proposed"),
    Ready("ready"),
    RebindSession("rebind-session"),
    Refresh("refresh"),
    Refused("refused"),
    Repurchase("repurchase"),
    SubmitPayment("submit-payment"),
    Track("track"),
    Unknown("unknown"),
    Withdraw("withdraw"),
    Abort("abort"),
}

object TransactionMinorStateSerializer : KSerializer<TransactionMinorState> {
    private val serialNames = TransactionMinorState.entries.associateBy { it.serialName }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransactionMinorState", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TransactionMinorState =
        fromName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: TransactionMinorState) {
        encoder.encodeString(value.serialName)
    }

    fun fromName(serialName: String): TransactionMinorState =
        serialNames[serialName] ?: TransactionMinorState.Unknown
}

object TransactionStateSerializer : KSerializer<TransactionState> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): TransactionState {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("TransactionStateSerializer can only be used with Json")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val majorName = obj["major"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val minorName = obj["minor"]?.jsonPrimitive?.contentOrNull
        return TransactionState(
            major = TransactionMajorStateSerializer.fromName(majorName),
            minor = minorName?.let(TransactionMinorStateSerializer::fromName),
            majorName = majorName,
            minorName = minorName,
        )
    }

    override fun serialize(encoder: Encoder, value: TransactionState) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("TransactionStateSerializer can only be used with Json")
        jsonEncoder.encodeJsonElement(buildJsonObject {
            put(
                "major",
                JsonPrimitive(
                    if (value.major == TransactionMajorState.Unknown && value.majorName != null) value.majorName
                    else value.major.serialName,
                ),
            )
            val minorName = value.minor?.let { minor ->
                if (minor == TransactionMinorState.Unknown && value.minorName != null) value.minorName
                else minor.serialName
            } ?: value.minorName
            if (minorName != null) put("minor", JsonPrimitive(minorName))
        })
    }
}
