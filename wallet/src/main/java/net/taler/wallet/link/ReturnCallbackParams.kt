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

package net.taler.wallet.link

import java.net.URLDecoder

/**
 * Liest correlationId/returnUri aus einer bereits vollstaendigen taler://-URI
 * (docs/API.md 2.10). Reine String-Parsing-Funktion (kein android.net.Uri -
 * das ist auf der JVM ohne Robolectric nicht nutzbar, dieses Repo hat kein
 * Robolectric als Testabhaengigkeit), damit sie mit plain JUnit testbar bleibt.
 */
object ReturnCallbackParams {

    fun extract(uri: String): Pair<String, String>? {
        val queryString = uri.substringAfter('?', missingDelimiterValue = "")
        if (queryString.isEmpty()) return null

        val params = queryString.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()

        val correlationId = params["correlationId"]?.takeIf { it.isNotBlank() }
        val returnUri = params["returnUri"]?.takeIf { it.isNotBlank() }
        if (correlationId == null || returnUri == null) return null
        return Pair(correlationId, returnUri)
    }

    fun stripQuery(uri: String): String = uri.substringBefore('?')
}

data class ReturnCallbackInfo(val correlationId: String, val returnUri: String)
