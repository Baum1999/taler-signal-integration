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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cheap, but catches exactly the typo that would silently break the pairing:
 * a pinned SHA-256 fingerprint that is not exactly 64 hex characters can never
 * match a real `signingCertSha256()` result.
 */
class AllowedCallersTest {

    private val hex64 = Regex("^[0-9A-Fa-f]{64}$")

    @Test
    fun pinnedHashesAreSixtyFourHexChars() {
        assertTrue(AllowedCallers.targets.isNotEmpty())
        for (target in AllowedCallers.targets) {
            assertTrue("No fingerprint for ${target.packageName}", target.certSha256.isNotEmpty())
            for (hash in target.certSha256) {
                assertTrue("Fingerprint for ${target.packageName} is not 64 hex characters: $hash", hex64.matches(hash))
            }
        }
    }

    @Test
    fun everySchemeMapsToItsOwnEntry() {
        for (target in AllowedCallers.targets) {
            assertEquals(target, AllowedCallers.targetForScheme(target.scheme))
        }
        assertEquals(AllowedCallers.targets.size, AllowedCallers.targets.map { it.scheme }.toSet().size)
    }

    @Test
    fun unknownSchemeHasNoEntry() {
        assertNull(AllowedCallers.targetForScheme("com.example.not.in.allowlist"))
        assertNull(AllowedCallers.targetForScheme(null))
    }
}
