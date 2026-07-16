/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
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

package net.taler.wallet.contacts

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.taler.common.RelativeTime
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.main.TAG
import org.json.JSONObject

@Serializable
data class ContactEntry(
    val alias: String,
    val aliasType: String,
    val mailboxBaseUri: String,
    val mailboxAddress: String,
    val source: String,
    val petname: String,
)

@Serializable
data class ContactListResponse(
    val contacts: List<ContactEntry>,
)

@Serializable
data class LookupAliasResponse(
    val targetUri: String?,
)

@Serializable
data class TaldirRegisterRequest(
    val alias: String,
    val aliasType: String,
    val taldirBaseUrl: String,
    val targetUri: String,
    val duration: RelativeTime,
)

@Serializable
data class TaldirRegisterResponse(
    val validFor: RelativeTime? = null,
)

sealed class ContactResult {
    data object Loading : ContactResult()
    data class Error(val error: TalerErrorInfo) : ContactResult()
    data class Success(val contacts: List<ContactEntry>) : ContactResult()
}

sealed class LookupResult {
    data object Idle : LookupResult()
    data object Loading : LookupResult()
    data class Error(val error: TalerErrorInfo) : LookupResult()
    data class Success(val targetUri: String, val source: String) : LookupResult()
}

sealed class RegisterResult {
    data object Idle : RegisterResult()
    data object Loading : RegisterResult()
    data class Error(val error: TalerErrorInfo) : RegisterResult()
    data class Success(val validFor: RelativeTime?) : RegisterResult()
    data object CompletionLoading : RegisterResult()
    data object CompletionSuccess : RegisterResult()
}

class ContactManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {
    private val _contacts = MutableStateFlow<ContactResult>(ContactResult.Loading)
    val contacts = _contacts.asStateFlow()

    private val _lookupResult = MutableStateFlow<LookupResult>(LookupResult.Idle)
    val lookupResult = _lookupResult.asStateFlow()

    private val _registerResult = MutableStateFlow<RegisterResult>(RegisterResult.Idle)
    val registerResult = _registerResult.asStateFlow()

    fun resetLookup() {
        _lookupResult.value = LookupResult.Idle
    }

    fun resetRegistration() {
        _registerResult.value = RegisterResult.Idle
    }

    fun loadContacts() {
        _contacts.value = ContactResult.Loading
        scope.launch {
            api.request("getContacts", ContactListResponse.serializer())
                .onSuccess {
                    _contacts.value = ContactResult.Success(it.contacts)
                }
                .onError {
                    Log.e(TAG, "Error getting contacts: $it")
                    _contacts.value = ContactResult.Error(it)
                }
        }
    }

    fun addContact(
        alias: String,
        aliasType: String,
        mailboxBaseUri: String,
        mailboxAddress: String,
        source: String,
        petname: String,
    ) {
        val entry = ContactEntry(
            alias = alias,
            aliasType = aliasType,
            mailboxBaseUri = mailboxBaseUri,
            mailboxAddress = mailboxAddress,
            source = source,
            petname = petname,
        )
        scope.launch {
            api.request<Unit>("addContact") {
                put("contact", JSONObject(BackendManager.json.encodeToString(entry)))
            }.onSuccess {
                loadContacts()
            }.onError {
                Log.e(TAG, "Error adding contact: $it")
            }
        }
    }

    fun taldirLookupAlias(
        alias: String,
        aliasType: String,
        taldirBaseUri: String = "https://taldir.gnunet.org",
    ) {
        _lookupResult.value = LookupResult.Loading
        scope.launch {
            api.request<LookupAliasResponse>("lookupAlias", LookupAliasResponse.serializer()) {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
            }.onSuccess { resp ->
                Log.d(TAG, "Got Taldir target URI for $alias: ${resp.targetUri}")
                val targetUri = resp.targetUri
                if (targetUri.isNullOrBlank()) {
                    _lookupResult.value = LookupResult.Error(TalerErrorInfo(
                        code = TalerErrorCode.UNKNOWN,
                        message = "Alias not found",
                    ))
                } else {
                    _lookupResult.value = LookupResult.Success(targetUri, taldirBaseUri)
                }
            }.onError {
                Log.e(TAG, "Error looking up contact: $it")
                _lookupResult.value = LookupResult.Error(it)
            }
        }
    }

    fun deleteContact(contact: ContactEntry) {
        scope.launch {
            api.request<Unit>("deleteContact") {
                put("contact", JSONObject(BackendManager.json.encodeToString(contact)))
            }.onSuccess {
                loadContacts()
            }.onError {
                Log.e(TAG, "Error deleting contact: $it")
            }
        }
    }

    fun taldirRegister(
        alias: String,
        aliasType: String,
        taldirBaseUri: String = "https://taldir.gnunet.org",
        targetUri: String,
        durationMonths: Int,
    ) {
        val durationUs = durationMonths.toLong() * 30 * 24 * 3600 * 1000000L
        _registerResult.value = RegisterResult.Loading
        scope.launch {
            api.request("registerAlias", TaldirRegisterResponse.serializer()) {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
                put("targetUri", targetUri)
                put("duration", JSONObject(BackendManager.json.encodeToString(RelativeTime.serializer(), RelativeTime(durationUs))))
            }.onSuccess {
                Log.d(TAG, "Taldir registration initiated for $alias: $it")
                _registerResult.value = RegisterResult.Success(it.validFor)
            }.onError {
                Log.e(TAG, "Error initiating Taldir registration: $it")
                _registerResult.value = RegisterResult.Error(it)
            }
        }
    }

    fun completeTaldirRegister(
        alias: String,
        aliasType: String,
        taldirBaseUri: String = "https://taldir.gnunet.org",
        targetUri: String,
        solution: String,
    ) {
        _registerResult.value = RegisterResult.CompletionLoading
        scope.launch {
            api.request<Unit>("completeRegisterAlias") {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
                put("targetUri", targetUri)
                put("challenge", solution)
            }.onSuccess {
                Log.d(TAG, "Taldir registration completed for $alias")
                _registerResult.value = RegisterResult.CompletionSuccess
            }.onError {
                Log.e(TAG, "Error completing Taldir registration: $it")
                _registerResult.value = RegisterResult.Error(it)
            }
        }
    }
}
