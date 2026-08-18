/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.backend

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.taler.qtart.TalerWalletCore
import net.taler.wallet.BuildConfig
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume


fun interface NotificationReceiver {
    fun onNotificationReceived(payload: NotificationPayload)
}

class BackendManager(
    private val notificationReceiver: NotificationReceiver,
) {

    companion object {
        private const val TAG = "BackendManager"
        private const val TAG_CORE = "taler-wallet-embedded"
        private const val REQUEST_TIMEOUT_MS = 60_000L
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        /** Only one wallet-core may run per process. */
        @JvmStatic
        private val coreRunning = AtomicBoolean(false)
    }

    private val walletCore = TalerWalletCore()
    private val requestManager = RequestManager()
    private val networkInterface = NetworkInterface()

    /**
     * Dispatches messages from wallet-core. Must be replaced after [destroy]: launching
     * on a cancelled scope drops every message silently, timing out all requests.
     */
    @Volatile
    private var scope = newScope()

    private var running = false

    private fun newScope() = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Synchronized
    fun run() {
        if (running) return
        if (!coreRunning.compareAndSet(false, true)) {
            Log.e(TAG, "refusing to run a second wallet-core in this process")
            return
        }
        running = true
        if (!scope.isActive) scope = newScope()
        walletCore.setMessageHandler { onMessageReceived(it) }
        walletCore.setHttpClient(networkInterface)
        if (BuildConfig.DEBUG) walletCore.setStdoutHandler {
            Log.d(TAG_CORE, it)
        }
        walletCore.run()
    }

    @Synchronized
    fun destroy() {
        if (!running) return
        running = false
        scope.cancel()
        walletCore.destroy()
        coreRunning.set(false)
    }

    suspend fun send(operation: String, args: JSONObject? = null): ApiResponse {
        var requestId = -1
        val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                requestManager.addRequest(cont) { id ->
                    requestId = id
                    val request = JSONObject().apply {
                        put("id", id)
                        put("operation", operation)
                        if (args != null) put("args", args)
                    }
                    Log.d(TAG, "sending message:\n${request.toString(2)}")
                    walletCore.sendRequest(request.toString())
                }
                cont.invokeOnCancellation {
                    requestManager.getAndRemoveContinuation(requestId)
                }
            }
        }
        if (response != null) return response
        return ApiResponse.Error(
            id = requestId,
            operation = operation,
            error = buildJsonObject {
                put("hint", JsonPrimitive("wallet-core did not respond"))
                put("message", JsonPrimitive("request '$operation' timed out"))
            },
        )
    }

    private fun onMessageReceived(msg: String) = scope.launch {
        Log.d(TAG, "message received: $msg")
        when (val message = json.decodeFromString<ApiMessage>(msg)) {
            is ApiMessage.Notification -> {
                notificationReceiver.onNotificationReceived(message.payload)
            }
            is ApiResponse -> {
                val id = message.id
                val cont = requestManager.getAndRemoveContinuation(id)
                if (cont == null) {
                    Log.e(TAG, "wallet returned unknown request ID ($id)")
                } else {
                    cont.resume(message)
                }
            }
        }
    }
}
