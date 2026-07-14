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

package net.taler.wallet.mailbox

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.taler.common.Timestamp
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.main.TAG
import org.json.JSONObject

@Serializable
data class MailboxConfiguration(
    val mailboxBaseUrl: String,
    val privateKey: String,
    val privateEncryptionKey: String,
    val hAddress: String,
    val expiration: Timestamp,
    val payUri: String? = null,
)

@Serializable
data class MailboxMessageRecord(
    val originMailboxBaseUrl: String,
    val downloadedAt: Timestamp,
    val talerUri: String,
)

@Serializable
data class MailboxMessagesResponse(
    val messages: List<MailboxMessageRecord>,
)

@Serializable
data class GetMailboxResponse(
    val mailboxConfiguration: MailboxConfiguration? = null,
)

sealed class MailboxResult {
    data object Loading : MailboxResult()
    data class Error(val error: TalerErrorInfo) : MailboxResult()
    data class Success(
        val config: MailboxConfiguration,
        val messages: List<MailboxMessageRecord>,
    ) : MailboxResult()
}

class MailboxManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {
    private val _mailbox = MutableStateFlow<MailboxResult>(MailboxResult.Loading)
    val mailbox = _mailbox.asStateFlow()

    fun loadMailbox(baseUrl: String = "https://mailbox.gnunet.org") {
        _mailbox.value = MailboxResult.Loading
        Log.e(TAG, "Loding mailbox")
        scope.launch {
            Log.e(TAG, "Loading mailbox")
            api.request("getMailbox", GetMailboxResponse.serializer()) {
                put("mailboxBaseUrl", baseUrl)
            }.onSuccess { resp ->
                if (null == resp.mailboxConfiguration) {
                    initializeMailbox(baseUrl)
                } else {
                    getMessages(resp.mailboxConfiguration)
                }
            }.onError {
                initializeMailbox(baseUrl)
            }
        }
    }

    private fun initializeMailbox(baseUrl: String) {
        scope.launch {
            api.request("initializeMailbox", MailboxConfiguration.serializer()) {
                put("mailboxBaseUrl", baseUrl)
            }.onSuccess { config ->
                getMessages(config)
            }.onError {
                Log.e(TAG, "Error initializing mailbox: $it")
                _mailbox.value = MailboxResult.Error(it)
            }
        }
    }

    private fun getMessages(config: MailboxConfiguration) {
        scope.launch {
            api.request("getMailboxMessage", MailboxMessagesResponse.serializer())
                .onSuccess { response ->
                    _mailbox.value = MailboxResult.Success(config, response.messages)
                }.onError {
                    Log.e(TAG, "Error getting mailbox messages: $it")
                    _mailbox.value = MailboxResult.Error(it)
                }
        }
    }

    fun refreshMailbox(config: MailboxConfiguration) {
        scope.launch {
            api.request("refreshMailbox", MailboxMessagesResponse.serializer()) {
                JSONObject(BackendManager.json.encodeToString(config))
            }.onSuccess { response ->
                response.messages.forEach { msg ->
                    addMessage(msg)
                }
                getMessages(config)
            }.onError {
                Log.e(TAG, "Error refreshing mailbox: $it")
            }
        }
    }

    private fun addMessage(message: MailboxMessageRecord) {
        scope.launch {
            api.request<Unit>("addMailboxMessage") {
                put("message", JSONObject(BackendManager.json.encodeToString(message)))
            }.onError {
                Log.e(TAG, "Error adding mailbox message: $it")
            }
        }
    }

    fun deleteMessage(config: MailboxConfiguration, message: MailboxMessageRecord) {
        scope.launch {
            api.request<Unit>("deleteMailboxMessage") {
                put("message", JSONObject(BackendManager.json.encodeToString(message)))
            }.onSuccess {
                getMessages(config)
            }.onError {
                Log.e(TAG, "Error deleting mailbox message: $it")
            }
        }
    }
}
