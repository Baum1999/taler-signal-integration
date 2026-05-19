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
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import net.taler.common.ChallengeConfirmRequest
import net.taler.common.ChallengesResponse
import net.taler.common.CurrencySpecification
import net.taler.common.TokenDuration
import net.taler.common.TokenRequest
import net.taler.common.Version
import net.taler.merchantlib.MerchantConfig
import net.taler.merchantpos.BuildConfig
import net.taler.merchantpos.R
import net.taler.lib.android.getIncompatibleStringOrNull

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
private const val SETTINGS_INITIAL_ORDER_SCREEN = "initialOrderScreen"
private const val SETTINGS_CACHED_RUNTIME_CONFIG = "cachedRuntimeConfig"

internal const val NEW_CONFIG_URL_DEMO = "my.taler-ops.ch"

private val VERSION = Version.parse(BuildConfig.BACKEND_API_VERSION)!!
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val TAG = ConfigManager::class.java.simpleName

enum class InitialOrderScreen(val prefValue: String) {
    AmountEntry("amountEntry"),
    Inventory("inventory");

    companion object {
        fun fromPrefValue(value: String?): InitialOrderScreen {
            return entries.firstOrNull { it.prefValue == value } ?: AmountEntry
        }
    }
}

@kotlinx.serialization.Serializable
private data class MerchantBackendConfigResponse(
    val version: String,
    val currency: String,
    val currencies: Map<String, CurrencySpecification> = emptyMap(),
)

@Serializable
private data class CachedRuntimeConfig(
    val posConfig: PosConfig,
    val merchantConfig: MerchantConfig,
    val currency: String,
    val currencySpec: CurrencySpecification? = null,
)

/* -- Limited access token -- */
@kotlinx.serialization.Serializable
private data class LimitedTokenResponse(
    val token: String,
    val scope: String,
    val refreshable: Boolean,
    // Using Unit here as a placeholder if we don't need the actual expiration object
    // or we could use net.taler.common.TokenExpiration
    val expiration: kotlinx.serialization.json.JsonElement
)

class ChallengeRequiredException(val challengeResponse: ChallengesResponse) : Exception()

/* -- Limited access token END -- */


interface ConfigurationReceiver {
    /**
     * Returns null if the configuration was valid, or a error string for user display otherwise.
     */
    suspend fun onConfigurationReceived(
        posConfig: PosConfig,
        currency: String,
        currencySpec: CurrencySpecification?,
    ): String?

    suspend fun onInventoryUpdated(
        posConfig: PosConfig,
        currency: String,
        currencySpec: CurrencySpecification?,
    ): String? = onConfigurationReceived(posConfig, currency, currencySpec)
}

class ConfigManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
) {

    private val _sessionExpired = MutableLiveData<Unit>()
    val sessionExpired: LiveData<Unit> = _sessionExpired

    private val prefs = context.getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
    private val configurationReceivers = ArrayList<ConfigurationReceiver>()
    private var inventoryRefreshJob: Job? = null
    private var cachedRuntimeConfig: CachedRuntimeConfig? = null

    init {
        migrateLegacyPrefsIfNeeded()
    }

    @Volatile
    var config: Config =
        Config.New(
            merchantUrl = prefs.getString(SETTINGS_MERCHANT_URL, "")!!,
            accessToken = prefs.getString(SETTINGS_ACCESS_TOKEN, "")!!,
            savePassword = prefs.getBoolean(SETTINGS_SAVE_PASSWORD, true),
        )

    init {
        restoreCachedRuntimeConfig()
    }

    @Volatile
    var merchantConfig: MerchantConfig? = null
        private set

    @Volatile
    var currency: String? = null
        private set

    @Volatile
    var currencySpec: CurrencySpecification? = null
        private set

    private val mInitialOrderScreen = MutableLiveData(
        InitialOrderScreen.fromPrefValue(
            prefs.getString(SETTINGS_INITIAL_ORDER_SCREEN, InitialOrderScreen.AmountEntry.prefValue),
        )
    )
    val initialOrderScreenLiveData: LiveData<InitialOrderScreen> = mInitialOrderScreen

    var initialOrderScreen: InitialOrderScreen
        get() = mInitialOrderScreen.value ?: InitialOrderScreen.AmountEntry
        set(value) {
            prefs.edit()
                .putString(SETTINGS_INITIAL_ORDER_SCREEN, value.prefValue)
                .apply()
            mInitialOrderScreen.value = value
        }

    private val mConfigUpdateResult = MutableLiveData<ConfigUpdateResult?>()
    val configUpdateResult: LiveData<ConfigUpdateResult?> = mConfigUpdateResult

    fun addConfigurationReceiver(receiver: ConfigurationReceiver) {
        configurationReceivers.add(receiver)
        cachedRuntimeConfig?.let { cached ->
            scope.launch {
                receiver.onConfigurationReceived(cached.posConfig, cached.currency, cached.currencySpec)
            }
        }
    }

    internal fun debugApplyFixture(
        posConfig: PosConfig,
        merchantConfig: MerchantConfig,
        currency: String,
        currencySpec: CurrencySpecification? = null,
        initialOrderScreen: InitialOrderScreen = InitialOrderScreen.Inventory,
    ) {
        config = Config.New(
            merchantUrl = merchantConfig.baseUrl,
            accessToken = "",
            savePassword = false,
        )
        this.merchantConfig = merchantConfig
        this.currency = currency
        this.currencySpec = currencySpec
        this.initialOrderScreen = initialOrderScreen

        val snapshot = CachedRuntimeConfig(
            posConfig = posConfig,
            merchantConfig = merchantConfig,
            currency = currency,
            currencySpec = currencySpec,
        )
        saveCachedRuntimeConfig(snapshot)
        runBlocking {
            configurationReceivers.forEach { receiver ->
                receiver.onConfigurationReceived(posConfig, currency, currencySpec)
            }
        }
    }

    private fun migrateLegacyPrefsIfNeeded() {
        val legacyVersion = prefs.getInt(SETTINGS_CONFIG_VERSION, CONFIG_VERSION_NEW)
        if (legacyVersion == CONFIG_VERSION_OLD) {
            prefs.edit().clear().apply()
        }
    }

    @UiThread
    fun reloadConfig() {
        fetchConfig(config, save = true, inventoryOnly = false, silent = false)
    }

    @UiThread
    fun refreshConfigInBackground() {
        if (!config.isValid() || !config.hasPassword()) return
        fetchConfig(config, save = false, inventoryOnly = false, silent = true)
    }

    @UiThread
    fun refreshInventory() {
        inventoryRefreshJob?.cancel()
        inventoryRefreshJob = scope.launch {
            delay(350)
            fetchConfig(config, save = false, inventoryOnly = true, silent = true)
        }
    }

    @UiThread
    fun fetchConfig(config: Config, save: Boolean) {
        fetchConfig(config, save, inventoryOnly = false, silent = false)
    }

    @UiThread
    private fun fetchConfig(
        config: Config,
        save: Boolean,
        inventoryOnly: Boolean,
        silent: Boolean,
    ) {
        if (!silent) mConfigUpdateResult.value = null
        val configToSave = if (save) {
            if (config.savePassword()) config else when (val c = config) {
                //is Config.Old -> c.copy(password = "")
                is Config.New -> c.copy(accessToken = "")
            }
        } else null

        scope.launch(Dispatchers.IO) {
            try {
                val url = when(val c = config) {
                    //is Config.Old -> c.configUrl
                    is Config.New -> c.merchantUrl.toUri()
                        .buildUpon()
                        .appendPath("private/pos")
                        .build()
                        .toString()
                }

                // get PoS configuration
                val posConfig: PosConfig = httpClient.get(url) {
                    when (val c = config) {
                        is Config.New -> {
                            val token = "secret-token:${c.accessToken}"
                            val auth = ("Bearer $token")
                            header(Authorization, auth)
                        }
                    }
                }.body()

                val merchantConfig = when (val c = config) {
                    //is Config.Old -> posConfig.merchantConfig!!
                    is Config.New -> MerchantConfig(c.merchantUrl, "secret-token:${c.accessToken}")
                }

                val backendConfig: MerchantBackendConfigResponse =
                    httpClient.get(merchantConfig.urlFor("config")).body()
                onMerchantConfigReceived(
                    configToSave,
                    posConfig,
                    merchantConfig,
                    backendConfig,
                    inventoryOnly,
                    silent,
                )
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
                if (!silent) onNetworkError(msg)
            }
        }
    }

    @WorkerThread
    private suspend fun onMerchantConfigReceived(
        newConfig: Config?,
        posConfig: PosConfig,
        merchantConfig: MerchantConfig,
        configResponse: MerchantBackendConfigResponse,
        inventoryOnly: Boolean,
        silent: Boolean,
    ) {
        val versionIncompatible =
            VERSION.getIncompatibleStringOrNull(context, configResponse.version)
        if (versionIncompatible != null) {
            Log.e(TAG, "Versions incompatible $configResponse")
            if (!silent) mConfigUpdateResult.postValue(ConfigUpdateResult.Error(versionIncompatible))
            return
        }
        val currencySpec = configResponse.currencies[configResponse.currency]
        for (receiver in configurationReceivers) {
            val result = try {
                if (inventoryOnly) {
                    receiver.onInventoryUpdated(posConfig, configResponse.currency, currencySpec)
                } else {
                    receiver.onConfigurationReceived(posConfig, configResponse.currency, currencySpec)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling configuration by ${receiver::class.java.simpleName}", e)
                context.getString(R.string.config_error_unknown)
            }
            if (result != null) { // error
                if (!silent) mConfigUpdateResult.postValue(ConfigUpdateResult.Error(result))
                return
            }
        }
        withContext(Dispatchers.Main) {
            newConfig?.let {
                config = it
                saveConfig(it)
            }
            this@ConfigManager.merchantConfig = merchantConfig
            this@ConfigManager.currency = configResponse.currency
            this@ConfigManager.currencySpec = currencySpec
            saveCachedRuntimeConfig(
                CachedRuntimeConfig(
                    posConfig = posConfig,
                    merchantConfig = merchantConfig,
                    currency = configResponse.currency,
                    currencySpec = currencySpec,
                )
            )
            if (!silent) {
                mConfigUpdateResult.value = ConfigUpdateResult.Success(configResponse.currency)
            }
        }
    }

    /**
     * POSTs to /instances/{username}/private/token with the user’s raw secret,
     * returns the new “write” token (without the “secret-token:” prefix).
     */
    @WorkerThread
    suspend fun fetchLimitedAccessToken(
        baseUrl: String,
        username: String,
        initialSecret: String,
        duration: TokenDuration,
        challengeIds: List<String> = emptyList()
    ): String {
        val tokenUrl = baseUrl.toUri()
            .buildUpon()
            .appendPath("instances")
            .appendPath(username)
            .appendPath("private")
            .appendPath("token")
            .build()
            .toString()

        val bearer = "Bearer secret-token:$initialSecret"
        val response = httpClient.post(tokenUrl) {
            header(HttpHeaders.Authorization, bearer)
            if (challengeIds.isNotEmpty()) {
                header("Taler-Challenge-Ids", challengeIds.joinToString(","))
            }
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(scope = "write", duration = duration))
        }

        if (response.status == HttpStatusCode.Accepted) {
            val challenge: ChallengesResponse = response.body()
            throw ChallengeRequiredException(challenge)
        }

        val resp: LimitedTokenResponse = response.body()

        return resp.token.removePrefix("secret-token:")
    }

    suspend fun requestChallenge(
        baseUrl: String,
        username: String,
        challengeId: String
    ) {
        val challengeUrl = baseUrl.toUri()
            .buildUpon()
            .appendPath("instances")
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
    ) {
        val confirmUrl = baseUrl.toUri()
            .buildUpon()
            .appendPath("instances")
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

    @UiThread
    fun forgetPassword() {
        config = when (val c = config) {
            //is Config.Old -> c.copy(password = "")
            is Config.New -> c.copy(accessToken = "")
        }
        saveConfig(config)
        clearCachedRuntimeConfig()
        merchantConfig = null
        currency = null
        currencySpec = null
    }

    @UiThread
    fun logout() {
        inventoryRefreshJob?.cancel()
        val savePassword = config.savePassword()
        config = Config.New(
            merchantUrl = "",
            accessToken = "",
            savePassword = savePassword,
        )
        saveConfig(config)
        clearCachedRuntimeConfig()
        merchantConfig = null
        currency = null
        currencySpec = null
        mConfigUpdateResult.value = null
    }

    @UiThread
    private fun saveConfig(config: Config) {
        when (val c = config) {
//            is Config.Old -> prefs.edit()
//                .putInt(SETTINGS_CONFIG_VERSION, CONFIG_VERSION_OLD)
//                .putString(SETTINGS_CONFIG_URL, c.configUrl)
//                .putString(SETTINGS_USERNAME, c.username)
//                .putString(SETTINGS_PASSWORD, c.password)
//                .putBoolean(SETTINGS_SAVE_PASSWORD, c.savePassword)
//                .apply()
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

    private fun restoreCachedRuntimeConfig() {
        if (!config.isValid() || !config.hasPassword()) {
            clearCachedRuntimeConfig()
            return
        }
        val encoded = prefs.getString(SETTINGS_CACHED_RUNTIME_CONFIG, null) ?: return
        val restored = runCatching {
            json.decodeFromString<CachedRuntimeConfig>(encoded)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to restore cached runtime config", error)
            clearCachedRuntimeConfig()
            return
        }
        cachedRuntimeConfig = restored
        merchantConfig = restored.merchantConfig
        currency = restored.currency
        currencySpec = restored.currencySpec
    }

    private fun saveCachedRuntimeConfig(snapshot: CachedRuntimeConfig) {
        cachedRuntimeConfig = snapshot
        prefs.edit()
            .putString(SETTINGS_CACHED_RUNTIME_CONFIG, json.encodeToString(CachedRuntimeConfig.serializer(), snapshot))
            .apply()
    }

    private fun clearCachedRuntimeConfig() {
        cachedRuntimeConfig = null
        prefs.edit().remove(SETTINGS_CACHED_RUNTIME_CONFIG).apply()
    }

    internal fun notifySessionExpired() {
        // do it on the Main thread
        _sessionExpired.postValue(Unit)
    }
}

sealed class ConfigUpdateResult {
    data class Error(val msg: String) : ConfigUpdateResult()
    data class Success(val currency: String) : ConfigUpdateResult()
}
