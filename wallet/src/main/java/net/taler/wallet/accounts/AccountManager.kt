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

package net.taler.wallet.accounts

import android.util.Log
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.taler.wallet.TAG
import net.taler.wallet.accounts.ListBankAccountsResult.Error
import net.taler.wallet.accounts.ListBankAccountsResult.None
import net.taler.wallet.accounts.ListBankAccountsResult.Success
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import org.json.JSONArray

class AccountManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {
    private val mBankAccounts = MutableStateFlow<ListBankAccountsResult>(None)
    internal val bankAccounts = mBankAccounts.asStateFlow()

    @UiThread
    fun listBankAccounts(currency: String? = null) = scope.launch {
        mBankAccounts.value = None
        api.request("listBankAccounts", ListBankAccountsResponse.serializer()) {
            if (currency != null) put("currency", currency)
            this
        }.onError { error ->
            Log.e(TAG, "Error listKnownBankAccounts $error")
            mBankAccounts.value = Error(error)
        }.onSuccess { response ->
            mBankAccounts.value = Success(
                accounts = response.accounts,
                currency = currency,
            )
        }
    }

    suspend fun addBankAccount(
        paytoUri: String,
        label: String,
        currencies: List<String>? = null,
        replaceBankAccountId: String? = null,
        onError: (error: TalerErrorInfo) -> Unit,
    ) {
        api.request<Unit>("addBankAccount") {
            currencies?.let { put("currencies", JSONArray(it)) }
            replaceBankAccountId?.let { put("replaceBankAccountId", it) }
            put("paytoUri", paytoUri)
            put("label", label)
        }.onError { error ->
            Log.e(TAG, "Error addKnownBankAccount $error")
            onError(error)
        }.onSuccess {
            listBankAccounts()
        }
    }

    fun forgetBankAccount(
        id: String,
        onError: (error: TalerErrorInfo) -> Unit,
    ) = scope.launch {
        api.request<Unit>("forgetBankAccount") {
            put("bankAccountId", id)
        }.onError { error ->
            Log.e(TAG, "Error addKnownBankAccount $error")
            onError(error)
        }.onSuccess {
            listBankAccounts()
        }
    }

    suspend fun getBankAccountById(
        id: String,
        onError: (error: TalerErrorInfo) -> Unit,
    ): KnownBankAccountInfo? {
        var response: KnownBankAccountInfo? = null
        api.request("getBankAccountById", KnownBankAccountInfo.serializer()) {
            put("bankAccountId", id)
        }.onError { error ->
            Log.e(TAG, "Error getBankAccountById $error")
            onError(error)
        }.onSuccess {
            response = it
        }

        return response
    }
}
