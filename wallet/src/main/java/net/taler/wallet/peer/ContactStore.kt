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

package net.taler.wallet.peer

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ContactInfo(
    val displayName: String,
    val isGroup: Boolean,
    val senderAppName: String?,
    val avatarFile: File?,
)

/**
 * Merkt sich dauerhaft, an welchen Messenger-Kontakt (Signal) eine
 * Transaktion gegangen ist, damit die Transaktionsliste und die
 * Detailansicht "Signal-Logo + Avatar + Name" zeigen koennen. Reine lokale
 * UI-Zusatzinfo wie [GroupShareStore], gleiches SharedPreferences-Muster.
 *
 * Der Avatar kommt als Content-URI mit nur voruebergehender Leseberechtigung
 * (FLAG_GRANT_READ_URI_PERMISSION, siehe MainActivity.composeSendFrom) und
 * waere nach einem Neustart nicht mehr lesbar. Deshalb wird das Bild beim
 * Speichern einmal verkleinert in den App-Speicher kopiert. Name und Bild
 * sind angreifer-kontrolliert und dienen nur der Anzeige.
 */
class ContactStore(
    private val prefs: SharedPreferences,
    private val avatarDir: File,
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        File(context.applicationContext.filesDir, "contact_avatars"),
    )

    suspend fun save(
        context: Context,
        transactionId: String,
        displayName: String?,
        isGroup: Boolean,
        senderAppName: String?,
        avatarUri: String?,
    ) {
        if (displayName.isNullOrBlank()) return
        val copied = avatarUri?.let { copyAvatar(context, transactionId, it) } ?: false
        prefs.edit()
            .putString(nameKey(transactionId), displayName)
            .putBoolean(groupKey(transactionId), isGroup)
            .putString(appKey(transactionId), senderAppName)
            .putBoolean(avatarKey(transactionId), copied)
            .apply()
    }

    fun get(transactionId: String): ContactInfo? {
        val name = prefs.getString(nameKey(transactionId), null) ?: return null
        val avatar = avatarFile(transactionId).takeIf {
            prefs.getBoolean(avatarKey(transactionId), false) && it.exists()
        }
        return ContactInfo(
            displayName = name,
            isGroup = prefs.getBoolean(groupKey(transactionId), false),
            senderAppName = prefs.getString(appKey(transactionId), null),
            avatarFile = avatar,
        )
    }

    private suspend fun copyAvatar(context: Context, transactionId: String, uriString: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= AVATAR_PX && bounds.outHeight / (sample * 2) >= AVATAR_PX) {
                    sample *= 2
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching false
                val scaled = Bitmap.createScaledBitmap(decoded, AVATAR_PX, AVATAR_PX, true)
                avatarDir.mkdirs()
                avatarFile(transactionId).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                true
            }.getOrDefault(false)
        }

    private fun avatarFile(transactionId: String) =
        File(avatarDir, transactionId.replace(Regex("[^A-Za-z0-9]"), "_") + ".jpg")

    private fun nameKey(transactionId: String) = "name_$transactionId"
    private fun groupKey(transactionId: String) = "group_$transactionId"
    private fun appKey(transactionId: String) = "app_$transactionId"
    private fun avatarKey(transactionId: String) = "avatar_$transactionId"

    companion object {
        private const val PREFS_NAME = "taler_peer_contacts"
        private const val AVATAR_PX = 96
    }
}
