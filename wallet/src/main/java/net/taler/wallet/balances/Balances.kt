/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.wallet.balances

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.wallet.PrefsScopeInfo
import net.taler.wallet.ScopeInfoType

@Serializable
data class BalanceItem(
    val scopeInfo: ScopeInfo,
    val available: Amount,
    val pendingIncoming: Amount,
    val pendingOutgoing: Amount,
    val disablePeerPayments: Boolean,
    val shoppingUrls: List<String> = emptyList(),
) {
    val currency: String get() = available.currency
}

@Serializable
sealed class ScopeInfo {
    abstract val currency: String

    @Serializable
    @SerialName("global")
    data class Global(
        override val currency: String
    ): ScopeInfo()

    @Serializable
    @SerialName("exchange")
    data class Exchange(
        override val currency: String,
        val url: String,
    ): ScopeInfo()

    @Serializable
    @SerialName("auditor")
    data class Auditor(
        override val currency: String,
        val url: String,
    ): ScopeInfo()

    @Serializable
    @SerialName("exchange-legacy-keys")
    data class ExchangeLegacyKeys(
        override val currency: String,
        val url: String,
        val masterPub: String,
    ): ScopeInfo()

    fun toPrefs(): PrefsScopeInfo {
        val type = when (this) {
            is Global -> ScopeInfoType.GLOBAL
            is Exchange -> ScopeInfoType.EXCHANGE
            is Auditor -> ScopeInfoType.AUDITOR
            is ExchangeLegacyKeys -> ScopeInfoType.EXCHANGE_LEGACY_KEYS
        }

        val url = when (this) {
            is Exchange -> url
            is Auditor -> url
            is ExchangeLegacyKeys -> url
            else -> null
        }

        val masterPub = when (this) {
            is ExchangeLegacyKeys -> masterPub
            else -> null
        }

        return PrefsScopeInfo
            .newBuilder()
            .setType(type)
            .setCurrency(currency)
            .apply {
                if (url != null)
                    setUrl(url)
                if (masterPub != null)
                    setMasterPub(masterPub)
            }.build()
    }

    companion object {
        fun fromPrefs(scope: PrefsScopeInfo) = when (scope.type) {
            ScopeInfoType.GLOBAL -> Global(currency = scope.currency)

            ScopeInfoType.EXCHANGE -> Exchange(
                currency = scope.currency,
                url = scope.url,
            )

            ScopeInfoType.AUDITOR -> Auditor(
                currency =  scope.currency,
                url = scope.url,
            )

            ScopeInfoType.EXCHANGE_LEGACY_KEYS -> ExchangeLegacyKeys(
                currency = scope.currency,
                url = scope.url,
                masterPub = scope.masterPub,
            )

            else -> null
        }
    }
}