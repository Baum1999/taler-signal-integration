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

package net.taler.merchantpos.config

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.util.Base64.NO_WRAP
import android.util.Base64.encodeToString
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.taler.common.Version
import net.taler.common.getIncompatibleStringOrNull
import net.taler.merchantlib.ConfigResponse
import net.taler.merchantlib.MerchantApi
import net.taler.merchantlib.MerchantConfig
import net.taler.merchantpos.BuildConfig
import net.taler.merchantpos.R

private const val SETTINGS_NAME = "taler-merchant-terminal"

private const val SETTINGS_CONFIG_VERSION = "configVersion"

internal const val CONFIG_VERSION_OLD = 0
internal const val CONFIG_VERSION_NEW = 1

// Old JSON config + basic auth config

private const val SETTINGS_CONFIG_URL = "configUrl"
private const val SETTINGS_USERNAME = "username"
private const val SETTINGS_PASSWORD = "password"
private const val SETTINGS_SAVE_PASSWORD = "savePassword"

internal const val OLD_CONFIG_URL_DEMO = "https://docs.taler.net/_static/sample-pos-config.json"
internal const val OLD_CONFIG_USERNAME_DEMO = ""
internal const val OLD_CONFIG_PASSWORD_DEMO = ""

// New merchant API + token config

private const val SETTINGS_MERCHANT_URL = "merchantUrl"
private const val SETTINGS_ACCESS_TOKEN = "accessToken"

internal const val NEW_CONFIG_URL_DEMO = "https://backend.demo.taler.net/instances/pos"

private val VERSION = Version.parse(BuildConfig.BACKEND_API_VERSION)!!

private val TAG = ConfigManager::class.java.simpleName

interface ConfigurationReceiver {
    /**
     * Returns null if the configuration was valid, or a error string for user display otherwise.
     */
    suspend fun onConfigurationReceived(posConfig: PosConfig, currency: String): String?
}

class ConfigManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val api: MerchantApi
) {

    private val prefs = context.getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
    private val configurationReceivers = ArrayList<ConfigurationReceiver>()

    var config: Config = if (prefs.getInt(SETTINGS_CONFIG_VERSION, CONFIG_VERSION_NEW) == CONFIG_VERSION_NEW) {
        Config.New(
            merchantUrl = prefs.getString(SETTINGS_MERCHANT_URL, "")!!,
            accessToken = prefs.getString(SETTINGS_ACCESS_TOKEN, "")!!,
            savePassword = prefs.getBoolean(SETTINGS_SAVE_PASSWORD, true),
        )
    } else {
        Config.Old(
            configUrl = prefs.getString(SETTINGS_CONFIG_URL, "")!!,
            username = prefs.getString(SETTINGS_USERNAME, OLD_CONFIG_USERNAME_DEMO)!!,
            password = prefs.getString(SETTINGS_PASSWORD, OLD_CONFIG_PASSWORD_DEMO)!!,
            savePassword = prefs.getBoolean(SETTINGS_SAVE_PASSWORD, true)
        )
    }

    @Volatile
    var merchantConfig: MerchantConfig? = null
        private set

    @Volatile
    var currency: String? = null
        private set

    private val mConfigUpdateResult = MutableLiveData<ConfigUpdateResult?>()
    val configUpdateResult: LiveData<ConfigUpdateResult?> = mConfigUpdateResult

    fun addConfigurationReceiver(receiver: ConfigurationReceiver) {
        configurationReceivers.add(receiver)
    }

    @UiThread
    fun reloadConfig() {
        fetchConfig(config, true)
    }

    @UiThread
    fun fetchConfig(config: Config, save: Boolean) {
        mConfigUpdateResult.value = null
        val configToSave = if (save) {
            if (config.savePassword()) config else when (val c = config) {
                is Config.Old -> c.copy(password = "")
                is Config.New -> c.copy(accessToken = "")
            }
        } else null

        scope.launch(Dispatchers.IO) {
            try {
                val url = when(val c = config) {
                    is Config.Old -> c.configUrl
                    is Config.New -> Uri.parse(c.merchantUrl)
                        .buildUpon()
                        .appendPath("private/pos")
                        .build()
                        .toString()
                }

                // get PoS configuration
                val posConfig: PosConfig = httpClient.get(url) {
                    when (val c = config) {
                        is Config.Old -> {
                            val credentials = "${c.username}:${c.password}"
                            val auth = ("Basic ${encodeToString(credentials.toByteArray(), NO_WRAP)}")
                            header(Authorization, auth)
                        }
                        is Config.New -> {
                            val token = "secret-token:${c.accessToken}"
                            val auth = ("Bearer $token")
                            header(Authorization, auth)
                        }
                    }
                }.body()

                val merchantConfig = when (val c = config) {
                    is Config.Old -> posConfig.merchantConfig!!
                    is Config.New -> MerchantConfig(c.merchantUrl, "secret-token:${c.accessToken}")
                }

                // get config from merchant backend API
                api.getConfig(merchantConfig.baseUrl).handleSuspend(::onNetworkError) {
                    onMerchantConfigReceived(configToSave, posConfig, merchantConfig, it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving merchant config", e)
                val msg = if (e is ClientRequestException) {
                    context.getString(
                        if (e.response.status == Unauthorized) R.string.config_auth_error
                        else R.string.config_error_network
                    )
                } else {
                    context.getString(R.string.config_error_malformed)
                }
                onNetworkError(msg)
            }
        }
    }

    @WorkerThread
    private suspend fun onMerchantConfigReceived(
        newConfig: Config?,
        posConfig: PosConfig,
        merchantConfig: MerchantConfig,
        configResponse: ConfigResponse
    ) {
        val versionIncompatible =
            VERSION.getIncompatibleStringOrNull(context, configResponse.version)
        if (versionIncompatible != null) {
            Log.e(TAG, "Versions incompatible $configResponse")
            mConfigUpdateResult.postValue(ConfigUpdateResult.Error(versionIncompatible))
            return
        }
        for (receiver in configurationReceivers) {
            val result = try {
                receiver.onConfigurationReceived(posConfig, configResponse.currency)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling configuration by ${receiver::class.java.simpleName}", e)
                context.getString(R.string.config_error_unknown)
            }
            if (result != null) { // error
                mConfigUpdateResult.postValue(ConfigUpdateResult.Error(result))
                return
            }
        }
        newConfig?.let {
            config = it
            saveConfig(it)
        }
        this.merchantConfig = merchantConfig
        this.currency = configResponse.currency
        mConfigUpdateResult.postValue(ConfigUpdateResult.Success(configResponse.currency))
    }

    @UiThread
    fun forgetPassword() {
        config = when (val c = config) {
            is Config.Old -> c.copy(password = "")
            is Config.New -> c.copy(accessToken = "")
        }
        saveConfig(config)
        merchantConfig = null
    }

    @UiThread
    private fun saveConfig(config: Config) {
        when (val c = config) {
            is Config.Old -> prefs.edit()
                .putInt(SETTINGS_CONFIG_VERSION, CONFIG_VERSION_OLD)
                .putString(SETTINGS_CONFIG_URL, c.configUrl)
                .putString(SETTINGS_USERNAME, c.username)
                .putString(SETTINGS_PASSWORD, c.password)
                .putBoolean(SETTINGS_SAVE_PASSWORD, c.savePassword)
                .apply()
            is Config.New -> prefs.edit()
                .putInt(SETTINGS_CONFIG_VERSION, CONFIG_VERSION_NEW)
                .putString(SETTINGS_MERCHANT_URL, c.merchantUrl)
                .putString(SETTINGS_ACCESS_TOKEN, c.accessToken)
                .putBoolean(SETTINGS_SAVE_PASSWORD, c.savePassword)
                .apply()
        }
    }

    private fun onNetworkError(msg: String) = scope.launch(Dispatchers.Main) {
        mConfigUpdateResult.value = ConfigUpdateResult.Error(msg)
    }
}

sealed class ConfigUpdateResult {
    data class Error(val msg: String) : ConfigUpdateResult()
    data class Success(val currency: String) : ConfigUpdateResult()
}
