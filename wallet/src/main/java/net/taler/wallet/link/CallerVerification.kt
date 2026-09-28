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

import android.content.pm.PackageManager
import android.os.Build

/**
 * Signaturpruefung fremder Apps gegen die Cert-Allowlist (docs/API.md,
 * Abschnitt 2.2). Ersetzt bewusst, was auf Android sonst ueber eine
 * `signature`-Permission liefe - das geht nicht, weil beide Apps absichtlich
 * mit unterschiedlichen Keystores signiert sind.
 *
 * Frueher auch Zugangskontrolle fuer eingehende Binder-Aufrufe; seit dem
 * Wegfall der App-zu-App-Schnittstelle nur noch fuer die Pruefung des
 * Ruecksprung-Ziels (ReturnIntentSender).
 */
object CallerVerification {

    /** SHA-256-Fingerabdruck (Hex, Grossbuchstaben, ohne Trenner) des Signing-Certs. */
    fun signingCertSha256(pm: PackageManager, packageName: String): String? {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                // Signature-Rotation lehnen wir bewusst ab: die Allowlist kennt
                // genau einen erwarteten Fingerabdruck pro Package.
                return null
            }
            signingInfo.apkContentsSigners?.firstOrNull() ?: return null
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull() ?: return null
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }
}
