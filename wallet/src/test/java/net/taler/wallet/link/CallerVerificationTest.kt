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

/**
 * Testet CallerVerification.resolveAllowedCaller() - die reine
 * Entscheidungslogik hinter assertCallerAllowed(), mit injizierten
 * Allowlist-/Signatur-Lookups statt der echten [AllowedCallers] (die aktuell
 * nur einen Eintrag hat, siehe REVIEW.md P3) und ohne PackageManager-Fake.
 */
class CallerVerificationTest {

    @Test
    fun noneOfSeveralPackagesInAllowlist() {
        val result = CallerVerification.resolveAllowedCaller(
            packages = listOf("com.example.a", "com.example.b"),
            expectedCertFor = { null },
            actualCertFor = { "IRRELEVANT" },
        )
        assertNull(result)
    }

    @Test
    fun oneOfSeveralPackagesInAllowlistWithMatchingCert() {
        val result = CallerVerification.resolveAllowedCaller(
            packages = listOf("com.example.unrelated", "com.example.allowed"),
            expectedCertFor = { pkg -> if (pkg == "com.example.allowed") "AABBCC" else null },
            actualCertFor = { pkg -> if (pkg == "com.example.allowed") "AABBCC" else "WRONG" },
        )
        assertEquals("com.example.allowed", result)
    }

    @Test
    fun packageInAllowlistButCertMismatchIsRejected() {
        // Der Package-Name ist in der Allowlist, aber die tatsaechliche Signatur
        // stimmt nicht - genau der Fall, den die Cert-Allowlist abfangen soll
        // (docs/API.md 2.2), z.B. eine gefaelschte App mit demselben Package-Namen.
        val result = CallerVerification.resolveAllowedCaller(
            packages = listOf("com.example.allowed"),
            expectedCertFor = { "AABBCC" },
            actualCertFor = { "DEADBEEF" },
        )
        assertNull(result)
    }

    @Test
    fun multiplePackagesInAllowlistFirstMatchWins() {
        // Shared-UID-Fall (docs/API.md 2.2): mehrere Packages fuer dieselbe UID,
        // mehrere davon in der Allowlist - der erste mit passender Signatur gewinnt.
        val result = CallerVerification.resolveAllowedCaller(
            packages = listOf("com.example.first", "com.example.second"),
            expectedCertFor = { pkg ->
                when (pkg) {
                    "com.example.first" -> "CERT1"
                    "com.example.second" -> "CERT2"
                    else -> null
                }
            },
            actualCertFor = { pkg ->
                when (pkg) {
                    "com.example.first" -> "CERT1"
                    "com.example.second" -> "CERT2"
                    else -> null
                }
            },
        )
        assertEquals("com.example.first", result)
    }

    @Test
    fun skipsMismatchedEntryAndFallsThroughToNextAllowedPackage() {
        // Erstes Package ist in der Allowlist, aber mit falscher Signatur -
        // die Schleife darf nicht dort abbrechen, sondern muss das zweite,
        // tatsaechlich passende Package noch pruefen.
        val result = CallerVerification.resolveAllowedCaller(
            packages = listOf("com.example.spoofed", "com.example.real"),
            expectedCertFor = { pkg ->
                when (pkg) {
                    "com.example.spoofed" -> "EXPECTED_SPOOFED"
                    "com.example.real" -> "EXPECTED_REAL"
                    else -> null
                }
            },
            actualCertFor = { pkg ->
                when (pkg) {
                    "com.example.spoofed" -> "WRONG_CERT"
                    "com.example.real" -> "EXPECTED_REAL"
                    else -> null
                }
            },
        )
        assertEquals("com.example.real", result)
    }

    @Test
    fun certComparisonIsCaseInsensitive() {
        val result = CallerVerification.resolveAllowedCaller(
            packages = listOf("com.example.allowed"),
            expectedCertFor = { "AABBCC" },
            actualCertFor = { "aabbcc" },
        )
        assertEquals("com.example.allowed", result)
    }

    @Test
    fun emptyPackageListReturnsNull() {
        val result = CallerVerification.resolveAllowedCaller(
            packages = emptyList(),
            expectedCertFor = { "AABBCC" },
            actualCertFor = { "AABBCC" },
        )
        assertNull(result)
    }
}
