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

package net.taler.cashier.config

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
import androidx.security.crypto.MasterKeys
import androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import net.taler.cashier.BuildConfig
import net.taler.cashier.Response.Companion.response
import net.taler.common.ChallengeConfirmRequest
import net.taler.common.ChallengesResponse
import net.taler.common.Timestamp
import net.taler.common.TokenDuration
import net.taler.common.TokenRequest
import net.taler.common.TokenSuccessResponse
import net.taler.common.Version
import net.taler.lib.android.getIncompatibleStringOrNull

val VERSION_BANK = Version.parse(BuildConfig.BACKEND_API_VERSION)!!
private const val PREF_NAME = "net.taler.cashier.prefs"
private const val PREF_KEY_BANK_URL = "bankUrl"
private const val PREF_KEY_USERNAME = "username"
private const val PREF_KEY_PASSWORD = "password"
private const val PREF_KEY_ACCESS_TOKEN = "accessToken"
private const val PREF_KEY_EXPIRATION = "expiration"
private const val PREF_KEY_CURRENCY = "currency"

private val TAG = ConfigManager::class.java.simpleName

class ConfigManager(
    private val app: Application,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
) {

    val configDestination = ConfigFragmentDirections.actionGlobalConfigFragment()

    private val masterKeyAlias = MasterKeys.getOrCreate(AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        PREF_NAME, masterKeyAlias, app, AES256_SIV, AES256_GCM
    )

    internal var config = Config(
        bankUrl = prefs.getString(PREF_KEY_BANK_URL, "")!!,
        username = prefs.getString(PREF_KEY_USERNAME, "")!!,
        password = prefs.getString(PREF_KEY_PASSWORD, "")!!,
        accessToken = prefs.getString(PREF_KEY_ACCESS_TOKEN, null),
        expiration = prefs.getLong(PREF_KEY_EXPIRATION, 0).let { if (it == 0L) null else Timestamp.fromMillis(it) }
    )

    private val mCurrency = MutableLiveData<String>(
        prefs.getString(PREF_KEY_CURRENCY, null)
    )
    internal val currency: LiveData<String> = mCurrency

    private val mConfigResult = MutableLiveData<ConfigResult?>()
    val configResult: LiveData<ConfigResult?> = mConfigResult

    fun hasConfig() = config.bankUrl.isNotEmpty()
            && config.username.isNotEmpty()
            && config.password.isNotEmpty()

    /**
     * Start observing [configResult] after calling this to get the result async.
     * Warning: Ignore null results that are used to reset old results.
     */
    @UiThread
    fun checkAndSaveConfig(config: Config, challengeIds: List<String> = emptyList()) = scope.launch {
        mConfigResult.value = null
        if (config.isTokenValid()) {
            this@ConfigManager.config = config
            mCurrency.postValue(prefs.getString(PREF_KEY_CURRENCY, null))
            mConfigResult.postValue(ConfigResult.Success)
            return@launch
        }
        checkConfig(config).onError { failure ->
            val result = if (failure.isOffline(app)) {
                ConfigResult.Offline
            } else {
                ConfigResult.Error(failure.statusCode == Unauthorized, failure.msg)
            }
            mConfigResult.postValue(result)
        }.onSuccess { response ->
            val versionIncompatible =
                VERSION_BANK.getIncompatibleStringOrNull(app, response.version)
            val result = if (versionIncompatible != null) {
                ConfigResult.Error(false, versionIncompatible)
            } else {
                // get access token
                when (val tokenRes = getToken(config, challengeIds)) {
                    is TokenResult.Success, is TokenResult.TanRequired -> {
                        mCurrency.postValue(response.currency)
                        prefs.edit { putString(PREF_KEY_CURRENCY, response.currency) }
                        // save config
                        if (tokenRes is TokenResult.Success) {
                            saveConfig(config.copy(
                                accessToken = tokenRes.accessToken,
                                expiration = tokenRes.expiration
                            ))
                        } else {
                            saveConfig(config)
                        }
                        when (tokenRes) {
                            is TokenResult.Success -> ConfigResult.Success
                            is TokenResult.TanRequired -> ConfigResult.TanRequired(tokenRes.challenges, tokenRes.combiAnd)
                        }
                    }

                    is TokenResult.Error -> {
                        ConfigResult.Error(tokenRes.authError, tokenRes.msg)
                    }

                    else -> ConfigResult.Unknown
                }
            }
            mConfigResult.postValue(result)
        }
    }

    private suspend fun checkConfig(config: Config) = withContext(Dispatchers.IO) {
        val url = config.bankUrl.toUri()
            .buildUpon()
            .appendPath("config")
            .build()
            .toString()
        Log.d(TAG, "Checking config: $url")
        response {
            httpClient.get(url).body<ConfigResponse>()
        }
    }

    private suspend fun getToken(config: Config, challengeIds: List<String> = emptyList()): TokenResult? = withContext(Dispatchers.IO) {
        // fetch authentication token
        val tokenUrl = config.bankUrl.toUri()
            .buildUpon()
            .appendPath("accounts")
            .appendPath(config.username)
            .appendPath("token")
            .build()
            .toString()
        var result: TokenResult? = null
        response {
            val res = httpClient.post(tokenUrl) {
                header(Authorization, config.basicAuth)
                if (challengeIds.isNotEmpty()) {
                    header("Taler-Challenge-Ids", challengeIds.joinToString(","))
                }
                contentType(ContentType.Application.Json)
                setBody(TokenRequest(scope = "readwrite", duration = TokenDuration.Forever))
            }

            return@response when (res.status.value) {
                200 -> res.body<TokenSuccessResponse>()
                202 -> res.body<ChallengesResponse>()
                else -> res.body<Unit>()
            }
        }.onSuccess { res ->
            if (res is TokenSuccessResponse) {
                result = TokenResult.Success(res.expiration, res.accessToken)
            } else if (res is ChallengesResponse) {
                result = TokenResult.TanRequired(res.challenges, res.combiAnd)
            }
        }.onError { err ->
            result = TokenResult.Error(err.statusCode == Unauthorized, err.msg)
        }
        return@withContext result
    }

    suspend fun requestChallenge(
        baseUrl: String,
        username: String,
        challengeId: String
    ) = withContext(Dispatchers.IO) {
        val challengeUrl = baseUrl.toUri()
            .buildUpon()
            .appendPath("accounts")
            .appendPath(username)
            .appendPath("challenge")
            .appendPath(challengeId)
            .build()
            .toString()
        httpClient.post(challengeUrl) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { })
        }
    }

    suspend fun confirmChallenge(
        baseUrl: String,
        username: String,
        challengeId: String,
        tan: String
    ) = withContext(Dispatchers.IO) {
        val confirmUrl = baseUrl.toUri()
            .buildUpon()
            .appendPath("accounts")
            .appendPath(username)
            .appendPath("challenge")
            .appendPath(challengeId)
            .appendPath("confirm")
            .build()
            .toString()
        httpClient.post(confirmUrl) {
            contentType(ContentType.Application.Json)
            setBody(ChallengeConfirmRequest(tan = tan))
        }
    }

    @WorkerThread
    @SuppressLint("ApplySharedPref")
    internal fun saveConfig(config: Config) {
        this.config = config
        prefs.edit(commit = true) {
            putString(PREF_KEY_BANK_URL, config.bankUrl)
            putString(PREF_KEY_USERNAME, config.username)
            putString(PREF_KEY_PASSWORD, config.password)
            if (config.accessToken != null) {
                putString(PREF_KEY_ACCESS_TOKEN, config.accessToken)
            } else {
                remove(PREF_KEY_ACCESS_TOKEN)
            }
            if (config.expiration != null) {
                putLong(PREF_KEY_EXPIRATION, config.expiration.ms)
            } else {
                remove(PREF_KEY_EXPIRATION)
            }
        }
    }

    @WorkerThread
    fun lock() {
        saveConfig(config.copy(password = ""))
    }

}
