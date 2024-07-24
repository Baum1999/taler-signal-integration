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
import androidx.compose.material.icons.filled.CurrencyBitcoin
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.compose.AmountInputField
import net.taler.wallet.compose.DEFAULT_INPUT_DECIMALS
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.peer.CheckFeeResult

class SendFundsFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val balanceManager get() = model.balanceManager
    private val peerManager get() = model.peerManager
    private val scopeInfo get() = model.transactionManager.selectedScope ?: error("No scope selected")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                SendFundsIntro(
                    currency = scopeInfo.currency,
                    spec = balanceManager.getSpecForScopeInfo(scopeInfo),
                    checkFees = this@SendFundsFragment::checkFees,
                    onDeposit = this@SendFundsFragment::onDeposit,
                    onPeerPush = this@SendFundsFragment::onPeerPush,
                    onScanQr = this@SendFundsFragment::onScanQr,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(getString(R.string.transactions_send_funds_title, scopeInfo.currency))
    }

    private suspend fun checkFees(amount: Amount): CheckFeeResult {
        return peerManager.checkPeerPushFees(amount)
    }

    private fun onDeposit(amount: Amount) {
        val bundle = bundleOf("amount" to amount.toJSONString())
        findNavController().navigate(R.id.action_sendFunds_to_nav_deposit, bundle)
    }

    private fun onPeerPush(amount: Amount) {
        val bundle = bundleOf("amount" to amount.toJSONString())
        peerManager.checkPeerPushDebit(amount)
        findNavController().navigate(R.id.action_sendFunds_to_nav_peer_push, bundle)
    }

    private fun onScanQr() {
        model.scanCode(ScanQrContext.Send)
    }
}

@Composable
private fun SendFundsIntro(
    currency: String,
    spec: CurrencySpecification?,
    checkFees: suspend (amount: Amount) -> CheckFeeResult,
    onDeposit: (Amount) -> Unit,
    onPeerPush: (Amount) -> Unit,
    onScanQr: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var text by rememberSaveable { mutableStateOf("0") }
        val amount: Amount? = remember(currency, text) {
            getAmount(currency, text)
        }

        var fees by remember { mutableStateOf<CheckFeeResult>(CheckFeeResult.None) }
        val insufficientBalance: Boolean = remember(fees) {
            fees is CheckFeeResult.InsufficientBalance
        }

        val maxAmount = remember(amount, fees) {
            (fees as? CheckFeeResult.InsufficientBalance)?.maxAmountEffective
        }

        val calculateFees = { input: String ->
            fees = CheckFeeResult.None
            getAmount(currency, input)?.let { amount ->
                coroutineScope.launch {
                    checkFees(amount).let {
                        fees = it
                    }
                }
            }
        }

        text.useDebounce(
            delayMillis = 150L,
            coroutineScope = coroutineScope,
        ) {
            calculateFees(it)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 8.dp),
        ) {

            AmountInputField(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                value = text,
                onValueChange = { input ->
                    text = input
                },
                label = { Text(stringResource(R.string.amount_send)) },
                supportingText = {
                    if (insufficientBalance) {
                        if (maxAmount != null) {
                            Text(stringResource(
                                R.string.payment_balance_insufficient_max,
                                maxAmount.withSpec(spec).toString(),
                            ))
                        } else {
                            Text(stringResource(R.string.payment_balance_insufficient))
                        }
                    }
                },
                isError = insufficientBalance,
                numberOfDecimals = spec?.numFractionalInputDigits ?: DEFAULT_INPUT_DECIMALS,
            )

            Text(
                modifier = Modifier,
                text = spec?.symbol ?: currency,
                softWrap = false,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        // Render fees dynamically
        if (fees is CheckFeeResult.Success) {
            val success = fees as CheckFeeResult.Success
            if (success.amountEffective > success.amountRaw) {
                val fee = success.amountEffective - success.amountRaw
                if (!fee.isZero()) {
                    Text(
                        modifier = Modifier.padding(bottom = 16.dp),
                        text = stringResource(id = R.string.payment_fee, fee.withSpec(spec)),
                        softWrap = false,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(R.string.send_intro),
            style = MaterialTheme.typography.titleLarge,
        )

        Column(modifier = Modifier.padding(16.dp)) {

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !insufficientBalance && amount?.isZero() == false,
                onClick = { amount?.let { onDeposit(it) } },
            ) {
                Icon(
                    if (currency == CURRENCY_BTC) {
                        Icons.Default.CurrencyBitcoin
                    } else {
                        Icons.Default.AccountBalance
                    },
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = if (currency == CURRENCY_BTC) {
                    stringResource(R.string.send_deposit_bitcoin)
                } else {
                    stringResource(R.string.send_deposit)
                })
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !insufficientBalance && amount?.isZero() == false,
                onClick = { amount?.let { onPeerPush(it) } },
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = if (currency == CURRENCY_BTC) {
                    stringResource(R.string.send_peer_bitcoin)
                } else {
                    stringResource(R.string.send_peer)
                })
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
fun PreviewSendFundsIntro() {
    Surface {
        SendFundsIntro(
            currency = "TESTKUDOS",
            spec = null,
            checkFees = {
                CheckFeeResult.InsufficientBalance(
                    maxAmountRaw = Amount.fromJSONString("TESTKUDOS:10"),
                    maxAmountEffective = Amount.fromJSONString("TESTKUDOS:10.2"),
                )
            },
            onDeposit = {},
            onScanQr = {},
            onPeerPush = {},
        )
    }
}
