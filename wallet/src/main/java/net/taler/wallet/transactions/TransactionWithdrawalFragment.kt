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

package net.taler.wallet.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.withdraw.TransactionWithdrawalComposable

class TransactionWithdrawalFragment : TransactionDetailFragment(), ActionListener {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                val t by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
                (t as? TransactionWithdrawal)?.let { tx ->
                    // show QR code only if withdrawal only contains one
                    val qrCode = remember(tx) {
                        (tx.withdrawalDetails as? WithdrawalDetails.ManualTransfer)?.let { details ->
                            if (details.exchangeCreditAccountDetails?.size == 1) {
                                val account0 = details.exchangeCreditAccountDetails[0]
                                val qrCodes = withdrawManager.getQrCodesForPayto(account0.paytoUri)
                                if (qrCodes.size == 1) qrCodes[0]
                                else null
                            } else null
                        }
                    }

                    TransactionWithdrawalComposable(
                        t = tx,
                        devMode = devMode,
                        spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                        qrCode = qrCode,
                        onConfirmBank = { onActionButtonClicked(tx, ActionListener.Type.CONFIRM_WITH_BANK) },
                        onConfirmManual = { onActionButtonClicked(tx, ActionListener.Type.CONFIRM_MANUAL) },
                        onShowQrCodes = { onActionButtonClicked(tx, ActionListener.Type.SHOW_WIRE_QR) },
                    ) {
                        onTransitionButtonClicked(tx, it)
                    }
                }
            }
        }
    }
}
