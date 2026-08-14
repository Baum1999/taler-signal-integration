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

data class ConsentEntry(
    val packageName: String,
    val certSha256: String,
    val grantedAtMillis: Long,
)

/**
 * Speichert, welchen (per Cert-Fingerabdruck verifizierten) Aufrufern der
 * Nutzer die Verbindung erlaubt hat. Bewusst eine eigene, kleine
 * SharedPreferences-Datei statt der bestehenden Proto-DataStore
 * (UserPreferences) der App - hier geht es um Autorisierung einzelner
 * Fremd-Apps, nicht um Nutzereinstellungen, und ein neues Proto-Feld haette
 * einen Eingriff in Talers bestehendes Schema bedeutet.
 */
class ConsentStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGranted(certSha256: String): Boolean =
        prefs.contains(keyFor(certSha256))

    fun grant(packageName: String, certSha256: String) {
        prefs.edit()
            .putString(keyFor(certSha256), "$packageName|${System.currentTimeMillis()}")
            .apply()
    }

    fun revoke(certSha256: String) {
        prefs.edit().remove(keyFor(certSha256)).apply()
    }

    fun listEntries(): List<ConsentEntry> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(KEY_PREFIX)) return@mapNotNull null
        val parts = (value as? String)?.split("|", limit = 2) ?: return@mapNotNull null
        if (parts.size != 2) return@mapNotNull null
        ConsentEntry(
            packageName = parts[0],
            certSha256 = key.removePrefix(KEY_PREFIX),
            grantedAtMillis = parts[1].toLongOrNull() ?: 0L,
        )
    }

    private fun keyFor(certSha256: String) = "$KEY_PREFIX$certSha256"

    companion object {
        private const val PREFS_NAME = "taler_link_consent"
        private const val KEY_PREFIX = "cert_"
    }
}
