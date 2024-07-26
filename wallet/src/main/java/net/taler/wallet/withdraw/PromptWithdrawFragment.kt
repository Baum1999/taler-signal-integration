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
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.EventObserver
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.AmountInputField
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.compose.DEFAULT_INPUT_DECIMALS
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.exchanges.ExchangeItem
import net.taler.wallet.exchanges.SelectExchangeDialogFragment
import net.taler.wallet.getAmount
import net.taler.wallet.showError
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.useDebounce
import net.taler.wallet.withdraw.WithdrawStatus.Status.*

class PromptWithdrawFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val withdrawManager by lazy { model.withdrawManager }
    private val transactionManager by lazy { model.transactionManager }
    private val exchangeManager by lazy { model.exchangeManager }
    private val balanceManager by lazy { model.balanceManager }

    private val selectExchangeDialog = SelectExchangeDialogFragment()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {
            val status by withdrawManager.withdrawStatus.collectAsStateLifecycleAware()
            val coroutineScope = rememberCoroutineScope()
            var defaultExchange by remember { mutableStateOf<ExchangeItem?>(null) }

            TalerSurface {
                status.let { s ->
                    if (s.error != null) {
                        WithdrawalError(error = s.error)
                        return@let
                    }

                    when (s.status) {
                        None, Loading, TosReviewRequired -> LoadingScreen()

                        InfoReceived, Updating -> {
                            val spec = remember(s) {
                                defaultExchange?.scopeInfo?.let { scopeInfo ->
                                    balanceManager.getSpecForScopeInfo(scopeInfo)
                                } ?: balanceManager.getSpecForCurrency(s.currency!!)
                            }

                            WithdrawalShowInfo(
                                status = s,
                                currency = s.currency!!,
                                spec = spec,
                                onSelectExchange = {
                                    selectExchange()
                                },
                                onSelectAmount = { amount ->
                                    withdrawManager.getWithdrawalDetails(
                                        amount = amount,
                                        exchangeBaseUrl = s.exchangeBaseUrl!!,
                                        loading = false,
                                    )
                                },
                                onConfirm = { age ->
                                    withdrawManager.acceptWithdrawal(age)
                                }
                            )
                        }
                        else -> {}
                    }
                }
            }

            LaunchedEffect(Unit) {
                coroutineScope.launch {
                    val s = status
                    if (s.uriInfo?.amount == null && s.uriInfo?.defaultExchangeBaseUrl != null) {
                        defaultExchange = exchangeManager.findExchangeByUrl(s.uriInfo.defaultExchangeBaseUrl)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                withdrawManager.withdrawStatus.collect { status ->
                    if (status.error != null) {
                        showError(status.error)
                    }

                    if (status.exchangeBaseUrl == null
                        && selectExchangeDialog.dialog?.isShowing != true) {
                        selectExchange()
                    }

                    when (status.status) {
                        // TODO: rewrite ToS review screen in compose
                        TosReviewRequired -> {
                            findNavController().navigate(
                                R.id.action_promptWithdraw_to_reviewExchangeTOS,
                            )
                        }

                        ManualTransferRequired -> {
                            findNavController().navigate(
                                R.id.action_promptWithdraw_to_nav_exchange_manual_withdrawal_success,
                            )
                        }

                        Success -> lifecycleScope.launch {
                            Snackbar.make(requireView(), R.string.withdraw_initiated, LENGTH_LONG).show()
                            if (transactionManager.selectTransaction(status.transactionId!!)) {
                                findNavController().navigate(R.id.action_promptWithdraw_to_nav_transactions_detail_withdrawal)
                            } else {
                                findNavController().navigate(R.id.action_promptWithdraw_to_nav_main)
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

        selectExchangeDialog.exchangeSelection.observe(viewLifecycleOwner, EventObserver {
            onExchangeSelected(it)
        })
    }

    private fun selectExchange() {
        val exchanges = withdrawManager.withdrawStatus.value.uriInfo?.possibleExchanges ?: return
        selectExchangeDialog.setExchanges(exchanges)
        selectExchangeDialog.show(parentFragmentManager, "SELECT_EXCHANGE")
    }

    private fun onExchangeSelected(exchange: ExchangeItem) {
        withdrawManager.getWithdrawalDetails(
            exchangeBaseUrl = exchange.exchangeBaseUrl,
        )
    }
}

@Composable
fun WithdrawalShowInfo(
    status: WithdrawStatus,
    currency: String,
    spec: CurrencySpecification?,
    onSelectAmount: (amount: Amount) -> Unit,
    onSelectExchange: () -> Unit,
    onConfirm: (age: Int?) -> Unit,
) {
    val defaultAmount = status.uriInfo?.amount
    val maxAmount = status.uriInfo?.maxAmount
    val editableAmount = status.uriInfo?.editableAmount ?: false
    val wireFee = status.uriInfo?.wireFee ?: Amount.zero(currency)
    val exchange = status.exchangeBaseUrl
    val possibleExchanges = status.uriInfo?.possibleExchanges ?: emptyList()
    val ageRestrictionOptions = status.amountInfo?.ageRestrictionOptions ?: emptyList()

    var selectedAmount by remember { mutableStateOf(defaultAmount) }
    var selectedAge by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    selectedAmount.useDebounce {
        it?.let { amount ->
            if (editableAmount) {
                onSelectAmount(amount)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (editableAmount) {
                WithdrawAmountComposable(
                    defaultAmount = defaultAmount?.withSpec(spec),
                    maxAmount = maxAmount?.withSpec(spec),
                    currency = currency,
                    spec = spec,
                    onAmountChanged = { amount, err ->
                        selectedAmount = amount
                        error = err
                    }
                )
            } else {
                selectedAmount?.let { amount ->
                    TransactionAmountComposable(
                        label = if (wireFee.isZero()) {
                            stringResource(R.string.amount_total)
                        } else {
                            stringResource(R.string.amount_chosen)
                        },
                        amount = amount,
                        amountType = if (wireFee.isZero()) {
                            AmountType.Positive
                        } else {
                            AmountType.Neutral
                        },
                    )
                }
            }

            if (!wireFee.isZero()) {
                TransactionAmountComposable(
                    label = stringResource(R.string.amount_fee),
                    amount = wireFee,
                    amountType = AmountType.Negative,
                )

                selectedAmount?.let { amount ->
                    TransactionAmountComposable(
                        label = stringResource(R.string.amount_total),
                        amount = amount + wireFee,
                        amountType = AmountType.Positive,
                    )
                }
            }

            exchange?.let {
                TransactionInfoComposable(
                    label = stringResource(R.string.withdraw_exchange),
                    info = cleanExchange(it),
                    trailing = {
                        if (possibleExchanges.size > 1) {
                            IconButton(
                                modifier = Modifier.padding(start = 8.dp),
                                onClick = { onSelectExchange() },
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                )
                            }
                        }
                    },
                )
            }

            var expanded by remember { mutableStateOf(false) }

            if (ageRestrictionOptions.isNotEmpty()) {
                TransactionInfoComposable(
                    label = stringResource(R.string.withdraw_restrict_age),
                    info = selectedAge?.toString()
                        ?: stringResource(R.string.withdraw_restrict_age_unrestricted)
                ) {
                    IconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.edit),
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.withdraw_restrict_age_unrestricted)) },
                            onClick = {
                                selectedAge = null
                                expanded = false
                            },
                        )

                        ageRestrictionOptions.forEach { age ->
                            DropdownMenuItem(
                                text = { Text(age.toString()) },
                                onClick = {
                                    selectedAge = age
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        BottomButtonBox(Modifier.fillMaxWidth()) {
            Button(
                enabled = !error
                        && status.status != Updating
                        && selectedAmount?.let { !it.isZero() } == true,
                onClick = {
                    selectedAmount?.let { onConfirm(selectedAge) }
                },
            ) {
                if (status.status == Updating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Text(stringResource(R.string.withdraw_button_confirm))
                }
            }
        }
    }
}

@Composable
fun WithdrawalError(
    error: TalerErrorInfo,
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        contentAlignment = Center,
    ) {
        Text(
            text = error.userFacingMsg,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun WithdrawAmountComposable(
    defaultAmount: Amount?,
    maxAmount: Amount?,
    currency: String,
    spec: CurrencySpecification?,
    onAmountChanged: (amount: Amount?, error: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(defaultAmount?.amountStr ?: "0") }
    val amount = remember(currency, text) { getAmount(currency, text) }
    val insufficientBalance = remember(amount, maxAmount) {
        amount?.let { maxAmount == null || it > maxAmount } == true
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        AmountInputField(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            value = text,
            onValueChange = { value ->
                text = value

                // Update selected amount
                getAmount(currency, text)?.let {
                    onAmountChanged(it, maxAmount == null || it > maxAmount)
                } ?: onAmountChanged(null, true)
            },
            label = { Text(stringResource(R.string.amount_withdraw)) },
            supportingText = {
                if (insufficientBalance && maxAmount != null) {
                    Text(stringResource(R.string.amount_excess, maxAmount))
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
}

@Preview
@Composable
fun WithdrawalShowInfoPreview() {
    TalerSurface {
        WithdrawalShowInfo(
            WithdrawStatus(
                status = Updating,
                talerWithdrawUri = "taler://",
                currency = "KUDOS",
                exchangeBaseUrl = "exchange.head.taler.net",
                transactionId = "tx:343434",
                error = null,
                uriInfo = WithdrawalDetailsForUri(
                    amount = null,
                    currency = "KUDOS",
                    editableAmount = true,
                    maxAmount = Amount.fromJSONString("KUDOS:10"),
                    wireFee = Amount.fromJSONString("KUDOS:0.2"),
                    defaultExchangeBaseUrl = "exchange.head.taler.net",
                    possibleExchanges = listOf(
                        ExchangeItem(
                            exchangeBaseUrl = "exchange.demo.taler.net",
                            currency = "KUDOS",
                            paytoUris = emptyList(),
                            scopeInfo = null,
                        ),
                        ExchangeItem(
                            exchangeBaseUrl = "exchange.head.taler.net",
                            currency = "KUDOS",
                            paytoUris = emptyList(),
                            scopeInfo = null,
                        ),
                    ),
                ),
                amountInfo = WithdrawalDetailsForAmount(
                    tosAccepted = true,
                    amountRaw = Amount.fromJSONString("KUDOS:10.1"),
                    amountEffective = Amount.fromJSONString("KUDOS:10.2"),
                    withdrawalAccountsList = emptyList(),
                    ageRestrictionOptions = listOf(18, 23),
                    scopeInfo = ScopeInfo.Exchange(
                        currency = "KUDOS",
                        url = "exchange.head.taler.net",
                    ),
                )
            ),
            currency = "KUDOS",
            spec = null,
            onSelectExchange = {},
            onSelectAmount = {},
            onConfirm = {},
        )
    }
}