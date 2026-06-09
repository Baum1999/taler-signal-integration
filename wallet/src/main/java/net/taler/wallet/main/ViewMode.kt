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

package net.taler.wallet.main

import net.taler.common.CurrencySpecification
import net.taler.wallet.PrefsViewMode2
import net.taler.wallet.balances.ScopeInfo

data class ViewMode(
    val selectedScope: ScopeInfo? = null,
    val selectedSpec: CurrencySpecification? = null,
) {
    fun toPrefs(): PrefsViewMode2 = PrefsViewMode2
        .newBuilder()
        .apply {
            if (this@ViewMode.selectedScope != null)
                setSelectedScope(this@ViewMode.selectedScope.toPrefs())
        }.build()

    companion object {
        fun fromPrefs(prefs: PrefsViewMode2) = ViewMode(
            selectedScope = prefs.selectedScope?.let { ScopeInfo.fromPrefs(it) },
        )
    }
}