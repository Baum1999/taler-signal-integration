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

package net.taler.wallet.peer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.AmountCurrencyField
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.exchanges.ExchangeTosStatus
import net.taler.wallet.peer.CheckFeeResult.InsufficientBalance
import net.taler.wallet.peer.CheckFeeResult.None
import net.taler.wallet.peer.CheckFeeResult.Success
import net.taler.wallet.useDebounce
import kotlin.random.Random

@Composable
fun OutgoingPushComposable(
    state: OutgoingState,
    defaultCurrency: String?,
    currencies: List<String>,
    getCurrencySpec: (currency: String) -> CurrencySpecification?,
    getFees: (amount: Amount) -> CheckFeeResult?,
    onSend: (amount: Amount, summary: String, hours: Long) -> Unit,
    onClose: () -> Unit,
) {
    when(state) {
        is OutgoingChecking, is OutgoingCreating, is OutgoingResponse -> PeerCreatingComposable()
        is OutgoingIntro, is OutgoingChecked -> OutgoingPushIntroComposable(
            defaultCurrency = defaultCurrency,
            currencies = currencies,
            getCurrencySpec = getCurrencySpec,
            getFees = getFees,
            onSend = onSend,
        )
        is OutgoingError -> PeerErrorComposable(state, onClose)
    }
}

@Composable
fun OutgoingPushIntroComposable(
    defaultCurrency: String?,
    currencies: List<String>,
    getCurrencySpec: (currency: String) -> CurrencySpecification?,
    getFees: (amount: Amount) -> CheckFeeResult?,
    onSend: (amount: Amount, summary: String, hours: Long) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = CenterHorizontally,
    ) {
        var amount by remember { mutableStateOf(Amount.zero(defaultCurrency ?: currencies[0])) }
        val selectedSpec = remember(amount) { getCurrencySpec(amount.currency) }
        var feeResult by remember { mutableStateOf<CheckFeeResult>(None) }

        // TODO: make checkPeerPullCredit asynchronous!
        amount.useDebounce {
            feeResult = getFees(it) ?: None
        }

        LaunchedEffect(Unit) {
            feeResult = getFees(amount) ?: None
        }

        AmountCurrencyField(
            modifier = Modifier.fillMaxWidth(),
            initialAmount = amount,
            initialCurrency = amount.currency,
            currencies = currencies,
            readOnly = false,
            onAmountChanged = { amount = it },
            getCurrencySpec = getCurrencySpec,
            label = { Text(stringResource(R.string.amount_send)) },
            isError = amount.isZero() || feeResult is InsufficientBalance,
            supportingText = {
                when (val res = feeResult) {
                    is Success -> if (res.amountEffective > res.amountRaw) {
                        val fee = res.amountEffective - res.amountRaw
                        Text(
                            modifier = Modifier.padding(vertical = 16.dp),
                            text = stringResource(
                                id = R.string.payment_fee,
                                fee.withSpec(selectedSpec)
                            ),
                            softWrap = false,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is InsufficientBalance -> if (res.maxAmountEffective != null) {
                        Text(stringResource(R.string.payment_balance_insufficient_max, res.maxAmountEffective))
                    }

                    else -> {}
                }
            }
        )

        var subject by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = true,
            value = subject,
            onValueChange = { input ->
                if (input.length <= MAX_LENGTH_SUBJECT)
                    subject = input.replace('\n', ' ')
            },
            isError = subject.isBlank(),
            label = {
                Text(
                    stringResource(R.string.send_peer_purpose),
                    color = if (subject.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else Color.Unspecified,
                )
            }
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            color = if (subject.isBlank()) MaterialTheme.colorScheme.error else Color.Unspecified,
            text = stringResource(R.string.char_count, subject.length, MAX_LENGTH_SUBJECT),
            textAlign = TextAlign.End,
        )

        Text(
            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
            text = stringResource(R.string.send_peer_expiration_period),
            style = MaterialTheme.typography.bodyMedium,
        )

        var option by rememberSaveable { mutableStateOf(DEFAULT_EXPIRY) }
        var hours by rememberSaveable { mutableLongStateOf(DEFAULT_EXPIRY.hours) }
        ExpirationComposable(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            option = option,
            hours = hours,
            onOptionChange = { option = it }
        ) { hours = it }

        Button(
            enabled = feeResult is Success && subject.isNotBlank(),
            onClick = { onSend(amount, subject, hours) },
        ) {
            Text(text = stringResource(R.string.send_peer_create_button))
        }
    }
}

@Preview
@Composable
fun PeerPushComposableCreatingPreview() {
    TalerSurface {
        OutgoingPushComposable(
            state = OutgoingCreating,
            defaultCurrency = "KUDOS",
            currencies = listOf("KUDOS", "TESTKUDOS", "NETZBON"),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
            ) },
            onSend = { _, _, _ -> },
            onClose = {},
        )
    }
}

@Preview
@Composable
fun PeerPushComposableCheckingPreview() {
    TalerSurface {
        val state = if (Random.nextBoolean()) OutgoingIntro else OutgoingChecking
        OutgoingPushComposable(
            state = state,
            defaultCurrency = "KUDOS",
            currencies = listOf("KUDOS", "TESTKUDOS", "NETZBON"),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
            ) },
            onSend = { _, _, _ -> },
            onClose = {},
        )
    }
}

@Preview
@Composable
fun PeerPushComposableCheckedPreview() {
    TalerSurface {
        val amountEffective = Amount.fromString("TESTKUDOS", "42.42")
        val amountRaw = Amount.fromString("TESTKUDOS", "42.23")
        val state = OutgoingChecked(amountRaw, amountEffective, "https://exchange.demo.taler.net", ExchangeTosStatus.Accepted)
        OutgoingPushComposable(
            state = state,
            defaultCurrency = "KUDOS",
            currencies = listOf("KUDOS", "TESTKUDOS", "NETZBON"),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
            ) },
            onSend = { _, _, _ -> },
            onClose = {},
        )
    }
}

@Preview
@Composable
fun PeerPushComposableErrorPreview() {
    TalerSurface {
        val json = mapOf("foo" to JsonPrimitive("bar"))
        val state = OutgoingError(TalerErrorInfo(TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED, "hint", "message", json))
        OutgoingPushComposable(
            state = state,
            defaultCurrency = "KUDOS",
            currencies = listOf("KUDOS", "TESTKUDOS", "NETZBON"),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
            ) },
            onSend = { _, _, _ -> },
            onClose = {},
        )
    }
}