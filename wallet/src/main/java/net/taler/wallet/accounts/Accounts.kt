/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.accounts

import android.net.Uri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.taler.common.Bech32
import net.taler.wallet.backend.TalerErrorInfo
import androidx.core.net.toUri

@Serializable
data class KnownBankAccountInfo(
    val bankAccountId: String,
    val paytoUri: String,

    /**
     * Did we previously complete a KYC process for this bank account?
     */
    val kycCompleted: Boolean,

    /**
     * Currencies supported by the bank, if known.
     */
    val currencies: List<String>? = null,

    val label: String? = null,
)

@Serializable
sealed class ListBankAccountsResult {
    @Serializable
    data object None: ListBankAccountsResult()

    @Serializable
    data class Success(
        val accounts: List<KnownBankAccountInfo>,
        val currency: String?,
    ): ListBankAccountsResult()

    @Serializable
    data class Error(val error: TalerErrorInfo): ListBankAccountsResult()
}

@Serializable
data class ListBankAccountsResponse(
    val accounts: List<KnownBankAccountInfo>,
)

@Serializable
data class AddBankAccountResponse(
    val bankAccountId: String,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("targetType")
sealed class PaytoUri(
    val isKnown: Boolean,
    val targetType: String,
) {
    abstract val targetPath: String
    abstract val params: Map<String, String>
    abstract val receiverName: String?

    companion object {
        fun parse(paytoUri: String): PaytoUri? {
            val uri = paytoUri.toUri()
            if (uri.scheme != "payto") return null
            if (uri.pathSegments.isEmpty()) return null
            return when (uri.authority?.lowercase()) {
                "iban" -> PaytoUriIban.fromString(uri)
                "x-taler-bank" -> PaytoUriTalerBank.fromString(uri)
                "bitcoin" -> PaytoUriBitcoin.fromString(uri)
                else -> null
            }
        }
    }
}

@Serializable
@SerialName("iban")
data class PaytoUriIban(
    val iban: String,
    val bic: String? = "SANDBOXX",
    override val targetPath: String,
    override val params: Map<String, String>,
    override val receiverName: String?,
    val receiverPostalCode: String?,
    val receiverTown: String?,
) : PaytoUri(
    isKnown = true,
    targetType = "iban",
) {
    val paytoUri: String
        get() = Uri.Builder()
            .scheme("payto")
            .authority(targetType)
            .apply { if (bic != null) appendPath(bic) }
            .appendPath(iban)
            .appendQueryParameter("receiver-name", receiverName)
            .appendQueryParameter("receiver-postal-code", receiverPostalCode)
            .appendQueryParameter("receiver-town", receiverTown)
            .apply {
                params.forEach { (key, value) ->
                    if (value.isNotEmpty() && build().getQueryParameter(key) == null) {
                        appendQueryParameter(key, value)
                    }
                }
            }.build().toString()

    companion object {
        fun fromString(uri: Uri): PaytoUriIban? {
            return PaytoUriIban(
                iban = uri.lastPathSegment ?: return null,
                bic = if (uri.pathSegments.size > 1) {
                    uri.pathSegments.first() ?: return null
                } else null,
                params = uri.queryParametersMap,
                receiverName = uri.getQueryParameter("receiver-name"),
                receiverPostalCode = uri.getQueryParameter("receiver-postal-code"),
                receiverTown = uri.getQueryParameter("receiver-town"),
                targetPath = "",
            )
        }
    }
}

@Serializable
@SerialName("x-taler-bank")
data class PaytoUriTalerBank(
    val host: String,
    val account: String,
    override val targetPath: String,
    override val params: Map<String, String>,
    override val receiverName: String?,
) : PaytoUri(
    isKnown = true,
    targetType = "x-taler-bank",
) {
    val paytoUri: String
        get() = Uri.Builder()
            .scheme("payto")
            .authority(targetType)
            .appendPath(host)
            .appendPath(account)
            .apply {
                params.forEach { (key, value) ->
                    if (value.isNotEmpty()) {
                        appendQueryParameter(key, value)
                    }
                }
            }
            .build().toString()

    companion object {
        fun fromString(uri: Uri): PaytoUriTalerBank? {
            return PaytoUriTalerBank(
                host = uri.pathSegments.getOrNull(0) ?: return null,
                account = uri.pathSegments.getOrNull(1) ?: return null,
                params = uri.queryParametersMap,
                receiverName = uri.getQueryParameter("receiver-name"),
                targetPath = "",
            )
        }
    }
}

@Serializable
@SerialName("bitcoin")
data class PaytoUriBitcoin(
    @SerialName("segwitAddrs")
    val segwitAddresses: List<String>,
    override val targetPath: String,
    override val params: Map<String, String> = emptyMap(),
    override val receiverName: String?,
) : PaytoUri(
    isKnown = true,
    targetType = "bitcoin",
) {
    val paytoUri: String
        get() = Uri.Builder()
            .scheme("payto")
            .authority(targetType)
            .apply {
                segwitAddresses.forEach { address ->
                    appendPath(address)
                }
            }
            .apply {
                params.forEach { (key, value) ->
                    if (value.isNotEmpty()) {
                        appendQueryParameter(key, value)
                    }
                }
            }
            .build().toString()

    companion object {
        fun fromString(uri: Uri): PaytoUriBitcoin? {
            val msg = uri.getQueryParameter("message").orEmpty()
            val reg = "\\b([A-Z0-9]{52})\\b".toRegex().find(msg)
            val reserve = reg?.value
                ?: uri.getQueryParameter("subject")
                ?: return null
            val segwitAddresses = Bech32.generateFakeSegwitAddress(
                reservePub = reserve,
                addr = uri.pathSegments.firstOrNull()
                    ?: return null,
            )

            return PaytoUriBitcoin(
                segwitAddresses = segwitAddresses,
                params = uri.queryParametersMap,
                receiverName = uri.getQueryParameter("receiver-name"),
                targetPath = "",
            )
        }
    }
}

val Uri.queryParametersMap: Map<String, String>
    get() = queryParameterNames.mapNotNull { name ->
        getQueryParameter(name)?.let { name to it }
    }.toMap()