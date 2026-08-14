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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build

/**
 * Bindet Aufrufer der lokalen Signal<->Taler-Schnittstelle an die Cert-Allowlist
 * (docs/API.md, Abschnitt 2.2). Signaturpruefung ersetzt hier bewusst, was auf
 * Android sonst ueber eine `signature`-Permission liefe - das geht nicht, weil
 * beide Apps absichtlich mit unterschiedlichen Keystores signiert sind.
 */
object CallerVerification {

    /**
     * Wirft [SecurityException], wenn der aktuelle Binder-Aufrufer nicht mit dem
     * erwarteten Fingerabdruck aus [AllowedCallers] uebereinstimmt. Gibt sonst den
     * Package-Namen des Aufrufers zurueck. Muss als erste Zeile jeder AIDL-Methode
     * aufgerufen werden - nicht nur einmalig bei onBind, da Binder.getCallingUid()
     * ohnehin je Transaktion verfuegbar ist.
     */
    fun assertCallerAllowed(context: Context): String {
        val callingUid = Binder.getCallingUid()
        val pm = context.packageManager
        val packages = (pm.getPackagesForUid(callingUid) ?: emptyArray()).toList()
        return resolveAllowedCaller(packages, AllowedCallers::expectedSha256) { pkg -> signingCertSha256(pm, pkg) }
            ?: throw SecurityException("Aufrufer (uid=$callingUid) ist nicht in der Allowlist")
    }

    /**
     * Reine Entscheidungslogik ohne Android-Framework-Abhaengigkeit (REVIEW.md
     * P3) - [expectedCertFor]/[actualCertFor] sind injiziert (statt fest
     * [AllowedCallers]/PackageManager zu befragen), damit der Shared-UID-Fall
     * (mehrere Packages fuer eine UID, siehe docs/API.md 2.2) mit
     * keinem/einem/mehreren Treffern durchtestbar ist, unabhaengig davon, wie
     * viele Eintraege die echte Allowlist gerade hat. Testbarkeit war hier der
     * einzige Grund fuer den Schnitt, kein neues Verhalten.
     */
    internal fun resolveAllowedCaller(
        packages: List<String>,
        expectedCertFor: (String) -> String?,
        actualCertFor: (String) -> String?,
    ): String? {
        for (pkg in packages) {
            val expected = expectedCertFor(pkg) ?: continue
            val actual = actualCertFor(pkg) ?: continue
            if (actual.equals(expected, ignoreCase = true)) return pkg
        }
        return null
    }

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
