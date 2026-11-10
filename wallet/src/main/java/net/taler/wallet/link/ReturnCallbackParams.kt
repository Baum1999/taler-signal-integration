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
        val params = queryParams(uri)
        val correlationId = params["correlationId"]?.takeIf { it.isNotBlank() }
        val returnUri = params["returnUri"]?.takeIf { it.isNotBlank() }
        if (correlationId == null || returnUri == null) return null
        return Pair(correlationId, returnUri)
    }

    /**
     * Anzeige-Kontext des Chats, aus dem der Messenger die Zahlung annehmen
     * laesst (Aktionslink, link-protokoll.md Abschnitt 6): dieselben
     * Hinweise wie bei compose-send. Ohne Chat-Namen null.
     */
    fun contact(uri: String): MessengerContact? {
        val params = queryParams(uri)
        val displayName = displayHint(params["recipientHint"]) ?: return null
        return MessengerContact(
            displayName = displayName,
            isGroup = params["isGroup"].equals("true", ignoreCase = true),
            senderAppName = displayHint(params["senderAppName"]),
            avatarUri = params["avatarUri"]?.takeIf { it.isNotBlank() },
            uriHash = OwnUriTracker.keyFor(stripQuery(uri)),
        )
    }

    fun stripQuery(uri: String): String = uri.substringBefore('?')

    private fun queryParams(uri: String): Map<String, String> {
        val queryString = uri.substringAfter('?', missingDelimiterValue = "")
        if (queryString.isEmpty()) return emptyMap()

        // Bei doppeltem Parameter gewinnt der erste, wie bei
        // Uri.getQueryParameter an allen anderen Lesestellen
        // (link-protokoll.md 2.3).
        val params = mutableMapOf<String, String>()
        queryString.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@forEach
            val key = pair.substring(0, idx)
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            params.putIfAbsent(key, value)
        }
        return params
    }

    // Gleiche Bereinigung wie fuer die compose-send-Hinweise (ComposeSendScreen):
    // Steuerzeichen raus, leer -> null.
    private fun displayHint(value: String?): String? =
        value?.filter { it.isDefined() && !it.isISOControl() }?.takeIf { it.isNotBlank() }
}

/**
 * Chat, aus dem eine eingehende Zahlung angenommen wurde. Name, Gruppe,
 * App-Name und Avatar sind wie bei compose-send reine, angreifer-
 * kontrollierte Anzeige-Hinweise (link-protokoll.md 2.6). [uriHash] ist der
 * SHA-256 der angenommenen URI ohne Query: ueber ihn findet der Messenger
 * die Nachricht spaeter wieder (link-protokoll.md Abschnitt 12).
 */
data class MessengerContact(
    val displayName: String,
    val isGroup: Boolean,
    val senderAppName: String?,
    val avatarUri: String?,
    val uriHash: String,
)

data class ReturnCallbackInfo(
    val correlationId: String,
    val returnUri: String,
    /** Chat des Annehmen-Links, fuer die Kontaktzeile der angenommenen Zahlung ([MessengerContact]). */
    val contact: MessengerContact? = null,
)
