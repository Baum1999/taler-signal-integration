/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.lib.android

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter.getDefaultAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.math.BigInteger

class TalerNfcService : HostApduService() {

    private var uri: String? = null
    private val ndefMessage: NdefMessage?
        get() = uri?.let {
            val record = createUriRecord(it)
            NdefMessage(record)
        }

    private val ndefUriBytes: ByteArray?
        get() = ndefMessage?.toByteArray()

    private val ndefUriLen: ByteArray?
        get() = ndefUriBytes?.size?.toLong()?.let { size ->
            fillByteArrayToFixedDimension(
                BigInteger.valueOf(size).toByteArray(),
                2
            )
        }

    private var readCapabilityContainerCheck = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("uri")?.let { uri = it }

        Log.d(TAG, "onStartCommand() | URI: $uri")

        return Service.START_STICKY
    }

    override fun processCommandApdu(
        commandApdu: ByteArray?,
        extras: Bundle?
    ): ByteArray {

        Log.d(TAG, "Processing command APDU")

        if (commandApdu == null) {
            Log.d(TAG, "processCommandApi() no data received")
            return A_ERROR
        }

        val message = ndefMessage
        if (message == null) {
            Log.d(TAG, "processCommandApi() no data to write")
            return A_ERROR
        }

        //
        // The following flow is based on Appendix E "Example of Mapping Version 2.0 Command Flow"
        // in the NFC Forum specification
        //
        Log.d(TAG, "processCommandApdu() | incoming commandApdu: " + commandApdu.toHex())

        //
        // First command: NDEF Tag Application select (Section 5.5.2 in NFC Forum spec)
        //
        if (APDU_SELECT.contentEquals(commandApdu)) {
            Log.d(TAG, "APDU_SELECT triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        //
        // Second command: Capability Container select (Section 5.5.3 in NFC Forum spec)
        //
        if (CAPABILITY_CONTAINER_OK.contentEquals(commandApdu)) {
            Log.d(TAG, "CAPABILITY_CONTAINER_OK triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        //
        // Third command: ReadBinary data from CC file (Section 5.5.4 in NFC Forum spec)
        //
        if (READ_CAPABILITY_CONTAINER.contentEquals(commandApdu) && !readCapabilityContainerCheck) {
            Log.d(TAG, "READ_CAPABILITY_CONTAINER triggered. Our Response: " + READ_CAPABILITY_CONTAINER_RESPONSE.toHex())

            readCapabilityContainerCheck = true
            return READ_CAPABILITY_CONTAINER_RESPONSE
        }

        //
        // Fourth command: NDEF Select command (Section 5.5.5 in NFC Forum spec)
        //
        if (NDEF_SELECT_OK.contentEquals(commandApdu)) {
            Log.d(TAG, "NDEF_SELECT_OK triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        if (NDEF_READ_BINARY_NLEN.contentEquals(commandApdu)) {
            // Build our response
            val response = ByteArray(ndefUriLen!!.size + A_OKAY.size)
            System.arraycopy(ndefUriLen!!, 0, response, 0, ndefUriLen!!.size)
            System.arraycopy(A_OKAY, 0, response, ndefUriLen!!.size, A_OKAY.size)

            Log.d(TAG, "NDEF_READ_BINARY_NLEN triggered. Our Response: " + response.toHex())

            readCapabilityContainerCheck = false
            return response
        }

        if (commandApdu.sliceArray(0..1).contentEquals(NDEF_READ_BINARY)) {
            val offset = commandApdu.sliceArray(2..3).toHex().toInt(16)
            val length = commandApdu.sliceArray(4..4).toHex().toInt(16)

            val fullResponse = ByteArray(ndefUriLen!!.size + ndefUriBytes!!.size)
            System.arraycopy(ndefUriLen!!, 0, fullResponse, 0, ndefUriLen!!.size)
            System.arraycopy(
                ndefUriBytes!!,
                0,
                fullResponse,
                ndefUriLen!!.size,
                ndefUriBytes!!.size,
            )

            Log.d(TAG, "NDEF_READ_BINARY triggered. Full data: " + fullResponse.toHex())
            Log.d(TAG, "READ_BINARY - OFFSET: $offset - LEN: $length")

            val slicedResponse = fullResponse.sliceArray(offset until fullResponse.size)

            // Build our response
            val realLength = if (slicedResponse.size <= length) slicedResponse.size else length
            val response = ByteArray(realLength + A_OKAY.size)

            System.arraycopy(slicedResponse, 0, response, 0, realLength)
            System.arraycopy(A_OKAY, 0, response, realLength, A_OKAY.size)

            Log.d(TAG, "NDEF_READ_BINARY triggered. Our Response: " + response.toHex())

            readCapabilityContainerCheck = false
            return response
        }

        //
        // We're doing something outside our scope
        //
        Log.wtf(TAG, "processCommandApdu() | I don't know what's going on!!!")
        return A_ERROR
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated() Fired! Reason: $reason")
    }

    private fun ByteArray.toHex(): String {
        val result = StringBuffer()

        forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS[firstIndex])
            result.append(HEX_CHARS[secondIndex])
        }

        return result.toString()
    }

    private fun createUriRecord(uri: String) = NdefRecord.createUri(uri)

    private fun fillByteArrayToFixedDimension(array: ByteArray, fixedSize: Int): ByteArray {
        if (array.size == fixedSize) {
            return array
        }

        val start = byteArrayOf(0x00.toByte())
        val filledArray = ByteArray(start.size + array.size)
        System.arraycopy(start, 0, filledArray, 0, start.size)
        System.arraycopy(array, 0, filledArray, start.size, array.size)
        return fillByteArrayToFixedDimension(filledArray, fixedSize)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() NFC service")
        uri = null
    }

    companion object {
        private const val TAG = "taler-wallet-hce"

        private val APDU_SELECT = byteArrayOf(
            0x00.toByte(), // CLA	- Class - Class of instruction
            0xA4.toByte(), // INS	- Instruction - Instruction code
            0x04.toByte(), // P1	- Parameter 1 - Instruction parameter 1
            0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
            0x07.toByte(), // Lc field	- Number of bytes present in the data field of the command
            0xD2.toByte(),
            0x76.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x85.toByte(),
            0x01.toByte(),
            0x01.toByte(), // NDEF Tag Application name
            0x00.toByte(), // Le field	- Maximum number of bytes expected in the data field of the response to the command
        )

        private val CAPABILITY_CONTAINER_OK = byteArrayOf(
            0x00.toByte(), // CLA	- Class - Class of instruction
            0xa4.toByte(), // INS	- Instruction - Instruction code
            0x00.toByte(), // P1	- Parameter 1 - Instruction parameter 1
            0x0c.toByte(), // P2	- Parameter 2 - Instruction parameter 2
            0x02.toByte(), // Lc field	- Number of bytes present in the data field of the command
            0xe1.toByte(),
            0x03.toByte(), // file identifier of the CC file
        )

        private val READ_CAPABILITY_CONTAINER = byteArrayOf(
            0x00.toByte(), // CLA	- Class - Class of instruction
            0xb0.toByte(), // INS	- Instruction - Instruction code
            0x00.toByte(), // P1	- Parameter 1 - Instruction parameter 1
            0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
            0x0f.toByte(), // Lc field	- Number of bytes present in the data field of the command
        )

        private val READ_CAPABILITY_CONTAINER_RESPONSE = byteArrayOf(
            0x00.toByte(), 0x11.toByte(), // CCLEN length of the CC file
            0x20.toByte(), // Mapping Version 2.0
            0xFF.toByte(), 0xFF.toByte(), // MLe maximum
            0xFF.toByte(), 0xFF.toByte(), // MLc maximum
            0x04.toByte(), // T field of the NDEF File Control TLV
            0x06.toByte(), // L field of the NDEF File Control TLV
            0xE1.toByte(), 0x04.toByte(), // File Identifier of NDEF file
            0xFF.toByte(), 0xFE.toByte(), // Maximum NDEF file size of 65534 bytes
            0x00.toByte(), // Read access without any security
            0xFF.toByte(), // Write access without any security
            0x90.toByte(), 0x00.toByte(), // A_OKAY
        )

        private val NDEF_SELECT_OK = byteArrayOf(
            0x00.toByte(), // CLA	- Class - Class of instruction
            0xa4.toByte(), // Instruction byte (INS) for Select command
            0x00.toByte(), // Parameter byte (P1), select by identifier
            0x0c.toByte(), // Parameter byte (P1), select by identifier
            0x02.toByte(), // Lc field	- Number of bytes present in the data field of the command
            0xE1.toByte(),
            0x04.toByte(), // file identifier of the NDEF file retrieved from the CC file
        )

        private val NDEF_READ_BINARY = byteArrayOf(
            0x00.toByte(), // Class byte (CLA)
            0xb0.toByte(), // Instruction byte (INS) for ReadBinary command
        )

        private val NDEF_READ_BINARY_NLEN = byteArrayOf(
            0x00.toByte(), // Class byte (CLA)
            0xb0.toByte(), // Instruction byte (INS) for ReadBinary command
            0x00.toByte(),
            0x00.toByte(), // Parameter byte (P1, P2), offset inside the CC file
            0x02.toByte(), // Le field
        )

        private val A_OKAY = byteArrayOf(
            0x90.toByte(), // SW1	Status byte 1 - Command processing status
            0x00.toByte(), // SW2	Status byte 2 - Command processing qualifier
        )

        private val A_ERROR = byteArrayOf(
            0x6A.toByte(), // SW1	Status byte 1 - Command processing status
            0x82.toByte(), // SW2	Status byte 2 - Command processing qualifier
        )

        private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

        /**
         * Returns true if NFC is supported and false otherwise.
         */
        fun hasNfc(context: Context): Boolean {
            return getDefaultAdapter(context) != null
        }

        fun setDefaultHandler(activity: Activity) {
            val adapter = getDefaultAdapter(activity) ?: return
            val emulation = CardEmulation.getInstance(adapter)
            // TODO: find an alternative for when canonicalName is null
            try {
                val cn = ComponentName(activity, TalerNfcService::class.java)
                emulation.setPreferredService(activity, cn)
            } catch (e: NullPointerException) {
                Log.d(TAG, "Not setting this app as the preferred NFC handler!")
            }
        }

        fun unsetDefaultHandler(activity: Activity) {
            val adapter = getDefaultAdapter(activity) ?: return
            val emulation = CardEmulation.getInstance(adapter)
            emulation.unsetPreferredService(activity)
        }

        fun setUri(activity: Activity, uri: String) {
            if (!hasNfc(activity)) return
            val intent = Intent(activity, TalerNfcService::class.java)
            intent.putExtra("uri", uri)
            activity.startService(intent)
        }

        fun clearUri(activity: Activity) {
            if (!hasNfc(activity)) return
            val intent = Intent(activity, TalerNfcService::class.java)
            activity.stopService(intent)
        }
    }
}