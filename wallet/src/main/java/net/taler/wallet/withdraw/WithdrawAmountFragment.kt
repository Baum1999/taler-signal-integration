/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.wallet.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.common.CurrencySpecification
import net.taler.wallet.AmountResult
import net.taler.wallet.AmountResult.InvalidAmount
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.AmountInputField
import net.taler.wallet.compose.DEFAULT_INPUT_DECIMALS
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.getAmount
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.withdraw.WithdrawStatus.Loading
import net.taler.wallet.withdraw.WithdrawStatus.NeedsAmount
import net.taler.wallet.withdraw.WithdrawStatus.NeedsExchange
import net.taler.wallet.withdraw.WithdrawStatus.ReceivedDetails
import net.taler.wallet.withdraw.WithdrawStatus.TosReviewRequired

class WithdrawAmountFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val withdrawManager by lazy { model.withdrawManager }
    private val balanceManager by lazy { model.balanceManager }
    private val exchangeManager by lazy { model.exchangeManager }

    var selected: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {
            val status by withdrawManager.withdrawStatus.observeAsState()
            val coroutineScope = rememberCoroutineScope()
            var defaultExchange by remember { mutableStateOf<ExchangeItem?>(null) }

            TalerSurface {
                when (val s = status) {
                    is Loading -> {
                        LoadingScreen()
                    }

                    else -> {
                        val currency = when(s) {
                            is NeedsAmount -> s.currency
                            is NeedsExchange -> s.currency
                            is ReceivedDetails -> s.currency
                            is TosReviewRequired -> s.currency
                            else -> error("invalid state")
                        }

                        // Find currencySpec for currency or exchange
                        val exchange = defaultExchange
                        val spec = if (exchange?.scopeInfo != null) {
                            balanceManager.getSpecForScopeInfo(exchange.scopeInfo)
                        } else {
                            balanceManager.getSpecForCurrency(currency)
                        }

                        WithdrawAmountComposable(
                            status = s,
                            spec = spec,
                            onCreateAmount = model::createAmount,
                            onSubmit = { amount ->
                                withdrawManager.selectWithdrawalAmount(amount)
                                selected = true
                            }
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                coroutineScope.launch {
                    val s = status
                    if (s is NeedsAmount && s.defaultExchangeBaseUrl != null) {
                        defaultExchange = exchangeManager.findExchangeByUrl(s.defaultExchangeBaseUrl)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        withdrawManager.withdrawStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is Loading -> {}
                is NeedsAmount -> {}
                else -> {
                    if (selected) {
                        findNavController().navigate(R.id.action_withdrawAmount_to_promptWithdraw)
                    }
                }
            }
        }
    }
}

@Composable
fun WithdrawAmountComposable(
    status: WithdrawStatus,
    spec: CurrencySpecification?,
    onCreateAmount: (str: String, currency: String, incoming: Boolean) -> AmountResult,
    onSubmit: (amount: Amount) -> Unit,
) {
    val amount = when (status) {
        is NeedsAmount -> null
        is NeedsExchange -> status.amount
        is ReceivedDetails -> status.amountRaw
        is TosReviewRequired -> status.amountRaw
        else -> error("invalid state")
    }

    val maxAmount = when (status) {
        is NeedsAmount -> status.maxAmount
        is NeedsExchange -> status.maxAmount
        is ReceivedDetails -> status.maxAmount
        is TosReviewRequired -> status.maxAmount
        else -> error("invalid state")
    }

    val currency = when (status) {
        is NeedsAmount -> status.currency
        is NeedsExchange -> status.currency
        is ReceivedDetails -> status.currency
        is TosReviewRequired -> status.currency
        else -> error("invalid state")
    }

    val wireFee = when (status) {
        is NeedsAmount -> status.wireFee
        is NeedsExchange -> status.wireFee
        is ReceivedDetails -> status.wireFee
        is TosReviewRequired -> status.wireFee
        else -> error("invalid state")
    }

    var error by remember { mutableStateOf<String?>(null) }
    var selectedAmount by remember {
        mutableStateOf(amount?.amountStr ?: "0")
    }
    val supportingText = @Composable {
        if (error != null) { Text(error!!) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            // TODO: enable auto focus when AmountInputField handles it correctly
            AmountInputField(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                value = selectedAmount,
                onValueChange = {
                    selectedAmount = it
                },
                label = { Text(stringResource(R.string.amount_withdraw)) },
                supportingText = supportingText,
                isError = error != null,
                numberOfDecimals = spec?.numFractionalInputDigits ?: DEFAULT_INPUT_DECIMALS,
            )

            Text(
                modifier = Modifier,
                text = spec?.symbol ?: currency,
                softWrap = false,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (wireFee != null && !wireFee.isZero()) {
            TransactionAmountComposable(
                label = stringResource(R.string.amount_fee),
                amount = wireFee,
                amountType = AmountType.Negative,
            )

            val selected = try {
                Amount.fromString(
                    currency = currency,
                    str = selectedAmount,
                )
            } catch (_: AmountParserException) { null }

            if (selected != null) {
                TransactionAmountComposable(
                    label = stringResource(R.string.amount_total),
                    amount = selected + wireFee,
                    amountType = AmountType.Positive,
                )
            }
        }

        val context = LocalContext.current

        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                getAmount(currency, selectedAmount)?.let { amount ->
                    // Check that amount doesn't exceed maximum
                    if (maxAmount != null && amount > maxAmount) {
                        error = context.getString(R.string.amount_excess)
                    } else {
                        onSubmit(amount)
                    }
                } ?: let {
                    error = context.getString(R.string.amount_invalid)
                }
            },
        ) {
            Text(stringResource(R.string.withdraw_select_amount))
        }
    }
}

@Preview
@Composable
fun WithdrawAmountComposablePreview() {
    TalerSurface {
        WithdrawAmountComposable(
            status = NeedsAmount(
                talerWithdrawUri = "taler://withdraw/XYZ",
                currency = "KUDOS",
                maxAmount = Amount.fromJSONString("KUDOS:100"),
                wireFee = Amount.fromJSONString("KUDOS:0.2"),
                possibleExchanges = listOf(),
                defaultExchangeBaseUrl = null,
                editableAmount = true,
            ),
            spec = null,
            onCreateAmount = { _, _, _ -> InvalidAmount },
            onSubmit = {},
        )
    }
}