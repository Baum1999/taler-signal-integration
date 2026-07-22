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
import android.nfc.tech.NfcA
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
fun TotpNfcWriter(
    totpString: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isWriting by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        onDispose {
            if (isWriting && activity != null) {
                NfcAdapter.getDefaultAdapter(context)?.disableReaderMode(activity)
            }
        }
    }

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

        Button(
            onClick = {
                val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                val act = activity
                if (nfcAdapter == null || act == null) {
                    Toast.makeText(context, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isWriting = true
                nfcAdapter.enableReaderMode(act, { tag ->
                    val ndef = Ndef.get(tag)
                    if (ndef != null) {
                        try {
                            ndef.connect()
                            ndef.writeNdefMessage(ndefMessage)
                            act.runOnUiThread {
                                Toast.makeText(context, R.string.nfc_write_success, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            act.runOnUiThread {
                                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            act.runOnUiThread {
                                isWriting = false
                                nfcAdapter.disableReaderMode(act)
                            }
                        }
                    } else {
                        act.runOnUiThread {
                            isWriting = false
                            nfcAdapter.disableReaderMode(act)
                            Toast.makeText(context, R.string.nfc_tag_not_ndef, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, NFC_READER_FLAGS, null)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 4.dp),
            enabled = !isWriting,
        ) {
            Text(if (isWriting) stringResource(R.string.nfc_writing) else stringResource(R.string.nfc_write_button))
        }

        if (!isWriting) {
            Spacer(Modifier.height(ButtonDefaults.IconSpacing))
        }
    }
}
