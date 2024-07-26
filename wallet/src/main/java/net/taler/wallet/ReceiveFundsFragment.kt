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

package net.taler.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.compose.AmountInputField
import net.taler.wallet.compose.DEFAULT_INPUT_DECIMALS
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.withdraw.WithdrawalDetailsForUri

class ReceiveFundsFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val exchangeManager get() = model.exchangeManager
    private val withdrawManager get() = model.withdrawManager
    private val balanceManager get() = model.balanceManager
    private val peerManager get() = model.peerManager
    private val scopeInfo get() = model.transactionManager.selectedScope ?: error("No scope selected")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                ReceiveFundsIntro(
                    scopeInfo.currency,
                    balanceManager.getSpecForScopeInfo(scopeInfo),
                    this@ReceiveFundsFragment::onManualWithdraw,
                    this@ReceiveFundsFragment::onPeerPull,
                    this@ReceiveFundsFragment::onScanQr,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(getString(R.string.transactions_receive_funds_title, scopeInfo.currency))
    }

    private fun onManualWithdraw(amount: Amount) {
        // TODO give some UI feedback while we wait for exchanges to load (quick enough for now)
        lifecycleScope.launchWhenResumed {
            // we need to set the exchange first, we want to withdraw from
            exchangeManager.findExchangeForCurrency(amount.currency).collect { exchange ->
                onExchangeRetrieved(exchange, amount)
            }
        }
    }

    private fun onExchangeRetrieved(exchange: ExchangeItem?, amount: Amount) {
        if (exchange == null) {
            Toast.makeText(requireContext(), "No exchange available", LENGTH_LONG).show()
            return
        }

        // now that we have the exchange, we can navigate
        exchangeManager.withdrawalExchange = exchange
        withdrawManager.resetWithdrawal()
        withdrawManager.getWithdrawalDetails(
            exchangeBaseUrl = exchange.exchangeBaseUrl,
            uriInfo = WithdrawalDetailsForUri(
                amount = amount,
                currency = amount.currency,
            ),
            amount = amount,
        )
        findNavController().navigate(R.id.action_receiveFunds_to_nav_prompt_withdraw)
    }

    private fun onPeerPull(amount: Amount) {
        val bundle = bundleOf("amount" to amount.toJSONString())
        peerManager.checkPeerPullCredit(amount)
        findNavController().navigate(R.id.action_receiveFunds_to_nav_peer_pull, bundle)
    }

    private fun onScanQr() {
        model.scanCode(ScanQrContext.Receive)
    }
}

@Composable
private fun ReceiveFundsIntro(
    currency: String,
    spec: CurrencySpecification?,
    onManualWithdraw: (Amount) -> Unit,
    onPeerPull: (Amount) -> Unit,
    onScanQr: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var text by rememberSaveable { mutableStateOf("0") }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp),
        ) {
            AmountInputField(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                value = text,
                onValueChange = { input ->
                    text = input
                },
                label = { Text(stringResource(R.string.amount_receive)) },
                numberOfDecimals = spec?.numFractionalInputDigits ?: DEFAULT_INPUT_DECIMALS,
            )
            Text(
                modifier = Modifier,
                text = spec?.symbol ?: currency,
                softWrap = false,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.receive_intro),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(modifier = Modifier.padding(16.dp)) {
            val amount: Amount? = remember(currency, text) {
                getAmount(currency, text)
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = amount?.isZero() == false,
                onClick = {
                    amount?.let { onManualWithdraw(it) }
                },
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.receive_withdraw))
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = amount?.isZero() == false,
                onClick = {
                    amount?.let { onPeerPull(it) }
                },
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.receive_peer))
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                text = stringResource(id = R.string.or),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onScanQr() },
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.button_scan_qr_code_label))
            }
        }
    }
}

@Preview
@Composable
fun PreviewReceiveFundsIntro() {
    Surface {
        ReceiveFundsIntro("TESTKUDOS", null, {}, {}) {}
    }
}
