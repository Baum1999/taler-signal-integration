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

package net.taler.wallet.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PerformanceStat {
    abstract val avgDurationMs: Int
    abstract val maxDurationMs: Int
    abstract val minDurationMs: Int
    abstract val totalDurationMs: Int
    abstract val count: Int

    @Serializable
    @SerialName("http-fetch")
    data class HttpFetch(
        val url: String,
        override val avgDurationMs: Int,
        override val maxDurationMs: Int,
        override val minDurationMs: Int,
        override val totalDurationMs: Int,
        override val count: Int,
    ): PerformanceStat()

    @Serializable
    @SerialName("db-query")
    data class DbQuery(
        val name: String,
        val location: String,
        override val avgDurationMs: Int,
        override val maxDurationMs: Int,
        override val minDurationMs: Int,
        override val totalDurationMs: Int,
        override val count: Int,
    ): PerformanceStat()

    @Serializable
    @SerialName("crypto")
    data class Crypto(
        val operation: String,
        override val avgDurationMs: Int,
        override val maxDurationMs: Int,
        override val minDurationMs: Int,
        override val totalDurationMs: Int,
        override val count: Int,
    ): PerformanceStat()

    @Serializable
    @SerialName("wallet-request")
    data class WalletRequest(
        val operation: String,
        override val avgDurationMs: Int,
        override val maxDurationMs: Int,
        override val minDurationMs: Int,
        override val totalDurationMs: Int,
        override val count: Int,
    ): PerformanceStat()

    @Serializable
    @SerialName("wallet-task")
    data class WalletTask(
        val taskId: String,
        override val avgDurationMs: Int,
        override val maxDurationMs: Int,
        override val minDurationMs: Int,
        override val totalDurationMs: Int,
        override val count: Int,
    ): PerformanceStat()
}

@Serializable
data class PerformanceTable(
    @SerialName("http-fetch")
    val httpFetch: List<PerformanceStat.HttpFetch> = emptyList(),

    @SerialName("db-query")
    val dbQuery: List<PerformanceStat.DbQuery> = emptyList(),

    @SerialName("crypto")
    val crypto: List<PerformanceStat.Crypto> = emptyList(),

    @SerialName("wallet-request")
    val walletRequest: List<PerformanceStat.WalletRequest> = emptyList(),

    @SerialName("wallet-task")
    val walletTask: List<PerformanceStat.WalletTask> = emptyList(),
)

@Serializable
data class TestingGetPerformanceStatsResponse(
    val stats: PerformanceTable,
)