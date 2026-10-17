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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnIntentSenderTest {

    private val target = AllowedCallers.targets.first()
    private val scheme = target.scheme
    private val cert = target.certSha256.first()

    @Test
    fun everyAllowedSchemeMapsToItsPackage() {
        for (t in AllowedCallers.targets) {
            assertEquals(t.packageName, ReturnIntentSender.targetFor("${t.scheme}://taler-return")?.packageName)
        }
    }

    @Test
    fun queryIsAcceptedBecauseItIsRebuiltAnyway() {
        assertEquals(
            target.packageName,
            ReturnIntentSender.targetFor("$scheme://taler-return?status=ready&correlationId=evil")?.packageName,
        )
    }

    @Test
    fun rejectsUnknownScheme() {
        assertNull(ReturnIntentSender.targetFor("evil://taler-return"))
        assertNull(ReturnIntentSender.targetFor("taler://taler-return"))
    }

    @Test
    fun rejectsOtherHostInAllowedScheme() {
        assertNull(ReturnIntentSender.targetFor("$scheme://check-for-update"))
        assertNull(ReturnIntentSender.targetFor("$scheme://linkdevice"))
    }

    @Test
    fun rejectsPathUserAndPort() {
        assertNull(ReturnIntentSender.targetFor("$scheme://taler-return/x"))
        assertNull(ReturnIntentSender.targetFor("$scheme://taler-return/"))
        assertNull(ReturnIntentSender.targetFor("$scheme://u@taler-return"))
        assertNull(ReturnIntentSender.targetFor("$scheme://taler-return:1"))
    }

    @Test
    fun rejectsOpaqueAndGarbage() {
        assertNull(ReturnIntentSender.targetFor("$scheme:taler-return"))
        assertNull(ReturnIntentSender.targetFor("not a uri"))
        assertNull(ReturnIntentSender.targetFor(""))
    }

    @Test
    fun certMustMatchTheSchemesOwnEntry() {
        assertTrue(ReturnIntentSender.certMatches(target, cert.lowercase()))
        assertFalse(ReturnIntentSender.certMatches(target, "DEADBEEF"))
        // No cross jumps: a link for one target never verifies with another target's certificate
        for (other in AllowedCallers.targets - target) {
            for (otherCert in other.certSha256 - target.certSha256) {
                assertFalse(ReturnIntentSender.certMatches(target, otherCert))
            }
        }
    }

    @Test
    fun verifiedTargetNeedsInstalledPackageWithMatchingCert() {
        val verified = ReturnIntentSender.verifiedTarget("$scheme://taler-return") { pkg ->
            cert.takeIf { pkg == target.packageName }
        }
        assertEquals(target.packageName, verified?.packageName)
    }

    @Test
    fun verifiedTargetIsNullForUnknownScheme() {
        assertNull(ReturnIntentSender.verifiedTarget("evil://taler-return") { cert })
    }

    @Test
    fun verifiedTargetIsNullWhenTargetNotInstalled() {
        assertNull(ReturnIntentSender.verifiedTarget("$scheme://taler-return") { null })
    }

    @Test
    fun verifiedTargetIsNullForForeignCert() {
        assertNull(ReturnIntentSender.verifiedTarget("$scheme://taler-return") { "DEADBEEF" })
    }
}
