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

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Billig, aber faengt genau den Tippfehler ab, der die Kopplung
 * stillschweigend kaputtmachen wuerde (REVIEW.md P3): ein gepinnter
 * SHA-256-Fingerabdruck, der nicht aus exakt 64 Hex-Zeichen besteht, kann nie
 * mit einem echten `signingCertSha256()`-Ergebnis uebereinstimmen - die
 * Kopplung wuerde fuer JEDEN Aufrufer stillschweigend fehlschlagen, nicht nur
 * fuer einen falsch getippten Fingerabdruck.
 */
class AllowedCallersTest {

    private val hex64 = Regex("^[0-9A-Fa-f]{64}$")

    @Test
    fun pinnedHashesAreSixtyFourHexChars() {
        val entries = listOf(
            "de.lenkenhoff.signalfuergnu" to AllowedCallers.expectedSha256("de.lenkenhoff.signalfuergnu"),
        )
        for ((pkg, hash) in entries) {
            assertTrue("Fingerabdruck fuer $pkg fehlt", hash != null)
            assertTrue("Fingerabdruck fuer $pkg ist keine 64-stellige Hex-Zahl: $hash", hex64.matches(hash!!))
        }
    }

    @Test
    fun unknownPackageHasNoEntry() {
        assertNull(AllowedCallers.expectedSha256("com.example.not.in.allowlist"))
    }
}
