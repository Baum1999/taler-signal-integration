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
import org.junit.Test

class ReturnIntentSenderTest {

    @Test
    fun resolvesTargetWhenCandidateIsAllowlistedWithMatchingCert() {
        val result = ReturnIntentSender.resolveTarget(
            candidates = listOf("de.lenkenhoff.signalfuergnu" to "org.thoughtcrime.securesms.taler.TalerReturnActivity"),
            expectedCertFor = { "AABBCC" },
            actualCertFor = { "AABBCC" },
        )
        assertEquals("de.lenkenhoff.signalfuergnu" to "org.thoughtcrime.securesms.taler.TalerReturnActivity", result)
    }

    @Test
    fun returnsNullWhenNoCandidatesResolve() {
        val result = ReturnIntentSender.resolveTarget(
            candidates = emptyList(),
            expectedCertFor = { "AABBCC" },
            actualCertFor = { "AABBCC" },
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenResolvedPackageIsNotAllowlisted() {
        val result = ReturnIntentSender.resolveTarget(
            candidates = listOf("com.evil.app" to "com.evil.app.FakeReturnActivity"),
            expectedCertFor = { null },
            actualCertFor = { "IRRELEVANT" },
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenAllowlistedButCertMismatch() {
        val result = ReturnIntentSender.resolveTarget(
            candidates = listOf("de.lenkenhoff.signalfuergnu" to "org.thoughtcrime.securesms.taler.TalerReturnActivity"),
            expectedCertFor = { "AABBCC" },
            actualCertFor = { "DEADBEEF" },
        )
        assertNull(result)
    }

    @Test
    fun fallsThroughToSecondCandidateWhenFirstCertMismatches() {
        val result = ReturnIntentSender.resolveTarget(
            candidates = listOf(
                "com.evil.spoofed" to "com.evil.SpoofedActivity",
                "de.lenkenhoff.signalfuergnu" to "org.thoughtcrime.securesms.taler.TalerReturnActivity",
            ),
            expectedCertFor = { pkg -> if (pkg == "de.lenkenhoff.signalfuergnu") "AABBCC" else "SPOOFED_EXPECTED" },
            actualCertFor = { pkg -> if (pkg == "de.lenkenhoff.signalfuergnu") "AABBCC" else "WRONG" },
        )
        assertEquals("de.lenkenhoff.signalfuergnu" to "org.thoughtcrime.securesms.taler.TalerReturnActivity", result)
    }
}
