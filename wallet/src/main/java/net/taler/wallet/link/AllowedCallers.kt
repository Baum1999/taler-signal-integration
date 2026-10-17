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

/** An allowed return target: exactly one package and its release certificates. */
data class ReturnTarget(
    val scheme: String,
    val packageName: String,
    val certSha256: Set<String>,
)

/**
 * Hard-coded allowlist of return targets, scheme -> package + certificates.
 * The scheme of the received `returnUri` alone decides the target, so a link
 * for one messenger can never land in another one.
 *
 * Fingerprints are SHA-256 without colons. The default entry is the official
 * Signal Android app (package and certificates as published in
 * https://signal.me/.well-known/assetlinks.json). Builds that pair the wallet
 * with other messengers replace this table and the matching `<queries>`
 * entries in AndroidManifest.xml.
 */
object AllowedCallers {
    private val byScheme = listOf(
        ReturnTarget(
            scheme = "sgnl",
            packageName = "org.thoughtcrime.securesms",
            certSha256 = setOf(
                "29F34E5F27F211B424BC5BF9D67162C0EAFBA2DA35AF35C16416FC446276BA26",
                "4BE4F6CD5BE844083E900279DC822AF65A547FECC26ABA7FF1F5203A45518CD8",
            ),
        ),
    ).associateBy { it.scheme }

    val targets: Collection<ReturnTarget> get() = byScheme.values

    fun targetForScheme(scheme: String?): ReturnTarget? = scheme?.let { byScheme[it] }
}
