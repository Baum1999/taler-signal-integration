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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.taler.wallet.balances.BalanceItem
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.balances.BalancesComposable
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionsComposable
import net.taler.wallet.transactions.TransactionsResult

@Composable
fun MainComposable(
    innerPadding: PaddingValues,
    state: BalanceState,
    txResult: TransactionsResult,
    viewMode: ViewMode,
    onGetDemoMoneyClicked: () -> Unit,
    onBalanceClicked: (balance: BalanceItem) -> Unit,
    onPendingClicked: (balance: BalanceItem) -> Unit,
    onStatementClicked: (sig: String) -> Unit,
    onTransactionClicked: (tx: Transaction) -> Unit,
    onTransactionsDelete: (txIds: List<String>) -> Unit,
    onShowBalancesClicked: () -> Unit,
) {
    when(viewMode) {
        is ViewMode.Assets -> BalancesComposable(
            innerPadding = innerPadding,
            state = state,
            onGetDemoMoneyClicked = onGetDemoMoneyClicked,
            onBalanceClicked = onBalanceClicked,
            onPendingClicked = onPendingClicked,
            onStatementClicked = onStatementClicked,
        )

        is ViewMode.Transactions -> {
            val balance = remember(state, viewMode) {
                (state as? BalanceState.Success)?.balances?.find {
                    it.scopeInfo == viewMode.selectedScope
                }
            }

            if (balance != null) TransactionsComposable(
                innerPadding = innerPadding,
                viewMode = viewMode,
                balance = balance,
                txResult = txResult,
                onTransactionClick = onTransactionClicked,
                onTransactionsDelete = onTransactionsDelete,
                onShowBalancesClicked = onShowBalancesClicked,
            )
        }
    }
}