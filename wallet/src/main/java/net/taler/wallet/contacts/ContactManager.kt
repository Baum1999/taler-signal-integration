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
import kotlinx.serialization.SerialName
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
    @SerialName("mailboxBaseUrl")
    val mailboxBaseUrl: String,
    val mailboxAddress: String,
    val source: String,
    val petname: String,
    val self: Boolean = false,
    val version: Int = 0,
) {
    val mailboxBaseUri: String get() = mailboxBaseUrl
}

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
    val mailboxBaseUrl: String,
    val mailboxAddress: String,
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
    data class Success(val validFor: RelativeTime?, val targetUri: String) : RegisterResult()
    data object CompletionLoading : RegisterResult()
    data object CompletionSuccess : RegisterResult()
}

sealed class AliasRegistrationStatus {
    data object Checking : AliasRegistrationStatus()
    data class Registered(val targetUri: String) : AliasRegistrationStatus()
    data object NotRegistered : AliasRegistrationStatus()
    data class Mismatched(val targetUri: String) : AliasRegistrationStatus()
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

    private val _aliasStatuses = MutableStateFlow<Map<String, AliasRegistrationStatus>>(emptyMap())
    val aliasStatuses = _aliasStatuses.asStateFlow()

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
        self: Boolean = false,
        version: Int = 0,
    ) {
        val entry = ContactEntry(
            alias = alias,
            aliasType = aliasType,
            mailboxBaseUrl = mailboxBaseUri,
            mailboxAddress = mailboxAddress,
            source = source,
            petname = petname,
            self = self,
            version = version,
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

    fun checkAliasRegistration(
        alias: String,
        aliasType: String,
        currentMailboxBaseUrl: String,
        currentMailboxAddress: String,
        taldirBaseUri: String = "https://taldir.gnunet.org",
    ) {
        val key = "$aliasType:$alias"
        val currentStatuses = _aliasStatuses.value.toMutableMap()
        currentStatuses[key] = AliasRegistrationStatus.Checking
        _aliasStatuses.value = currentStatuses

        scope.launch {
            api.request<LookupAliasResponse>("lookupAlias", LookupAliasResponse.serializer()) {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
            }.onSuccess { resp ->
                val targetUri = resp.targetUri ?: ""
                val cleanBase = currentMailboxBaseUrl.trimEnd('/')
                val expectedPath = "$cleanBase/$currentMailboxAddress"
                val map = _aliasStatuses.value.toMutableMap()
                if (targetUri.isBlank()) {
                    map[key] = AliasRegistrationStatus.NotRegistered
                } else if (targetUri.trimEnd('/') == expectedPath || targetUri.endsWith(currentMailboxAddress)) {
                    map[key] = AliasRegistrationStatus.Registered(targetUri)
                } else {
                    map[key] = AliasRegistrationStatus.Mismatched(targetUri)
                }
                _aliasStatuses.value = map
            }.onError {
                val map = _aliasStatuses.value.toMutableMap()
                map[key] = AliasRegistrationStatus.NotRegistered
                _aliasStatuses.value = map
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
        mailboxBaseUrl: String,
        mailboxAddress: String,
        durationMonths: Int,
    ) {
        val durationUs = durationMonths.toLong() * 30 * 24 * 3600 * 1000000L
        _registerResult.value = RegisterResult.Loading
        scope.launch {
            api.request("registerAlias", TaldirRegisterResponse.serializer()) {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
                put("mailboxBaseUrl", mailboxBaseUrl)
                put("mailboxAddress", mailboxAddress)
                put("duration", JSONObject(BackendManager.json.encodeToString(RelativeTime.serializer(), RelativeTime(durationUs))))
            }.onSuccess {
                Log.d(TAG, "Taldir registration initiated for $alias: $it")
                val targetUri = if (mailboxBaseUrl.isBlank() || mailboxAddress.isBlank()) "" else "$mailboxBaseUrl/$mailboxAddress"
                _registerResult.value = RegisterResult.Success(it.validFor, targetUri)
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
        mailboxBaseUrl: String,
        mailboxAddress: String,
        solution: String,
    ) {
        _registerResult.value = RegisterResult.CompletionLoading
        scope.launch {
            api.request<Unit>("completeRegisterAlias") {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
                put("mailboxBaseUrl", mailboxBaseUrl)
                put("mailboxAddress", mailboxAddress)
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

    fun taldirDeleteAlias(
        alias: String,
        aliasType: String,
        taldirBaseUri: String = "https://taldir.gnunet.org",
        mailboxBaseUrl: String = "",
        mailboxAddress: String = "",
        durationMonths: Int = 12,
    ) {
        taldirRegister(
            alias = alias,
            aliasType = aliasType,
            taldirBaseUri = taldirBaseUri,
            mailboxBaseUrl = mailboxBaseUrl,
            mailboxAddress = mailboxAddress,
            durationMonths = durationMonths,
        )
    }

    fun recoverRegistration(
        alias: String,
        aliasType: String,
        taldirBaseUri: String = "https://taldir.gnunet.org",
        expectedTargetUri: String,
    ) {
        _registerResult.value = RegisterResult.Loading
        scope.launch {
            api.request<LookupAliasResponse>("lookupAlias", LookupAliasResponse.serializer()) {
                put("alias", alias)
                put("aliasType", aliasType)
                put("taldirBaseUrl", taldirBaseUri)
            }.onSuccess { resp ->
                val targetUri = resp.targetUri ?: ""
                if (expectedTargetUri.isEmpty()) {
                    if (targetUri.isBlank()) {
                        _registerResult.value = RegisterResult.CompletionSuccess
                    } else {
                        _registerResult.value = RegisterResult.Error(TalerErrorInfo(
                            code = TalerErrorCode.UNKNOWN,
                            message = "Existing registration is not deleted (points to $targetUri)",
                        ))
                    }
                } else {
                    if (targetUri.isNullOrBlank()) {
                        _registerResult.value = RegisterResult.Error(TalerErrorInfo(
                            code = TalerErrorCode.UNKNOWN,
                            message = "Alias not found",
                        ))
                    } else if (targetUri != expectedTargetUri) {
                        _registerResult.value = RegisterResult.Error(TalerErrorInfo(
                            code = TalerErrorCode.UNKNOWN,
                            message = "Existing registration points to a different mailbox URI",
                        ))
                    } else {
                        _registerResult.value = RegisterResult.CompletionSuccess
                    }
                }
            }.onError {
                Log.e(TAG, "Error recovering registration: $it")
                if (expectedTargetUri.isEmpty()) {
                    // When unlinking, lookup error (404 Alias not found) indicates successful unlinking
                    _registerResult.value = RegisterResult.CompletionSuccess
                } else {
                    _registerResult.value = RegisterResult.Error(it)
                }
            }
        }
    }
}
