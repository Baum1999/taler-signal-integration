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

package net.taler.wallet.backend

import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.util.toMap
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import net.taler.common.getDefaultHttpClient
import net.taler.common.toHttpMethod
import net.taler.qtart.Networking
import net.taler.wallet.TAG
import java.util.concurrent.ConcurrentHashMap

@OptIn(DelicateCoroutinesApi::class)
class NetworkInterface: Networking.RequestHandler {
    private val requests: ConcurrentHashMap<Int, Job> = ConcurrentHashMap()

    override fun handleRequest(
        req: Networking.RequestInfo,
        id: Int,
        sendResponse: (resp: Networking.ResponseInfo) -> Unit
    ) {
        Log.d(TAG, "HTTP: handleRequest($req, $id")
//        if (req.debug) debugHttpRequest(req)
        debugHttpRequest(req)

        requests[id] = GlobalScope.launch {
            val resp = try {
                getDefaultHttpClient(
                    timeoutMs = req.timeoutMs,
                    followRedirect = req.redirectMode == Networking.RedirectMode.Transparent,
                ).request {
                    url(req.url)

                    method = req.method.toHttpMethod() ?: error("invalid method")

                    headers {
                        req.headers.forEach {
                            val parts = it.split(':', limit = 2)
                            if (parts.size == 2) header(parts[0].trim(), parts[1].trim())
                        }
                    }

                    if (req.body != null) {
                        setBody(req.body)
                    }
                }
            } catch (e: ClientRequestException) {
                Log.d(TAG, e.message)
                null
            } catch (e: ServerResponseException) {
                Log.d(TAG, e.message)
                null
            } catch (e: IOException) {
                Log.d(TAG, e.message ?: "IOException")
                null
            } catch (e: SerializationException) {
                Log.d(TAG, e.message ?: "SerializationException")
                null
            } ?: return@launch

            // HTTP response status code or 0 on error.
            val status = if (resp.status.value in 200 until 300) resp.status.value else 0

            // When status is 0, error message.
            val errorMsg = if (status == 0) "There was an error" else null

            Log.d(TAG, "Sending response to wallet-core")
            sendResponse(
                Networking.ResponseInfo(
                    requestId = id,
                    status = status,
                    errorMsg = errorMsg,
                    headers = resp.headers.toMap()
                        .map { (k, v) -> "$k: $v" }
                        .toTypedArray(),
                    body = resp.body(),
                )
            )
        }
    }

    override fun cancelRequest(id: Int): Boolean {
        Log.d(TAG, "HTTP: cancelRequest($id")
        requests[id]?.let { job ->
            job.cancel()
            requests.remove(id)
        }

        return true
    }

    private fun debugHttpRequest(req: Networking.RequestInfo) {
        Log.d(TAG, "HTTP request: body = ${req.body}")
        req.headers.forEachIndexed { i, header ->
            Log.d(TAG, "HTTP: header[$i] = $header")
        }
        Log.d(TAG, "HTTP request: method = ${req.method}")
        Log.d(TAG, "HTTP request: redirectMode = ${req.redirectMode}")
        Log.d(TAG, "HTTP request: timeoutMs = ${req.timeoutMs}")
        Log.d(TAG, "HTTP request: url = ${req.url}")
    }
}