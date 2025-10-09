/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.wallet.donau

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.taler.wallet.TAG
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi

class DonauManager(
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
) {
    private val mDonauStatus = MutableStateFlow<GetDonauStatus>(GetDonauStatus.None)
    val donauStatus = mDonauStatus.asStateFlow()

    fun setDonau(
        info: DonauInfo,
        onSuccess: () -> Unit,
        onError: (error: TalerErrorInfo) -> Unit,
    ) = scope.launch {
        api.request<Unit>("setDonau") {
            put("donauBaseUrl", info.donauBaseUrl)
            put("taxPayerId", info.taxPayerId)
        }.onError { error ->
            Log.e(TAG, "Error setDonau $error")
            onError(error)
        }.onSuccess {
            getDonau()
            onSuccess()
        }
    }

    fun getDonau() = scope.launch {
        mDonauStatus.value = GetDonauStatus.Loading
        api.request("getDonau", GetDonauResponse.serializer())
            .onError { error ->
                Log.e(TAG, "Error getDonau $error")
                mDonauStatus.value = GetDonauStatus.Error(error)
            }.onSuccess { res ->
                mDonauStatus.value = GetDonauStatus.Success(res.currentDonauInfo)
            }
    }
}