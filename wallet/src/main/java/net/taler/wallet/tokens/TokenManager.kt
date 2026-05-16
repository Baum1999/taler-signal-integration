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

package net.taler.wallet.tokens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import net.taler.common.Timestamp
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletResponse

sealed class UiState<out T> {
    object Loading: UiState<Nothing>()
    data class Success<out T>(val data: T): UiState<T>()
    data class Error(val error: TalerErrorInfo): UiState<Nothing>()
}

fun <T, U> WalletResponse<T>.toUiState(transform: (r: T) -> U): UiState<U> = when(this) {
    is WalletResponse.Success -> UiState.Success(transform(this.result))
    is WalletResponse.Error -> UiState.Error(this.error)
}

sealed class TokenFilter() {
    // data object All: TokenFilter()
    data object Valid: TokenFilter()
    data object Expired: TokenFilter()
}

class TokenManager(private val api: WalletBackendApi){
    private val refreshTrigger =
        MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val discounts: Flow<UiState<List<DiscountListDetail>>> = refreshTrigger.flatMapLatest {
        flow {
            emit(UiState.Loading)
            emit(api.request("listDiscounts", ListDiscountsResponse.serializer())
                .toUiState { it.discounts })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val passes: Flow<UiState<List<SubscriptionListDetail>>> = refreshTrigger.flatMapLatest {
        flow {
            emit(UiState.Loading)
            emit(api.request("listSubscriptions", ListSubscriptionsResponse.serializer())
                .toUiState { it.subscriptions })
        }
    }

    fun refresh() { refreshTrigger.tryEmit(Unit) }
}

fun List<DiscountListDetail>.filterDiscounts(filter: TokenFilter): List<DiscountListDetail> {
    return when(filter) {
        TokenFilter.Valid -> filter { !it.isExpired }
        TokenFilter.Expired -> filter { it.isExpired }
    }
}

fun List<SubscriptionListDetail>.filterPasses(filter: TokenFilter): List<SubscriptionListDetail> {
    return when(filter) {
        TokenFilter.Valid -> filter { !it.isExpired }
        TokenFilter.Expired -> filter { it.isExpired }
    }
}