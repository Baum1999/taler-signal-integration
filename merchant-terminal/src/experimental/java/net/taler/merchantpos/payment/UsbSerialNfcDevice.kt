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
 * GNU Taler.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taler.merchantpos.payment

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Experimental USB-host integration; this source set is intentionally not built.
 *
 * Sends the active payment or refund URI to the optional USB-connected NFC broadcaster.
 *
 * The broadcaster accepts a URL terminated by a line feed and clears its NFC
 * payload when it receives an empty line.
 */
class UsbSerialNfcDevice(private val context: Context) : AutoCloseable {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val permissionAction = "${context.packageName}.USB_SERIAL_NFC_PERMISSION"

    private var started = false
    private var desiredPayload = RESET_PAYLOAD
    private var lastSentPayload: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                permissionAction -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        enqueueSend(force = true)
                    } else {
                        Log.w(TAG, "USB serial permission denied")
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> enqueueSend(force = true)
            }
        }
    }

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(permissionAction)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enqueueSend(force = true)
    }

    fun setUri(uri: String?) {
        val payload = uri?.takeIf(String::isNotBlank)?.plus("\n") ?: RESET_PAYLOAD
        val changed = synchronized(lock) {
            if (desiredPayload == payload) false else {
                desiredPayload = payload
                true
            }
        }
        if (changed) enqueueSend()
    }

    private fun enqueueSend(force: Boolean = false) {
        val shouldSend = synchronized(lock) {
            started && (force || desiredPayload != lastSentPayload)
        }
        if (!shouldSend) return

        executor.execute {
            val payload = synchronized(lock) {
                if (!started) return@execute
                desiredPayload
            }
            send(if (payload == RESET_PAYLOAD) listOf(payload) else listOf(RESET_PAYLOAD, payload))
        }
    }

    private fun send(payloads: List<String>) {
        val driver = UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull()
            ?: return
        val device = driver.device
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            requestPermission(device)
            return
        }

        val port = driver.ports.firstOrNull() ?: run {
            connection.close()
            return
        }
        try {
            port.open(connection)
            port.setParameters(
                BAUD_RATE,
                DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE,
            )
            payloads.forEach { payload ->
                port.write(payload.toByteArray(Charsets.UTF_8), WRITE_TIMEOUT_MILLIS)
            }
            synchronized(lock) {
                if (desiredPayload == payloads.last()) lastSentPayload = payloads.last()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to USB NFC device", e)
        } finally {
            try {
                port.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close USB serial port", e)
            }
            connection.close()
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(permissionAction).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    override fun close() {
        synchronized(lock) {
            if (!started) return
            desiredPayload = RESET_PAYLOAD
            lastSentPayload = null
        }
        // Keep ordering with a preceding URI write, but do not skip the reset
        // after marking this instance inactive.
        executor.execute { send(listOf(RESET_PAYLOAD)) }
        context.unregisterReceiver(receiver)
        synchronized(lock) { started = false }
        executor.shutdown()
    }

    private companion object {
        const val TAG = "UsbSerialNfcDevice"
        const val BAUD_RATE = 115200
        const val DATA_BITS = 8
        const val WRITE_TIMEOUT_MILLIS = 1000
        const val RESET_PAYLOAD = "\n"
    }
}
