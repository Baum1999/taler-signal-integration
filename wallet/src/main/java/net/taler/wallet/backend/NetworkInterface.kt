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
import io.ktor.client.plugins.*
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.util.flattenForEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.taler.common.getDefaultHttpClient
import net.taler.common.toHttpMethod
import net.taler.qtart.Networking
import net.taler.wallet.main.TAG
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** The HTTP client wallet-core drives instead of doing its own networking.
 * Every request must be answered exactly once: wallet-core suspends a promise per
 * request with no timeout of its own, so an unanswered request hangs the wallet. */
@OptIn(DelicateCoroutinesApi::class)
class NetworkInterface: Networking.RequestHandler {
    private val requests: ConcurrentHashMap<Int, Job> = ConcurrentHashMap()

    private val sharedClient by lazy {
        getDefaultHttpClient(followRedirect = false, logging = false)
    }

    private val redirectClient by lazy {
        getDefaultHttpClient(followRedirect = true, logging = false)
    }

    override fun handleRequest(
        req: Networking.RequestInfo,
        id: Int,
        sendResponse: (resp: Networking.ResponseInfo) -> Unit
    ) {
        Log.d(TAG, "HTTP: handleRequest($req, $id)")

        requests[id] = GlobalScope.launch {
            val answered = AtomicBoolean(false)
            fun answer(
                status: Int,
                error: String?,
                respHeaders: Array<String>,
                respBody: ByteArray?,
            ) {
                if (answered.compareAndSet(false, true)) {
                    sendResponse(
                        Networking.ResponseInfo(
                            requestId = id,
                            status = status,
                            errorMsg = error,
                            headers = respHeaders,
                            body = respBody,
                        )
                    )
                }
            }
            try {
                val client = if (req.redirectMode == Networking.RedirectMode.Transparent) {
                    redirectClient
                } else {
                    sharedClient
                }

                val method = req.method.toHttpMethod()
                if (method == null) {
                    // Reporting it as a transport failure is the only way to tell wallet-core.
                    answer(0, "unsupported HTTP method '${req.method}'", emptyArray(), null)
                    return@launch
                }

                var errorMsg: String? = null
                val resp = try {
                    client.request {
                        url(req.url)
                        this.method = method
                        parseHeaders(req.headers).forEach { (k, v) -> header(k, v) }
                        if (req.body != null) {
                            setBody(req.body)
                        }
                        timeout {
                            val t = req.timeoutMs
                            if (t > 0) {
                                requestTimeoutMillis = t
                                socketTimeoutMillis = t
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ResponseException) {
                    // Send non-200 responses on: a 402 from a sync provider is a normal part
                    // of the backup protocol, not a failure.
                    e.response
                } catch (e: Throwable) {
                    Log.d(TAG, "Exception performing HTTP request", e)
                    errorMsg = e.message ?: e.toString()
                    null
                }

                // Body reads can fail on their own; keep them inside the catch.
                val body: ByteArray? = if (resp == null) null else try {
                    resp.body<ByteArray>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.d(TAG, "Exception reading HTTP response body", e)
                    errorMsg = e.message ?: e.toString()
                    null
                }

                val respHeaders = mutableListOf<String>().apply {
                    resp?.headers?.flattenForEach { k, v -> add("$k: $v") }
                }.toTypedArray()

                Log.d(TAG, "Sending response to wallet-core")
                answer(resp?.status?.value ?: 0, errorMsg, respHeaders, body)
            } catch (e: CancellationException) {
                // wallet-core cancelled the request; do not answer it.
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected failure handling HTTP request $id", e)
                answer(0, e.message ?: e.toString(), emptyArray(), null)
            } finally {
                cleanupRequest(id)
            }
        }
    }

    /** Splits `"Name: value"` header lines; a line without a colon is skipped, never thrown. */
    private fun parseHeaders(headers: Array<String>): List<Pair<String, String>> =
        headers.mapNotNull {
            val parts = it.split(':', limit = 2)
            if (parts.size < 2) {
                Log.w(TAG, "ignoring malformed request header '$it'")
                null
            } else parts[0].trim() to parts[1].trim()
        }

    override fun cancelRequest(id: Int): Boolean {
        Log.d(TAG, "HTTP: cancelRequest($id)")
        requests.remove(id)?.cancel()
        return true
    }

    private fun cleanupRequest(id: Int) {
        requests.remove(id)
    }
}
