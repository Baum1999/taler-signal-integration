/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
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

package net.taler.merchantlib

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Response<out T> private constructor(
    private val value: Any?
) {
    companion object {
        private const val NETWORK_ERROR_MESSAGE =
            "Network error: check your internet connection and merchant URL."

        suspend fun <T> response(request: suspend () -> T): Response<T> {
            return try {
                success(request())
            } catch (e: Throwable) {
                println(e)
                failure(e)
            }
        }

        fun <T> success(value: T): Response<T> =
            Response(value)

        fun <T> failure(e: Throwable): Response<T> =
            Response(Failure(e))
    }

    val isFailure: Boolean get() = value is Failure

    suspend fun handle(onFailure: ((String) -> Unit)? = null, onSuccess: ((T) -> Unit)? = null) {
        if (value is Failure) onFailure?.let { it(getFailureString(value)) }
        else onSuccess?.let {
            @Suppress("UNCHECKED_CAST")
            it(value as T)
        }
    }

    suspend fun handleSuspend(
        onFailure: ((String) -> Any)? = null,
        onSuccess: (suspend (T) -> Any)? = null
    ) {
        if (value is Failure) onFailure?.let { it(getFailureString(value)) }
        else onSuccess?.let {
            @Suppress("UNCHECKED_CAST")
            it(value as T)
        }
    }

    private suspend fun getFailureString(failure: Failure): String = when (val exception = failure.exception) {
        is ResponseException -> getExceptionString(exception)
        is UnknownHostException,
        is UnresolvedAddressException,
        is ConnectException,
        is SocketTimeoutException -> NETWORK_ERROR_MESSAGE
        is IOException -> exception.message?.takeIf(String::isNotBlank) ?: NETWORK_ERROR_MESSAGE
        else -> exception.message?.takeIf(String::isNotBlank) ?: exception.toString()
    }

    private suspend fun getExceptionString(e: ResponseException): String {
        val response = e.response
        val responseText = response.bodyAsText()
        parseInventoryAvailabilityError(response.status.value, responseText)?.let { return it }
        return try {
            val error = Json.decodeFromString<Error>(responseText)
            buildString {
                append("Error")
                error.code?.let {
                    append(' ')
                    append(it)
                }
                append(" (")
                append(response.status.value)
                append(")")
                error.hint?.takeIf(String::isNotBlank)?.let {
                    append(": ")
                    append(it)
                }
                error.detail?.takeIf(String::isNotBlank)?.let {
                    append(" - ")
                    append(it)
                }
            }
        } catch (ex: Exception) {
            fallbackStatusMessage(response.status.value)
        }
    }

    private fun parseInventoryAvailabilityError(statusCode: Int, body: String): String? {
        if (statusCode != 410) return null
        val error = runCatching {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<InventoryAvailabilityError>(body)
        }.getOrNull() ?: return null
        val productId = error.productId?.takeIf(String::isNotBlank) ?: return null
        val requested = error.unitRequestedQuantity
            ?.takeIf(String::isNotBlank)
            ?: error.requestedQuantity?.toString()
            ?: return null
        val available = error.unitAvailableQuantity
            ?.takeIf(String::isNotBlank)
            ?: error.availableQuantity?.toString()
            ?: return null
        return "Inventory stock unavailable for product $productId: " +
            "$requested requested, $available available."
    }

    private fun fallbackStatusMessage(statusCode: Int): String = when (statusCode) {
        400 -> "Bad request (400)"
        401 -> "Unauthorized (401)"
        403 -> "Forbidden (403)"
        404 -> "Not found (404): check the merchant URL and instance path."
        408 -> "Request timed out (408)"
        in 500..599 -> "Server error ($statusCode)"
        else -> "HTTP error ($statusCode)"
    }

    private class Failure(val exception: Throwable)

    @Serializable
    private class Error(
        val code: Int?,
        val hint: String?,
        val detail: String? = null,
    )

    @Serializable
    private class InventoryAvailabilityError(
        @kotlinx.serialization.SerialName("product_id")
        val productId: String? = null,
        @kotlinx.serialization.SerialName("requested_quantity")
        val requestedQuantity: Int? = null,
        @kotlinx.serialization.SerialName("unit_requested_quantity")
        val unitRequestedQuantity: String? = null,
        @kotlinx.serialization.SerialName("available_quantity")
        val availableQuantity: Int? = null,
        @kotlinx.serialization.SerialName("unit_available_quantity")
        val unitAvailableQuantity: String? = null,
    )
}
