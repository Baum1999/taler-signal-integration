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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.AmountScope
import net.taler.wallet.compose.AmountScopeField
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.exchanges.ExchangeTosStatus
import net.taler.wallet.payment.stringResId
import net.taler.wallet.peer.CheckFeeResult.InsufficientBalance
import net.taler.wallet.peer.CheckFeeResult.None
import net.taler.wallet.peer.CheckFeeResult.Success
import net.taler.wallet.systemBarsPaddingBottom
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.useDebounce
import kotlin.random.Random

@Composable
fun OutgoingPushComposable(
    state: OutgoingState,
    defaultScope: ScopeInfo?,
    scopes: List<ScopeInfo>,
    getCurrencySpec: (scope: ScopeInfo) -> CurrencySpecification?,
    getFees: suspend (amount: AmountScope) -> CheckFeeResult?,
    onSend: (amount: AmountScope, summary: String, hours: Long) -> Unit,
    onClose: () -> Unit,
) {
    when(state) {
        is OutgoingChecking, is OutgoingCreating, is OutgoingResponse -> PeerCreatingComposable()
        is OutgoingIntro, is OutgoingChecked -> OutgoingPushIntroComposable(
            defaultScope = defaultScope,
            scopes = scopes,
            getCurrencySpec = getCurrencySpec,
            getFees = getFees,
            onSend = onSend,
        )
        is OutgoingError -> PeerErrorComposable(state, onClose)
    }
}

@Composable
fun OutgoingPushIntroComposable(
    defaultScope: ScopeInfo?,
    scopes: List<ScopeInfo>,
    getCurrencySpec: (scope: ScopeInfo) -> CurrencySpecification?,
    getFees: suspend (amount: AmountScope) -> CheckFeeResult?,
    onSend: (amount: AmountScope, summary: String, hours: Long) -> Unit,
) {
    var amount by remember {
        val scope = defaultScope ?: scopes[0]
        val currency = scope.currency
        mutableStateOf(AmountScope(Amount.zero(currency), scope))
    }
    val selectedSpec = remember(amount.scope) { getCurrencySpec(amount.scope) }
    var feeResult by remember { mutableStateOf<CheckFeeResult>(None()) }
    var subject by rememberSaveable { mutableStateOf("") }

    var option by rememberSaveable { mutableStateOf(DEFAULT_EXPIRY) }
    var hours by rememberSaveable { mutableLongStateOf(DEFAULT_EXPIRY.hours) }

    amount.useDebounce {
        feeResult = getFees(it) ?: None()
    }

    LaunchedEffect(Unit) {
        feeResult = getFees(amount) ?: None()
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = CenterHorizontally,
        ) {
            AnimatedVisibility(feeResult.maxDepositAmountRaw != null) {
                feeResult.maxDepositAmountRaw?.let {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = if (feeResult.maxDepositAmountEffective == it) {
                            stringResource(
                                R.string.amount_available_transfer,
                                it.withSpec(selectedSpec),
                            )
                        } else {
                            stringResource(
                                R.string.amount_available_transfer_fees,
                                it.withSpec(selectedSpec),
                            )
                        },
                    )
                }
            }

            AmountScopeField(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                amount = amount.copy(amount = amount.amount.withSpec(selectedSpec)),
                scopes = scopes,
                readOnly = false,
                onAmountChanged = { amount = it },
                label = { Text(stringResource(R.string.amount_send)) },
                isError = amount.amount.isZero() || feeResult is InsufficientBalance,
                supportingText = {
                    when (val res = feeResult) {
                        is Success -> if (res.amountEffective > res.amountRaw) {
                            val fee = res.amountEffective - res.amountRaw
                            Text(
                                text = stringResource(
                                    id = R.string.payment_fee,
                                    fee.withSpec(selectedSpec)
                                ),
                                softWrap = false,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        is InsufficientBalance -> {
                            Text(
                                stringResource(
                                    res.causeHint?.stringResId()
                                        ?: R.string.payment_balance_insufficient
                                )
                            )
                        }

                        else -> {}
                    }
                }
            )

            OutlinedTextField(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
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
                },
                supportingText = {
                    Text(stringResource(R.string.char_count, subject.length, MAX_LENGTH_SUBJECT))
                },
            )

            Text(
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                text = stringResource(R.string.send_peer_expiration_period),
                style = MaterialTheme.typography.bodyMedium,
            )

            ExpirationComposable(
                modifier = Modifier.padding(
                    vertical = 8.dp,
                    horizontal = 16.dp,
                ),
                option = option,
                hours = hours,
                onOptionChange = { option = it }
            ) { hours = it }

            // only show provider for global scope,
            // otherwise it's already in scope selector
            AnimatedVisibility(feeResult is Success && amount.scope is ScopeInfo.Global) {
                (feeResult as? Success)?.let {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        TransactionInfoComposable(
                            label = stringResource(id = R.string.withdraw_exchange),
                            info = cleanExchange(it.exchangeBaseUrl),
                        )
                    }
                }
            }

            BottomInsetsSpacer()
        }

        BottomButtonBox(Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier.systemBarsPaddingBottom(),
                enabled = feeResult is Success && subject.isNotBlank(),
                onClick = { onSend(amount, subject, hours) },
            ) {
                Text(text = stringResource(R.string.send_peer_create_button))
            }
        }
    }
}

@Preview
@Composable
fun PeerPushComposableCreatingPreview() {
    TalerSurface {
        OutgoingPushComposable(
            state = OutgoingCreating,
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net"
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
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net"
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
            getCurrencySpec = { null },
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net"
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
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net"
            ) },
            onSend = { _, _, _ -> },
            onClose = {},
        )
    }
}