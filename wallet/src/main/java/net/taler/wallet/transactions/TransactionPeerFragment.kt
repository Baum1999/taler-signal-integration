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

package net.taler.wallet.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.toAbsoluteTime
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.launchInAppBrowser
import net.taler.wallet.peer.TransactionPeerPullCreditComposable
import net.taler.wallet.peer.TransactionPeerPullDebitComposable
import net.taler.wallet.peer.TransactionPeerPushCreditComposable
import net.taler.wallet.peer.TransactionPeerPushDebitComposable

class TransactionPeerFragment : TransactionDetailFragment(), ActionListener {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                val t by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
                t?.let { tx ->
                    TransactionPeerComposable(
                        tx, devMode,
                        balanceManager.getSpecForCurrency(tx.amountRaw.currency),
                        this@TransactionPeerFragment,
                    ) {
                        onTransitionButtonClicked(tx, it)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                transactionManager.selectedTransaction.collect { tx ->
                    val actionBar = (requireActivity() as? AppCompatActivity)
                        ?.supportActionBar
                        ?: return@collect
                    actionBar.title = tx?.getTitle(requireContext())
                }
            }
        }
    }

    override fun onActionButtonClicked(tx: Transaction, type: ActionListener.Type) {
        when (type) {
            ActionListener.Type.COMPLETE_KYC -> {
                val kycUrl = when (tx) {
                    is TransactionPeerPullCredit -> tx.kycUrl
                    is TransactionPeerPushCredit -> tx.kycUrl
                    else -> return
                } ?: return

                launchInAppBrowser(requireContext(), kycUrl)
            }

            else -> {} // does not apply
        }
    }
}

@Composable
fun TransactionPeerComposable(
    t: Transaction,
    devMode: Boolean,
    spec: CurrencySpecification?,
    actionListener: ActionListener,
    onTransition: (t: TransactionAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = CenterHorizontally,
    ) {
        val context = LocalContext.current

        TransactionStateComposable(state = t.txState)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(context).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        when (t) {
            is TransactionPeerPullCredit -> TransactionPeerPullCreditComposable(t, spec, actionListener)
            is TransactionPeerPushCredit -> TransactionPeerPushCreditComposable(t, spec, actionListener)
            is TransactionPeerPullDebit -> TransactionPeerPullDebitComposable(t, spec)
            is TransactionPeerPushDebit -> TransactionPeerPushDebitComposable(t, spec)
            else -> error("unexpected transaction: ${t::class.simpleName}")
        }

        TransitionsComposable(t, devMode, onTransition)

        if (devMode && t.error != null) {
            ErrorTransactionButton(error = t.error!!)
        }

        BottomInsetsSpacer()
    }
}

@Composable
fun TransactionAmountComposable(label: String, amount: Amount, amountType: AmountType) {
    Text(
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
        text = label,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        text = amount.toString(negative = amountType == AmountType.Negative),
        fontSize = 24.sp,
        color = when (amountType) {
            AmountType.Positive -> colorResource(R.color.green)
            AmountType.Negative -> MaterialTheme.colorScheme.error
            AmountType.Neutral -> Color.Unspecified
        },
    )
}

@Composable
fun TransactionInfoComposable(
    label: String,
    info: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Text(
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
        text = label,
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(
        modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = info,
            fontSize = 24.sp,
        )

        trailing?.let { it() }
    }
}
