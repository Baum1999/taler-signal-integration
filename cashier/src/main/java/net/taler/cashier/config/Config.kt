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

import kotlinx.serialization.Serializable
import net.taler.common.Challenge
import net.taler.common.Timestamp
import okhttp3.Credentials

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

sealed class ConfigResult {
    data class Error(val authError: Boolean, val msg: String) : ConfigResult()
    data object Offline : ConfigResult()
    data object Success : ConfigResult()
    data object Unknown : ConfigResult()
    data class TanRequired(val challenges: List<Challenge>, val combiAnd: Boolean): ConfigResult()
}
