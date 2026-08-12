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

package net.taler.wallet.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import net.taler.common.CurrencySpecification
import net.taler.wallet.balances.ScopeInfo

@Composable
fun rememberCurrencySpec(
    currency: String,
    scopes: List<ScopeInfo>,
    getSpec: suspend (String, List<ScopeInfo>) -> CurrencySpecification?,
): CurrencySpecification? {
    val spec by produceState<CurrencySpecification?>(
        initialValue = null,
        key1 = currency,
        key2 = scopes,
    ) {
        value = getSpec(currency, scopes)
    }
    return spec
}

@Composable
fun rememberCurrencySpec(
    scope: ScopeInfo,
    getSpec: suspend (ScopeInfo) -> CurrencySpecification?,
): CurrencySpecification? {
    val spec by produceState<CurrencySpecification?>(
        initialValue = null,
        key1 = scope,
    ) {
        value = getSpec(scope)
    }
    return spec
}
