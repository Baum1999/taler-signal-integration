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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URISyntaxException

enum class ReturnStatus(val wireValue: String) {
    READY("ready"),
    CANCELLED("cancelled"),
}

/**
 * Feuert den Ruecksprung-Intent nach docs/API.md 2.10 - niemals implizit
 * (dasselbe Prinzip wie 2.1): das Scheme des `returnUri` waehlt aus der
 * Allowlist (AllowedCallers) genau ein Package, dessen installiertes
 * Signing-Cert wird geprueft, erst danach geht ein EXPLIZITER Intent (per
 * setPackage, ohne Activity-Klasse) an genau dieses Package.
 */
object ReturnIntentSender {

    const val RETURN_HOST = "taler-return"

    fun fire(
        context: Context,
        returnUri: String,
        correlationId: String,
        status: ReturnStatus,
        talerUri: String? = null,
        talerPaymentData: TalerPaymentData? = null,
    ): Boolean {
        // returnUri kommt aus einem taler://pay-push/...-URI, der NICHT
        // zwingend vom eigenen Annehmen-Button des Messengers stammt
        // (QR-Code, Web-Link, fremde Chat-Nachricht) - der Angreifer
        // kontrolliert also den Inhalt. Ohne strenge Pruefung koennte
        // returnUri auf einen ANDEREN exportierten Deep-Link des Messengers
        // zeigen (z.B. sgnl://linkdevice) - Taler wuerde zum Redirect-Gadget.
        // Deshalb nur Scheme aus der Tabelle, Host taler-return, sonst nichts.
        val target = verifiedTarget(context, returnUri) ?: return false

        // Fix (Final-Review I1): angreifer-gewaehlte Query-Parameter (und
        // Fragment) duerfen die von Taler gesetzten Werte nicht verschatten.
        // Darum wird die Ziel-URI komplett neu gebaut statt uebernommen:
        // <scheme>://taler-return?<neue Query>.
        val targetUri = Uri.Builder()
            .scheme(target.scheme)
            .authority(RETURN_HOST)
            .appendQueryParameter("correlationId", correlationId)
            .appendQueryParameter("status", status.wireValue)
            .apply { talerUri?.let { appendQueryParameter("talerUri", it) } }
            .apply { talerPaymentData?.let { appendQueryParameter("talerPaymentData", Json.encodeToString(it)) } }
            .build()
        val explicitIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            setPackage(target.packageName)
        }
        return try {
            context.startActivity(explicitIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Ob ein Ruecksprung an `returnUri` moeglich ist: Ziel steht in der
     * Allowlist, ist installiert und mit dem erwarteten Cert signiert. Die
     * Compose-Screens fragen das schon beim Oeffnen ab, damit eine Zahlung
     * nicht erst erstellt und dann beim Ruecksprung stillschweigend verloren
     * geht (dann Fallback: URI in die Zwischenablage).
     */
    fun canReturn(context: Context, returnUri: String): Boolean =
        verifiedTarget(context, returnUri) != null

    private fun verifiedTarget(context: Context, returnUri: String): ReturnTarget? =
        verifiedTarget(returnUri) { packageName ->
            try {
                CallerVerification.signingCertSha256(context.packageManager, packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

    /** [certOf] liefert das SHA-256 des installierten Packages, null wenn nicht installiert. */
    internal fun verifiedTarget(returnUri: String, certOf: (packageName: String) -> String?): ReturnTarget? {
        val target = targetFor(returnUri) ?: return null
        val actualCert = certOf(target.packageName) ?: return null
        return target.takeIf { certMatches(it, actualCert) }
    }

    /**
     * Reine Pruefung des empfangenen `returnUri`, ohne Android-Klassen, damit
     * sie ohne Robolectric testbar bleibt. Erlaubt ist nur
     * `<scheme aus der Tabelle>://taler-return` ohne User, Port und Pfad; die
     * Query wird spaeter ohnehin verworfen.
     */
    internal fun targetFor(returnUri: String): ReturnTarget? {
        val parsed = try {
            URI(returnUri)
        } catch (e: URISyntaxException) {
            return null
        }
        if (parsed.isOpaque) return null
        val target = AllowedCallers.targetForScheme(parsed.scheme) ?: return null
        if (parsed.rawAuthority != RETURN_HOST || parsed.host != RETURN_HOST) return null
        if (parsed.rawUserInfo != null || parsed.port != -1) return null
        if (!parsed.rawPath.isNullOrEmpty()) return null
        return target
    }

    internal fun certMatches(target: ReturnTarget, actualCertSha256: String): Boolean =
        target.certSha256.any { it.equals(actualCertSha256, ignoreCase = true) }
}
