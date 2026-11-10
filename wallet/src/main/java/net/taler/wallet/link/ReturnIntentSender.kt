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
    const val URI_HASH_PARAM = "uriHash"
    const val MAX_URI_HASHES = 32

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
     * Ruecksprung fuer compose-abort (link-protokoll.md Abschnitt 9): kein
     * `status`, dafuer je Eingangs-URI ein `result=<uri>|<ergebnis>` in
     * Eingangs-Reihenfolge. Gleiche Zielpruefung und neu gebaute Query wie
     * [fire]; appendQueryParameter kodiert auch das `|` als %7C.
     */
    fun fireAbort(
        context: Context,
        returnUri: String,
        correlationId: String,
        results: List<Pair<String, AbortOutcome>>,
    ): Boolean {
        val target = verifiedTarget(context, returnUri) ?: return false
        val targetUri = Uri.Builder()
            .scheme(target.scheme)
            .authority(RETURN_HOST)
            .appendQueryParameter("correlationId", correlationId)
            .apply {
                results.forEach { (uri, outcome) ->
                    appendQueryParameter("result", ComposeAbortRequest.resultValue(uri, outcome))
                }
            }
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
     * Oeffnet im Messenger den Chat mit der Nachricht zu einer Zahlung
     * (link-protokoll.md Abschnitt 12):
     * `<scheme>://taler-return?uriHash=<hex>&uriHash=<hex>...`, ohne
     * correlationId. [uriHashes] sind die SHA-256-Schluessel aus dem
     * [OwnUriTracker], bei einem Gruppen-Split einer pro Anteil. Den Hash
     * gibt es auch noch, wenn wallet-core die URI nach dem Annehmen nicht
     * mehr liefert, und anders als die URI ist er kein Inhaberpapier. Der
     * Messenger fragt den Status der Zahlung dabei sofort neu ab. Gleiche
     * Zielpruefung wie [fire]. Ist [returnUri] nicht bekannt (Transaktion aus
     * einer aelteren Version), wird der erste installierte und korrekt
     * signierte Messenger aus [AllowedCallers] genommen.
     */
    fun openInMessenger(context: Context, returnUri: String?, uriHashes: List<String>): Boolean {
        if (uriHashes.isEmpty()) return false
        val target = openTarget(context, returnUri) ?: return false
        val explicitIntent = Intent(Intent.ACTION_VIEW, openUri(target.scheme, uriHashes)).apply {
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
     * Package des Messengers, an den [returnUri] zurueckspringt: nur wenn er in
     * [AllowedCallers] steht, installiert und mit dem erwarteten Cert signiert
     * ist. Anders als [openInMessenger] ohne Ersatz-Messenger, denn es dient der
     * Anzeige (Logo, Name) genau dieses Messengers.
     */
    fun verifiedMessengerPackage(context: Context, returnUri: String?): String? =
        returnUri?.let { verifiedTarget(context, it)?.packageName }

    /** Ob [openInMessenger] ein gepruefter Messenger zur Verfuegung steht. */
    fun canOpenInMessenger(context: Context, returnUri: String?): Boolean =
        openTarget(context, returnUri) != null

    private fun openTarget(context: Context, returnUri: String?): ReturnTarget? {
        val candidates = listOfNotNull(returnUri) + AllowedCallers.targets.map { "${it.scheme}://$RETURN_HOST" }
        return candidates.firstNotNullOfOrNull { verifiedTarget(context, it) }
    }

    internal fun openUri(scheme: String, uriHashes: List<String>): Uri = Uri.Builder()
        .scheme(scheme)
        .authority(RETURN_HOST)
        .apply { uriHashes.take(MAX_URI_HASHES).forEach { appendQueryParameter(URI_HASH_PARAM, it) } }
        .build()

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
