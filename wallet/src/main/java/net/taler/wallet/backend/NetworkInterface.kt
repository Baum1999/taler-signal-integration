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
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.util.flattenForEach
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import net.taler.common.getDefaultHttpClient
import net.taler.common.toHttpMethod
import net.taler.qtart.Networking
import net.taler.wallet.TAG
import java.io.IOException
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

        requests[id] = GlobalScope.launch {
            val client = getDefaultHttpClient(
                timeoutMs = req.timeoutMs,
                followRedirect = req.redirectMode == Networking.RedirectMode.Transparent,
                logging = req.debug,
            )

            var errorMsg: String? = null

            val resp = try {
                // TODO: reuse the same client for every request
                client.request {
                    url(req.url)

                    method = req.method.toHttpMethod()
                        ?: error("invalid method")

                    headers {
                        parseHeaders(req.headers).map {
                            header(it.key, it.value)
                        }
                    }

                    if (req.body != null) {
                        setBody(req.body)
                    }
                }
            } catch (e: ResponseException) {
                e.response // send non-200 responses to wallet-core anyway
            } catch (e: IOException) {
                Log.d(TAG,  "Exception handling HTTP response", e)
                errorMsg = e.message
                null
            } catch (e: SerializationException) {
                Log.d(TAG, "Exception handling HTTP response", e)
                errorMsg = e.message
                null
            } finally {
                cleanupRequest(id)
                client.close()
            }

            // HTTP response status code or 0 on error.
            val status = if (resp?.status?.value != null) resp.status.value else 0

            val headers = mutableListOf<String>().apply {
                resp?.headers?.flattenForEach { k, v -> add("$k: $v") }
            }.toTypedArray()

            Log.d(TAG, "Sending response to wallet-core")
            sendResponse(
                Networking.ResponseInfo(
                    requestId = id,
                    status = status,
                    errorMsg = errorMsg,
                    headers = headers,
                    body = resp?.body(),
                )
            )
        }
    }

    private fun parseHeaders(headers: Array<String>) = headers.associate {
        val parts = it.split(':', limit = 2)
        parts[0] to parts[1]
    }

    override fun cancelRequest(id: Int): Boolean {
        Log.d(TAG, "HTTP: cancelRequest($id")
        requests[id]?.let { job ->
            job.cancel()
            requests.remove(id)
        }

        return true
    }

    private fun cleanupRequest(id: Int) {
        requests.remove(id)
    }
}