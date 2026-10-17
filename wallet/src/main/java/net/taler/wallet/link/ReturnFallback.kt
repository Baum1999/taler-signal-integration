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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.flow.Flow
import net.taler.wallet.R

/**
 * Fallback, wenn der Ruecksprung an den aufrufenden Messenger nicht moeglich
 * ist (ReturnIntentSender.canReturn == false, z.B. Scheme nicht in der
 * Allowlist einer aelteren Wallet): Die Zahlung wird trotzdem erstellt, ihre
 * URIs landen aber in der Zwischenablage statt per Ruecksprung im Chat.
 */
object ReturnFallback {

    /** Gleiches Format wie der Text, den die Messenger selbst posten: URIs durch Leerzeile getrennt. */
    fun clipboardText(uris: List<String>): String = uris.joinToString("\n\n")

    fun copyToClipboard(context: Context, uris: List<String>) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("taler", clipboardText(uris)))
        Toast.makeText(context, R.string.compose_return_copied, Toast.LENGTH_LONG).show()
    }
}

enum class UpdateCheckState { SEARCHING, UPDATE_FOUND, NO_UPDATE, FAILED }

/**
 * Sofortige Update-Pruefung bei unbekanntem Ruecksprungziel - oft ist die
 * Wallet einfach aelter als der Messenger. gnu-fork hat keinen eigenen
 * Updater, deshalb bleibt der Haken hier leer (keine Statuszeile im UI);
 * ein Build mit Updater (Branch downstream) belegt ihn beim App-Start.
 */
object UpdateCheckHook {
    @Volatile
    var checkNow: ((Context) -> Flow<UpdateCheckState>)? = null
}
