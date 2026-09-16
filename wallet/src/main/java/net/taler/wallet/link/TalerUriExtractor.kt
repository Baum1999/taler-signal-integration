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
 * Findet unterstuetzte Taler-URIs im manuell eingegebenen Text der
 * "Link eingeben"-Eingabe (EnterLinkTab in ScanQrScreen.kt), auch wenn dort
 * nicht nur die reine URI steht, sondern z.B. noch Begleittext davor
 * ("Zahlung: taler://...") oder DAHINTER (Signal-Fork-Transkript-Kopie:
 * "taler://...\n\n<Erklaerungstext>", siehe TalerReturnActivity.kt), oder ein
 * JSON-Wrapper ({"uri": "taler://..."} bzw. bei einem Gruppen-Split
 * {"uri": ["taler://...", "taler://..."]}).
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

    /** Erster Treffer aus [extractAll], oder null. */
    fun extract(input: String): String? = extractAll(input).firstOrNull()

    /**
     * Liefert ALLE im Text gefundenen URIs (dedupliziert, in Fundreihenfolge).
     *
     * Root-Cause-Fix (Bug: "Als Transkript kopieren" -> Einfuegen schlaegt
     * fehl): der fruehere Code behandelte "Text beginnt mit dem Schema" als
     * "Text IST komplett eine URI" und gab bei einem Transkript wie
     * "taler://...\n\n<Erklaerungstext>" den GESAMTEN mehrzeiligen String als
     * URI zurueck - der landete unveraendert bei wallet-core und scheiterte
     * dort an isFixedSizeCrock() statt am erwarteten uri_invalid-Fehler hier.
     * Eine URI enthaelt per RFC 3986 nie rohen Whitespace, das ist die
     * korrekte Grenze zwischen "Text IST eine URI" und "Text ENTHAELT eine".
     */
    fun extractAll(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Sonderfall "der ganze Text IST die URI": nur dann den getrimmten
        // Text unveraendert nehmen. Bleibt noetig (statt immer ueber
        // EMBEDDED_URI_REGEX zu gehen), weil der Regex Zeichen wie , ; ) ] }
        // " ' ausschliesst, die in einer allein stehenden URI durchaus
        // zulaessig vorkommen duerfen (z.B. ein payto-Verwendungszweck mit
        // Komma) - siehe singleUriContainingCommaIsNotTruncated-Test.
        if (startsWithSupportedScheme(trimmed) && trimmed.none(Char::isWhitespace)) {
            return listOf(trimmed)
        }

        collectUrisFromJson(trimmed).takeIf { it.isNotEmpty() }?.let { return it }

        return EMBEDDED_URI_REGEX.findAll(trimmed).map { it.value }.distinct().toList()
    }

    private fun startsWithSupportedScheme(value: String): Boolean =
        SUPPORTED_SCHEMES.any { value.startsWith(it, ignoreCase = true) }

    private fun collectUrisFromJson(value: String): List<String> {
        if (!value.startsWith("{") && !value.startsWith("[")) return emptyList()
        return try {
            val found = mutableListOf<String>()
            collectUrisFromJsonNode(JSONTokener(value).nextValue(), found)
            found.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectUrisFromJsonNode(node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONObject -> node.keys().forEach { key -> collectUrisFromJsonNode(node.get(key), out) }
            is JSONArray -> (0 until node.length()).forEach { i -> collectUrisFromJsonNode(node.get(i), out) }
            is String -> {
                val trimmedValue = node.trim()
                if (startsWithSupportedScheme(trimmedValue)) {
                    out.add(trimmedValue)
                } else {
                    EMBEDDED_URI_REGEX.find(trimmedValue)?.value?.let { out.add(it) }
                }
            }
        }
    }
}
