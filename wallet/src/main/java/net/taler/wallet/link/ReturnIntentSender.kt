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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

enum class ReturnStatus(val wireValue: String) {
    READY("ready"),
    CANCELLED("cancelled"),
}

/**
 * Feuert den Ruecksprung-Intent nach docs/API.md 2.10 - niemals implizit
 * (dasselbe Prinzip wie 2.1): erst wird per PackageManager aufgeloest, wer
 * `returnUri` tatsaechlich registriert hat, dann dessen Signing-Cert gegen
 * dieselbe Allowlist geprueft, die schon fuer den AIDL-Bind existiert
 * (CallerVerification/AllowedCallers) - erst danach ein EXPLIZITER Intent an
 * genau dieses verifizierte Package.
 */
object ReturnIntentSender {

    fun fire(context: Context, returnUri: String, correlationId: String, status: ReturnStatus, talerUri: String? = null): Boolean {
        // returnUri kommt aus einem taler://pay-push/...-URI, der NICHT
        // zwingend von Signals eigenem Annehmen-Button stammt (QR-Code,
        // Web-Link, fremde Chat-Nachricht) - der Angreifer kontrolliert also
        // den Inhalt. Die Allowlist unten prueft nur, AN WEN gefeuert wird
        // (Signing-Cert des aufgeloesten Packages), nicht WAS gefeuert wird.
        // Ohne diese Pruefung koennte returnUri auf einen ANDEREN, ebenfalls
        // exportierten und allowlisted Signal-Deep-Link zeigen (z.B.
        // sgnl://linkdevice) mit angreifer-gewaehlten Query-Parametern -
        // Taler wuerde so zum ungewollten Redirect-Gadget fuer beliebige
        // Signal-Deep-Links, ausgeloest allein dadurch, dass der Nutzer die
        // Taler-Bestaetigungsseite verlaesst. Deshalb Schema+Host laut
        // docs/API.md 2.10 verifizieren, BEVOR ueberhaupt aufgeloest wird.
        val parsed = Uri.parse(returnUri)
        if (parsed.scheme != "signalfuergnu" || parsed.host != "taler-return") return false

        val pm = context.packageManager
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(returnUri))
        val candidates = pm.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { candidate ->
                val packageName = candidate.activityInfo?.packageName ?: return@mapNotNull null
                val className = candidate.activityInfo?.name ?: return@mapNotNull null
                packageName to className
            }

        val target = resolveTarget(
            candidates = candidates,
            expectedCertFor = { pkg -> AllowedCallers.expectedSha256(pkg) },
            actualCertFor = { pkg -> CallerVerification.signingCertSha256(pm, pkg) },
        ) ?: return false

        val (packageName, className) = target
        // Fix (Final-Review I1): returnUri ist laut Kommentar oben angreifer-
        // kontrolliert (kann aus einem taler://pay-push/...-URI aus nicht
        // vertrauenswuerdiger Quelle stammen) und koennte bereits eigene Query-
        // Parameter mit denselben Namen (correlationId/status/talerUri) tragen.
        // Uri.getQueryParameter liefert bei doppelten Keys den ERSTEN Treffer -
        // ein vorab eingeschleuster Parameter wuerde also die unten von Taler
        // angehaengten, legitimen Werte auf Empfaengerseite verschatten.
        // clearQuery() entfernt nur die Query-Komponente, Schema/Host/Pfad
        // (bereits oben verifiziert) bleiben unveraendert.
        val targetUri = Uri.parse(returnUri).buildUpon()
            .clearQuery()
            .appendQueryParameter("correlationId", correlationId)
            .appendQueryParameter("status", status.wireValue)
            .apply { talerUri?.let { appendQueryParameter("talerUri", it) } }
            .build()
        val explicitIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            setClassName(packageName, className)
            setPackage(packageName)
        }
        context.startActivity(explicitIntent)
        return true
    }

    /**
     * Reine Entscheidungslogik, ohne PackageManager/Context - analog zu
     * CallerVerification.resolveAllowedCaller(), damit sie ohne Mocking-
     * Framework testbar bleibt (dieses Repo hat keines als Testabhaengigkeit,
     * siehe CallerVerificationTest.kt fuers etablierte Muster). Gibt
     * (packageName, className) des ersten Kandidaten zurueck, dessen
     * Signing-Cert mit der Allowlist uebereinstimmt, oder null.
     */
    internal fun resolveTarget(
        candidates: List<Pair<String, String>>,
        expectedCertFor: (String) -> String?,
        actualCertFor: (String) -> String?,
    ): Pair<String, String>? {
        for ((packageName, className) in candidates) {
            val expected = expectedCertFor(packageName) ?: continue
            val actual = actualCertFor(packageName) ?: continue
            if (actual.equals(expected, ignoreCase = true)) return packageName to className
        }
        return null
    }
}
