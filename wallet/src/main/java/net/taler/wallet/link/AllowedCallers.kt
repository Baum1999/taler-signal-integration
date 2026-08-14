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
 * Hartcodierte Allowlist der Apps, die die lokale Schnittstelle nutzen duerfen.
 * Fingerabdruck berechnet aus keys/signal-fuer-gnu.p12 (siehe docs/API.md,
 * Abschnitt 2.2: `keytool -list -v -alias signalfuergnu`, Feld SHA256).
 */
object AllowedCallers {
    private val entries = mapOf(
        "de.lenkenhoff.signalfuergnu" to
            "90567A0AB652F227D22898EE45423413D36F3699DF6D81712C1642F780892415"
    )

    fun expectedSha256(pkg: String): String? = entries[pkg]
}
