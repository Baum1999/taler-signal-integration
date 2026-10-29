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
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taler.wallet.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import net.taler.wallet.NavigateCallback
import net.taler.wallet.backend.TalerErrorInfo

/**
 * Temporary developer switch between the old and the new main screen.
 *
 * The old implementation is fully contained in [LegacyMainScreen] and its
 * Legacy* composables. Once the new main screen is finished, delete
 * [LegacyMainScreen], the legacy composables, the developer preference and this
 * switch.
 */
@Composable
fun MainScreen(
    model: MainViewModel,
    onNavigate: NavigateCallback,
    onFulfillPayment: (url: String) -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val context = LocalContext.current
    val newMainScreenEnabled by model.settingsManager
        .getNewMainScreenEnabled(context)
        .collectAsState(false)

    if (newMainScreenEnabled) {
        NewMainScreen(
            model = model,
            onNavigate = onNavigate,
            onFulfillPayment = onFulfillPayment,
        )
    } else {
        LegacyMainScreen(
            model = model,
            onNavigate = onNavigate,
            onFulfillPayment = onFulfillPayment,
            onShowError = onShowError,
        )
    }
}
