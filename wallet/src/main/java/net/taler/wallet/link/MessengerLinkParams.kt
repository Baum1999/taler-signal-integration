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

/**
 * Lese-Regeln fuer talerlink://-Links eines Messengers
 * (docs/cross-platform/link-protokoll.md). Reine String-Funktionen ohne
 * android.net.Uri, damit sie wie ReturnCallbackParams mit plain JUnit
 * testbar bleiben; MainActivity ruft sie beim Lesen des Intents auf.
 */
object MessengerLinkParams {

    private const val LINK_SCHEME = "talerlink"
    private val PEER_ACTION_HOSTS = setOf("pay-push", "pay-pull")

    /**
     * Aktionslink `talerlink://pay-push/...` bzw. `pay-pull/...`, wie ihn
     * Signal-iOS fuer Annehmen und Abbrechen sendet (link-protokoll.md 6,
     * android-nachzieh-liste.md N1): Scheme auf `taler` zuruecksetzen, Pfad
     * und Query (correlationId, returnUri) unveraendert lassen. Den Rest
     * erledigt der normale taler://-Weg (HandleUriScreen,
     * ReturnCallbackParams). null fuer jeden anderen Link.
     */
    fun peerActionTalerUri(link: String): String? {
        val separator = link.indexOf("://")
        if (separator < 0) return null
        if (!link.substring(0, separator).equals(LINK_SCHEME, ignoreCase = true)) return null
        val rest = link.substring(separator + 3)
        val host = rest.substringBefore('/').substringBefore('?')
        if (host !in PEER_ACTION_HOSTS || !rest.startsWith("$host/")) return null
        return "taler://$rest"
    }

    /**
     * `direction` von compose-send: nur PAY_PUSH oder PAY_PULL, alles andere
     * (auch PAY, WITHDRAW, REFUND) und ein fehlender Wert ergeben PAY_PUSH
     * (link-protokoll.md 2.3).
     */
    fun composeDirection(value: String?): String = when (value) {
        TalerUriKind.PAY_PULL.name -> TalerUriKind.PAY_PULL.name
        else -> TalerUriKind.PAY_PUSH.name
    }

    /** Pflichtparameter: ein leerer Wert zaehlt wie ein fehlender (link-protokoll.md 2.3). */
    fun required(value: String?): String? = value?.takeIf { it.isNotBlank() }
}
