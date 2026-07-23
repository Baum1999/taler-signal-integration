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

package net.taler.wallet.payment

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.ui.theme.TalerTheme
import java.io.ByteArrayOutputStream

private const val NFC_READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_V

private fun formatTotpPayload(totpString: String): ByteArray {
    val codes = totpString.split("\n").mapNotNull { it.toUIntOrNull() }
    val out = ByteArrayOutputStream()
    out.write(0x42)
    for (code in codes) {
        val v = code.toInt()
        out.write(v shr 0 and 0xFF)
        out.write(v shr 8 and 0xFF)
        out.write(v shr 16 and 0xFF)
        out.write(v shr 24 and 0xFF)
    }
    return out.toByteArray()
}

@Composable
fun PayTotpComposable(
    totpString: String,
    enableNfc: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val nfcAdapter = remember(enableNfc) { if (enableNfc) NfcAdapter.getDefaultAdapter(context) else null }
    var nfcDone by remember { mutableStateOf(false) }

    if (enableNfc) {
        val ndefMessage = remember(totpString) {
            val payload = formatTotpPayload(totpString)
            val record = NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                "T".encodeToByteArray(),
                ByteArray(0),
                payload,
            )
            NdefMessage(arrayOf(record))
        }

        LaunchedEffect(activity, ndefMessage) {
            val act = activity ?: return@LaunchedEffect
            if (nfcAdapter == null) return@LaunchedEffect
            nfcAdapter.enableReaderMode(act, { tag ->
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    try {
                        ndef.connect()
                        ndef.writeNdefMessage(ndefMessage)
                        act.runOnUiThread {
                            nfcDone = true
                            Toast.makeText(context, R.string.nfc_write_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        act.runOnUiThread {
                            Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        act.runOnUiThread { nfcAdapter.disableReaderMode(act) }
                    }
                } else {
                    nfcAdapter.disableReaderMode(act)
                }
            }, NFC_READER_FLAGS, null)
        }

        DisposableEffect(activity) {
            onDispose {
                if (activity != null) {
                    nfcAdapter?.disableReaderMode(activity)
                }
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = CenterHorizontally) {
        Text(
            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
            text = stringResource(R.string.payment_confirmation_code),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            text = totpString,
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.titleLarge.fontSize,
        )

        if (enableNfc) {
            if (nfcAdapter == null) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.nfc_not_available),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (nfcDone) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.nfc_write_success),
                    color = TalerTheme.extraColors.success,
                )
            } else {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.nfc_scanning),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
