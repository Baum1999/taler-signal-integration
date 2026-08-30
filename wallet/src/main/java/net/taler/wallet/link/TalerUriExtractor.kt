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

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Findet eine unterstuetzte Taler-URI im manuell eingegebenen Text der
 * "Link eingeben"-Eingabe (EnterLinkTab in ScanQrScreen.kt), auch wenn dort
 * nicht nur die reine URI steht, sondern z.B. noch Begleittext davor
 * ("Zahlung: taler://...") oder ein JSON-Wrapper ({"uri": "taler://..."}),
 * wie er beim Kopieren aus manchen Quellen mitkommt.
 *
 * Bewusst nicht abgedeckt (siehe Ruecksprache): ext+taler://-Schema und
 * einzelne Anfuehrungszeichen um eine sonst blanke URI.
 */
object TalerUriExtractor {

    private val SUPPORTED_SCHEMES = listOf(
        "taler://", "taler+http://", "payto://",
    )

    // Schliesst typische Begleitzeichen aus (schliessende Klammern/Anfuehrungszeichen,
    // Satzzeichen), damit z.B. aus "{uri: taler://pay/X}" nicht "taler://pay/X}" wird.
    private val EMBEDDED_URI_REGEX = Regex(
        """(?:taler(?:\+http)?|payto)://[^\s"')\]}<>,;]+""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (startsWithSupportedScheme(trimmed)) return trimmed

        extractFromJson(trimmed)?.let { return it }

        return EMBEDDED_URI_REGEX.find(trimmed)?.value
    }

    private fun startsWithSupportedScheme(value: String): Boolean =
        SUPPORTED_SCHEMES.any { value.startsWith(it, ignoreCase = true) }

    private fun extractFromJson(value: String): String? {
        if (!value.startsWith("{") && !value.startsWith("[")) return null
        return try {
            findUriInJson(JSONTokener(value).nextValue())
        } catch (_: Exception) {
            null
        }
    }

    private fun findUriInJson(node: Any?): String? = when (node) {
        is JSONObject -> node.keys().asSequence().firstNotNullOfOrNull { key ->
            findUriInJson(node.get(key))
        }
        is JSONArray -> (0 until node.length()).asSequence().firstNotNullOfOrNull { i ->
            findUriInJson(node.get(i))
        }
        is String -> {
            val trimmedValue = node.trim()
            if (startsWithSupportedScheme(trimmedValue)) {
                trimmedValue
            } else {
                EMBEDDED_URI_REGEX.find(trimmedValue)?.value
            }
        }
        else -> null
    }
}
