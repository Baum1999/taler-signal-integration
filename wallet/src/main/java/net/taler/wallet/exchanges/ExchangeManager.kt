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

package net.taler.wallet.exchanges

import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.taler.common.Event
import net.taler.common.toEvent
import net.taler.wallet.TAG
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.withdraw.TosResponse

@Serializable
data class ExchangeListResponse(
    val exchanges: List<ExchangeItem>,
)

class ExchangeManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {

    private val mProgress = MutableLiveData<Boolean>()
    val progress: LiveData<Boolean> = mProgress

    private val mExchanges = MutableLiveData<List<ExchangeItem>>()
    val exchanges: LiveData<List<ExchangeItem>> get() = list()

    private val mAddError = MutableLiveData<Event<TalerErrorInfo>>()
    val addError: LiveData<Event<TalerErrorInfo>> = mAddError

    private val mListError = MutableLiveData<Event<TalerErrorInfo>>()
    val listError: LiveData<Event<TalerErrorInfo>> = mListError

    private val mDeleteError = MutableLiveData<Event<TalerErrorInfo>>()
    val deleteError: LiveData<Event<TalerErrorInfo>> = mDeleteError

    private val mReloadError = MutableLiveData<Event<TalerErrorInfo>>()
    val reloadError: LiveData<Event<TalerErrorInfo>> = mReloadError

    var withdrawalExchange: ExchangeItem? = null

    private fun list(): LiveData<List<ExchangeItem>> {
        mProgress.value = true
        scope.launch {
            val response = api.request("listExchanges", ExchangeListResponse.serializer())
            response.onError {
                mProgress.value = false
                mListError.value = it.toEvent()
            }.onSuccess {
                Log.d(TAG, "Exchange list: ${it.exchanges}")
                mProgress.value = false
                mExchanges.value = it.exchanges
            }
        }
        return mExchanges
    }

    fun add(exchangeUrl: String) = scope.launch {
        mProgress.value = true
        api.request<Unit>("addExchange") {
            put("exchangeBaseUrl", exchangeUrl)
        }.onError {
            Log.e(TAG, "Error adding exchange: $it")
            mProgress.value = false
            mAddError.value = it.toEvent()
        }.onSuccess {
            mProgress.value = false
            Log.d(TAG, "Exchange $exchangeUrl added")
            list()
        }
    }

    fun reload(exchangeUrl: String, force: Boolean = true) = scope.launch {
        mProgress.value = true
        api.request<Unit>("updateExchangeEntry") {
            put("exchangeBaseUrl", exchangeUrl)
            put("force", force)
        }.onError {
            Log.e(TAG, "Error reloading exchange: $it")
            mProgress.value = false
            mReloadError.value = it.toEvent()
        }.onSuccess {
            mProgress.value = false
            Log.d(TAG, "Exchange $exchangeUrl reloaded")
            list()
        }
    }

    fun delete(exchangeUrl: String, purge: Boolean = false) = scope.launch {
        mProgress.value = true
        api.request<Unit>("deleteExchange") {
            put("exchangeBaseUrl", exchangeUrl)
            put("purge", purge)
        }.onError {
            Log.e(TAG, "Error deleting exchange: $it")
            mProgress.value = false
            mDeleteError.value = it.toEvent()
        }.onSuccess {
            mProgress.value = false
            Log.d(TAG, "Exchange $exchangeUrl deleted")
            list()
        }
    }

    fun findExchangeForCurrency(currency: String): Flow<ExchangeItem?> = flow {
        emit(findExchange(currency))
    }

    fun findExchangeForBaseUrl(url: String): Flow<ExchangeItem?> = flow {
        emit(findExchangeByUrl(url))
    }

    @WorkerThread
    suspend fun findExchange(currency: String): ExchangeItem? {
        var exchange: ExchangeItem? = null
        api.request(
            operation = "listExchanges",
            serializer = ExchangeListResponse.serializer()
        ).onSuccess { exchangeListResponse ->
            // just pick the first for now
            exchange = exchangeListResponse.exchanges.find { it.currency == currency }
        }
        return exchange
    }

    @WorkerThread
    suspend fun findExchangeByUrl(exchangeUrl: String): ExchangeItem? {
        var exchange: ExchangeItem? = null
        api.request("getExchangeEntryByUrl", ExchangeItem.serializer()) {
            put("exchangeBaseUrl", exchangeUrl)
        }.onError {
            Log.e(TAG, "Error getExchangeEntryByUrl: $it")
        }.onSuccess {
            exchange = it
        }
        return exchange
    }

    /**
     * Fetch exchange terms of service.
     */
    suspend fun getExchangeTos(
        exchangeBaseUrl: String,
        language: String? = null,
    ): TosResponse? {
        var result: TosResponse? = null
        api.request("getExchangeTos", TosResponse.serializer()) {
            language?.let { put("acceptLanguage", it) }
            put("exchangeBaseUrl", exchangeBaseUrl)
        }.onError { error ->
            Log.d(TAG, "Error getExchangeTos: $error")
        }.onSuccess {
            result = it
        }
        return result
    }

    /**
     * Accept the currently displayed terms of service.
     */
    suspend fun acceptCurrentTos(
        exchangeBaseUrl: String,
        currentEtag: String,
    ): Boolean {
        var success = false
        api.request<Unit>("setExchangeTosAccepted") {
            put("exchangeBaseUrl", exchangeBaseUrl)
            put("etag", currentEtag)
        }.onError { error ->
            Log.d(TAG, "Error setExchangeTosAccepted: $error")
        }.onSuccess {
            success = true
            // update exchange list
            list()
        }
        return success
    }

    /**
     * Un-accept the terms of service of an exchange
     */
    suspend fun forgetCurrentTos(
        exchangeBaseUrl: String,
        currentEtag: String,
    ): Boolean {
        var success = false
        api.request<Unit>("setExchangeTosForgotten") {
            put("exchangeBaseUrl", exchangeBaseUrl)
            put("etag", currentEtag)
        }.onError { error ->
            Log.d(TAG, "Error setExchangeTosForgotten: $error")
        }.onSuccess {
            success = true
            list()
        }
        return success
    }

    fun addDevExchanges() {
        scope.launch {
            listOf(
                "https://exchange.demo.taler.net/",
                "https://exchange.test.taler.net/",
                "https://exchange.head.taler.net/",
                "https://exchange.taler.ar/",
                "https://exchange.taler.fdold.eu/",
                "https://exchange.taler.grothoff.org/",
            ).forEach { exchangeUrl ->
                add(exchangeUrl)
                delay(100)
            }
            exchanges.value?.let { exs ->
                exs.find {
                    it.exchangeBaseUrl.startsWith("https://exchange.taler.fdold.eu")
                }?.let { fDoldExchange ->
                    api.request<Unit>("addGlobalCurrencyExchange") {
                        put("currency", fDoldExchange.currency)
                        put("exchangeBaseUrl", fDoldExchange.exchangeBaseUrl)
                        put("exchangeMasterPub",
                            "7ER30ZWJEXAG026H5KG9M19NGTFC2DKKFPV79GVXA6DK5DCNSWXG")
                    }.onError {
                        Log.e(TAG, "Error addGlobalCurrencyExchange: $it")
                    }.onSuccess {
                        Log.i(TAG, "fdold is global now!")
                    }
                }
            }
        }
    }

}
