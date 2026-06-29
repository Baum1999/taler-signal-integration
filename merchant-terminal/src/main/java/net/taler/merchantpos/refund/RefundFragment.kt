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

package net.taler.merchantpos.refund

import android.os.Bundle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.compose.runtime.livedata.observeAsState
import net.taler.merchantpos.MainActivity
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.merchantlib.OrderHistoryEntry
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.refund.RefundResult.AlreadyRefunded
import net.taler.merchantpos.refund.RefundResult.Error
import net.taler.merchantpos.refund.RefundResult.PastDeadline
import net.taler.merchantpos.refund.RefundResult.Success
import net.taler.merchantpos.showPosError

class RefundFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val refundManager by lazy { model.refundManager }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val item = refundManager.toBeRefunded
            if (item == null) {
                requireActivity().showPosError(R.string.refund_state_missing)
                (requireActivity() as MainActivity).navigateBack()
                return@setContent
            }
            RefundScreen(
                item = item,
                currencySpec = model.configManager.currencySpec,
                onAbort = { (requireActivity() as MainActivity).navigateBack() },
                onRefund = ::onRefundButtonClicked,
            )
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        refundManager.refundResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Error -> onError(R.string.refund_error_backend, result.msg)
                PastDeadline -> onError(R.string.refund_error_deadline)
                AlreadyRefunded -> onError(R.string.refund_error_already_refunded)
                is Success -> (requireActivity() as MainActivity).navigateTo(PosDestination.RefundUri)
                null -> Unit
            }
        }
    }

    private fun onRefundButtonClicked(item: OrderHistoryEntry, amount: Amount, reason: String) {
        refundManager.refund(item, amount, reason)
    }

    private fun onError(mainResId: Int, details: String = "") {
        requireActivity().showPosError(mainResId, details)
    }
}

@Composable
private fun RefundScreen(
    item: OrderHistoryEntry,
    currencySpec: net.taler.common.CurrencySpecification?,
    initialReason: String? = null,
    onAbort: () -> Unit,
    onRefund: (OrderHistoryEntry, Amount, String) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(item.amount.withSpec(currencySpec).amountStr)
    }
    var reason by remember { mutableStateOf(initialReason ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val amountFocusRequester = remember { FocusRequester() }
    val reasonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val invalidAmountText = stringResource(R.string.refund_error_invalid_amount)
    val zeroAmountText = stringResource(R.string.refund_error_zero)
    val maxAmountTemplate = stringResource(R.string.refund_error_max_amount, "%s")
    val submitRefund = submit@{
        val maxAmount = item.amount.withSpec(currencySpec)
        val normalizedAmountText = amountText.trim()
        val inputAmount = try {
            if (normalizedAmountText.isEmpty()) {
                maxAmount
            } else {
                Amount.fromString(item.amount.currency, normalizedAmountText).withSpec(currencySpec)
            }
        } catch (_: AmountParserException) {
            errorText = invalidAmountText
            return@submit
        }
        if (inputAmount > maxAmount) {
            errorText = maxAmountTemplate.replace("%s", maxAmount.toString(showSymbol = false))
            return@submit
        }
        if (inputAmount.isZero()) {
            errorText = zeroAmountText
            return@submit
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onRefund(item, inputAmount, reason)
    }

    PosTheme {
        LaunchedEffect(Unit) {
            amountFocusRequester.requestFocus()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(item.summary, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    errorText = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
                label = { Text(stringResource(R.string.refund_amount)) },
                supportingText = errorText?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { reasonFocusRequester.requestFocus() },
                ),
                singleLine = true,
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(reasonFocusRequester),
                label = { Text(stringResource(R.string.refund_reason)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitRefund() },
                ),
                singleLine = true,
            )
            Button(
                onClick = { submitRefund() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.refund_confirm))
            }
            OutlinedButton(
                onClick = onAbort,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.refund_abort))
            }
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            },
                        )
                    },
            )
        }
    }
}

@Composable
internal fun RefundScreenContent(
    item: OrderHistoryEntry,
    currencySpec: net.taler.common.CurrencySpecification?,
    initialReason: String? = null,
) {
    RefundScreen(
        item = item,
        currencySpec = currencySpec,
        initialReason = initialReason,
        onAbort = {},
        onRefund = { _, _, _ -> },
    )
}
