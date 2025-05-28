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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment.Companion.Center
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
import net.taler.wallet.systemBarsPaddingBottom
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.useDebounce
import kotlin.random.Random

@Composable
fun OutgoingPullComposable(
    state: OutgoingState,
    defaultScope: ScopeInfo?,
    scopes: List<ScopeInfo>,
    getCurrencySpec: (scope: ScopeInfo) -> CurrencySpecification?,
    checkPeerPullCredit: suspend (amount: AmountScope) -> CheckPeerPullCreditResult?,
    onCreateInvoice: (amount: AmountScope, subject: String, hours: Long, exchangeBaseUrl: String) -> Unit,
    onTosAccept: (exchangeBaseUrl: String) -> Unit,
    onClose: () -> Unit,
) {
    when(state) {
        is OutgoingChecking, is OutgoingCreating, is OutgoingResponse -> PeerCreatingComposable()
        is OutgoingIntro, is OutgoingChecked -> OutgoingPullIntroComposable(
            defaultScope = defaultScope,
            scopes = scopes,
            getCurrencySpec = getCurrencySpec,
            checkPeerPullCredit = checkPeerPullCredit,
            onCreateInvoice = onCreateInvoice,
            onTosAccept = onTosAccept,
        )
        is OutgoingError -> PeerErrorComposable(state, onClose)
    }
}

@Composable
fun PeerCreatingComposable() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(32.dp)
                .align(Center),
        )
    }
}

@Composable
fun OutgoingPullIntroComposable(
    defaultScope: ScopeInfo?,
    scopes: List<ScopeInfo>,
    getCurrencySpec: (scope: ScopeInfo) -> CurrencySpecification?,
    checkPeerPullCredit: suspend (amount: AmountScope) -> CheckPeerPullCreditResult?,
    onCreateInvoice: (amount: AmountScope, subject: String, hours: Long, exchangeBaseUrl: String) -> Unit,
    onTosAccept: (exchangeBaseUrl: String) -> Unit,
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var amount by remember {
        val scope = defaultScope ?: scopes[0]
        val currency = scope.currency
        mutableStateOf(AmountScope(Amount.zero(currency), scope))
    }
    val selectedSpec = remember(amount.scope) { getCurrencySpec(amount.scope) }
    var checkResult by remember { mutableStateOf<CheckPeerPullCreditResult?>(null) }
    val res = checkResult

    var option by rememberSaveable { mutableStateOf(DEFAULT_EXPIRY) }
    var hours by rememberSaveable { mutableLongStateOf(DEFAULT_EXPIRY.hours) }

    amount.useDebounce {
        checkResult = checkPeerPullCredit(it)
    }

    LaunchedEffect(Unit) {
        checkResult = checkPeerPullCredit(amount)
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
            AmountScopeField(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                amount = amount.copy(amount = amount.amount.withSpec(selectedSpec)),
                scopes = scopes,
                readOnly = false,
                onAmountChanged = { amount = it },
                isError = amount.amount.isZero(),
                label = { Text(stringResource(R.string.amount_receive)) },
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

            if (res != null) {
                if (res.amountEffective > res.amountRaw) {
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
            }

            checkResult?.exchangeBaseUrl?.let { exchangeBaseUrl ->
                TransactionInfoComposable(
                    label = stringResource(id = R.string.withdraw_exchange),
                    info = cleanExchange(exchangeBaseUrl),
                )
            }

            Text(
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                text = stringResource(R.string.send_peer_expiration_period),
                style = MaterialTheme.typography.bodyMedium,
            )

            ExpirationComposable(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                option = option,
                hours = hours,
                onOptionChange = { option = it }
            ) { hours = it }

            BottomInsetsSpacer()
        }

        BottomButtonBox(Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier
                    .systemBarsPaddingBottom(),
                enabled = subject.isNotBlank() && res != null,
                onClick = {
                    val ex = res?.exchangeBaseUrl ?: error("clickable without exchange")
                    if (res.tosStatus == ExchangeTosStatus.Accepted) {
                        onCreateInvoice(
                            amount,
                            subject,
                            hours,
                            ex
                        )
                    } else onTosAccept(ex)
                },
            ) {
                if (checkResult != null && checkResult?.tosStatus != ExchangeTosStatus.Accepted) {
                    Text(text = stringResource(R.string.exchange_tos_accept))
                } else {
                    Text(text = stringResource(R.string.receive_peer_create_button))
                }
            }
        }
    }
}

@Composable
fun PeerErrorComposable(state: OutgoingError, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            text = state.info.userFacingMsg,
        )

        Button(
            modifier = Modifier.padding(16.dp),
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(text = stringResource(R.string.close))
        }

        BottomInsetsSpacer()
    }
}

@Preview
@Composable
fun PeerPullComposableCreatingPreview() {
    TalerSurface {
        OutgoingPullComposable(
            state = OutgoingCreating,
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            checkPeerPullCredit = { null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
fun PeerPullComposableCheckingPreview() {
    TalerSurface {
        OutgoingPullComposable(
            state = if (Random.nextBoolean()) OutgoingIntro else OutgoingChecking,
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            checkPeerPullCredit = { null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
fun PeerPullComposableCheckedPreview() {
    TalerSurface {
        val amountRaw = Amount.fromString("TESTKUDOS", "42.42")
        val amountEffective = Amount.fromString("TESTKUDOS", "42.23")
        OutgoingPullComposable(
            state = OutgoingChecked(amountRaw, amountEffective, "https://exchange.demo.taler.net/", ExchangeTosStatus.Accepted),
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            checkPeerPullCredit = { null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
fun PeerPullComposableErrorPreview() {
    TalerSurface {
        val json = mapOf("foo" to JsonPrimitive("bar"))
        val state = OutgoingError(TalerErrorInfo(TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED, "hint", "message", json))
        OutgoingPullComposable(
            state = state,
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            getCurrencySpec = { null },
            checkPeerPullCredit = { null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
            onClose = {},
        )
    }
}