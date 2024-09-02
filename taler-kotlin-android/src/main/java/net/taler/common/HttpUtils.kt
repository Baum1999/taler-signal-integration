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

package net.taler.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun getDefaultHttpClient(
    withJson: Boolean = true,
    timeoutMs: Long? = null,
    followRedirect: Boolean = false,
    logging: Boolean = true,
): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    followRedirects = followRedirect
    engine {
        config {
            retryOnConnectionFailure(true)
        }
    }
    install(ContentNegotiation) {
        if (withJson) {
            json(Json {
                encodeDefaults = false
                ignoreUnknownKeys = true
            })
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = if (timeoutMs != null && timeoutMs > 0) {
            timeoutMs
        } else {
            HttpTimeout.INFINITE_TIMEOUT_MS
        }

        socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        connectTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
    }
    install(HttpRedirect) {
        checkHttpMethod = !followRedirect
    }
    install(Logging) {
        logger = Logger.ANDROID
        level = if (logging) LogLevel.INFO else LogLevel.NONE
    }
}

fun String.toHttpMethod(): HttpMethod? = when(this) {
    "GET" -> HttpMethod.Get
    "POST" -> HttpMethod.Post
    "PUT" -> HttpMethod.Put
    "PATCH" -> HttpMethod.Patch
    "DELETE" -> HttpMethod.Delete
    "OPTIONS" -> HttpMethod.Options
    else -> null
}