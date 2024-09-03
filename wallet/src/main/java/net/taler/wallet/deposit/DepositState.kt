/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

package net.taler.wallet.deposit

import net.taler.common.Amount
import net.taler.wallet.backend.TalerErrorInfo

sealed class DepositState {
    open val showFees: Boolean = false
    open val totalDepositCost: Amount? = null
    open val effectiveDepositAmount: Amount? = null

    data object Start : DepositState()

    data object CheckingFees : DepositState()

    data class FeesChecked(
        override val totalDepositCost: Amount,
        override val effectiveDepositAmount: Amount,
    ) : DepositState() {
        override val showFees = true
    }

    data class MakingDeposit(
        override val totalDepositCost: Amount,
        override val effectiveDepositAmount: Amount,
    ) : DepositState() {
        override val showFees = true
    }

    data object Success : DepositState()

    data class Error(val error: TalerErrorInfo) : DepositState()

}
