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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.link.splitAmountEvenly
import net.taler.wallet.systemBarsPaddingBottom
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.useDebounce
import kotlin.random.Random

@Composable
fun OutgoingPullComposable(
    state: OutgoingState,
    defaultScope: ScopeInfo?,
    scopes: List<ScopeInfo>,
    devMode: Boolean,
    getCurrencySpec: suspend (scope: ScopeInfo) -> CurrencySpecification?,
    checkPeerPullCredit: suspend (amount: AmountScope, loading: Boolean) -> CheckPeerPullCreditResult?,
    onCreateInvoice: (amount: AmountScope, subject: String, hours: Long, exchangeBaseUrl: String) -> Unit,
    onTosAccept: (exchangeBaseUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    // Gruppen-Split-Anfordern (Pendant zu onSendGroup in
    // OutgoingPushComposable): wird statt [onCreateInvoice] aufgerufen, wenn
    // der Split-Rechner aktiv und gueltig ist - amount ist bereits der
    // Pro-Kopf-Anteil, count die Anzahl der zu erzeugenden Invoices/URIs.
    onCreateInvoiceGroup: (
        amount: AmountScope, count: Int, subject: String, hours: Long, exchangeBaseUrl: String,
    ) -> Unit = { _, _, _, _, _ -> },
    // Fix (Signal-Fork: Gruppen-Split fuer "Anfordern" fehlte komplett):
    // gleiche Kopplung an den disappearing-messages-Timer des Chats wie bei
    // OutgoingPushComposable - siehe dortiger Kommentar.
    initialExpirationHours: Long? = null,
    // Gleiches Prinzip wie in OutgoingPushComposable: nur gesetzt bei
    // Gruppen-Chats (ComposeSendScreen), aktiviert die Split-UI unten.
    groupSplitMemberCount: Int? = null,
    onSplitDataChanged: (includeSelf: Boolean?, totalAmount: Amount?) -> Unit = { _, _ -> },
    // Gleicher Slot wie in OutgoingPushComposable - vom Aufrufer befuellter
    // Gruppen-/disappearing-messages-Hinweis, direkt unter dem Betragsfeld.
    contextContent: @Composable () -> Unit = {},
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var amount by remember {
        val scope = defaultScope ?: scopes[0]
        val currency = scope.currency
        mutableStateOf(AmountScope(Amount.zero(currency), scope))
    }
    val selectedSpec by produceState<CurrencySpecification?>(
        initialValue = null,
        key1 = amount.scope,
    ) {
        value = getCurrencySpec(amount.scope)
    }
    var checkResult by remember { mutableStateOf<CheckPeerPullCreditResult?>(null) }
    val res = checkResult

    var option by rememberSaveable {
        mutableStateOf(if (initialExpirationHours != null) ExpirationOption.CUSTOM else DEFAULT_EXPIRY)
    }
    var hours by rememberSaveable { mutableLongStateOf(initialExpirationHours ?: DEFAULT_EXPIRY.hours) }

    // Gruppen-Split-Rechner-Zustand - identisches Muster wie in
    // OutgoingPushComposable (siehe dortige Kommentare zu resolvedSplit).
    var splitEnabled by rememberSaveable { mutableStateOf(false) }
    var includeSelf by rememberSaveable { mutableStateOf(true) }
    var memberCountInput by rememberSaveable {
        mutableStateOf(groupSplitMemberCount?.toString() ?: "")
    }

    LaunchedEffect(splitEnabled, includeSelf, memberCountInput, amount.amount) {
        if (splitEnabled) {
            val enteredCount = memberCountInput.toIntOrNull()
            val totalAmount = if (enteredCount != null && enteredCount in 1..MAX_SPLIT_MEMBERS) {
                amount.amount
            } else {
                null
            }
            onSplitDataChanged(includeSelf, totalAmount)
        } else {
            onSplitDataChanged(null, null)
        }
    }

    val resolvedSplit = if (splitEnabled) {
        val enteredCount = memberCountInput.toIntOrNull()
        if (enteredCount != null && enteredCount in 1..MAX_SPLIT_MEMBERS && !amount.amount.isZero()) {
            val recipientCount = if (includeSelf) enteredCount - 1 else enteredCount
            if (recipientCount > 0) {
                val memberCountForSplit = if (includeSelf) enteredCount else enteredCount + 1
                recipientCount to splitAmountEvenly(amount.amount, memberCountForSplit, includeSelf)
            } else null
        } else null
    } else null

    val tosReview = checkResult != null && !checkResult!!.tosStatus!!.isAccepted()

    amount.amount.useDebounce {
        if (amount.debounce) {
            checkResult = checkPeerPullCredit(amount, false)
        }
    }

    LaunchedEffect(amount) {
        if (!amount.debounce) {
            checkResult = checkPeerPullCredit(amount, true)
        }
    }

    if (state is OutgoingChecking ||
        state is OutgoingCreating ||
        state is OutgoingResponse) {
        LoadingScreen(modifier)
        return
    }

    val amountFocusRequester = remember { FocusRequester() }
    val subjectFocusRequester = remember { FocusRequester() }

    Column(
        modifier
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
            var shortcutSelected by remember { mutableStateOf(false) }
            AmountScopeField(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
                amount = amount.copy(amount = amount.amount.withSpec(selectedSpec)),
                scopes = scopes,
                readOnly = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                showAmount = !tosReview && state !is OutgoingError,
                showShortcuts = true,
                onAmountChanged = {
                    amount = it.copy(debounce = amount.scope == it.scope)
                    shortcutSelected = false
                },
                onShortcutSelected = {
                    amount = it.copy(debounce = true)
                    shortcutSelected = true
                },
                isError = amount.amount.isZero(),
                label = { Text(stringResource(R.string.amount_receive)) },
            )

            contextContent()

            groupSplitMemberCount?.let {
                GroupSplitHint(
                    total = amount.amount,
                    spec = selectedSpec,
                    splitEnabled = splitEnabled,
                    onSplitEnabledChanged = { splitEnabled = it },
                    includeSelf = includeSelf,
                    onIncludeSelfChanged = { newIncludeSelf ->
                        memberCountInput.toIntOrNull()?.let { current ->
                            val adjusted = if (newIncludeSelf) current + 1 else current - 1
                            memberCountInput = adjusted.coerceIn(1, MAX_SPLIT_MEMBERS).toString()
                        }
                        includeSelf = newIncludeSelf
                    },
                    memberCountInput = memberCountInput,
                    onMemberCountInputChanged = { memberCountInput = it },
                )
            }

            LaunchedEffect(tosReview) {
                if (!tosReview) amountFocusRequester.requestFocus()
            }

            if (state is OutgoingError) {
                ErrorComposable(
                    error = state.info,
                    modifier = Modifier.fillMaxWidth(),
                    devMode = devMode,
                    scrollable = false,
                )
                return@Column
            }

            if (tosReview) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.receive_peer_review_terms)
                )
            } else AnimatedVisibility(!amount.amount.isZero()) {
                Column {
                    OutlinedTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .focusRequester(subjectFocusRequester),
                        singleLine = true,
                        value = subject,
                        onValueChange = { input ->
                            if (input.length <= MAX_LENGTH_SUBJECT)
                                subject = input.replace('\n', ' ')
                        },
                        label = { Text(stringResource(R.string.send_peer_purpose)) },
                        placeholder = { Text(stringResource(R.string.receive_peer_default_purpose)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.char_count,
                                    subject.length,
                                    MAX_LENGTH_SUBJECT
                                )
                            )
                        },
                    )

                    if (res != null && res.amountRaw != null && res.amountEffective != null) {
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
                }

                LaunchedEffect(Unit) {
                    // do not steal focus when manually typing amount
                    if (shortcutSelected) subjectFocusRequester.requestFocus()
                }
            }

            // only show provider for global scope,
            // otherwise it's already in scope selector
            if (amount.scope is ScopeInfo.Global) {
                checkResult?.exchangeBaseUrl?.let { exchangeBaseUrl ->
                    TransactionInfoComposable(
                        label = stringResource(id = R.string.withdraw_exchange),
                        info = cleanExchange(exchangeBaseUrl),
                        marquee = true,
                    )
                }
            }

            BottomInsetsSpacer()
        }

        val defaultSubject = stringResource(R.string.receive_peer_default_purpose)
        BottomButtonBox(Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier
                    .systemBarsPaddingBottom(),
                // Gleiches Prinzip wie in OutgoingPushComposable: bei
                // aktivem, aber (noch) ungueltigem Split bleibt der Button
                // deaktiviert statt stillschweigend auf den Einzel-Pfad
                // zurueckzufallen.
                enabled = tosReview ||
                    (res != null && !amount.amount.isZero() && (!splitEnabled || resolvedSplit != null)),
                onClick = {
                    val ex = res?.exchangeBaseUrl ?: error("clickable without exchange")
                    if (res.tosStatus?.isAccepted() == true) {
                        val split = resolvedSplit
                        if (split != null) {
                            val (count, perShare) = split
                            onCreateInvoiceGroup(
                                amount.copy(amount = perShare),
                                count,
                                subject.ifBlank { defaultSubject },
                                hours,
                                ex,
                            )
                        } else {
                            onCreateInvoice(
                                amount,
                                subject.ifBlank { defaultSubject },
                                hours,
                                ex
                            )
                        }
                    } else onTosAccept(ex)
                },
            ) {
                if (tosReview) {
                    Text(text = stringResource(R.string.exchange_tos_view))
                } else {
                    Text(text = stringResource(R.string.receive_peer_create_button_amount,
                        amount.amount.withSpec(selectedSpec)))
                }
            }
        }
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
            devMode = true,
            getCurrencySpec = { null },
            checkPeerPullCredit = { _, _ -> null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
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
            devMode = true,
            getCurrencySpec = { null },
            checkPeerPullCredit = { _, _ -> null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
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
            state = OutgoingIntro,
            defaultScope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            scopes = listOf(
                ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
                ScopeInfo.Exchange("TESTKUDOS", "https://exchange.test.taler.net/"),
                ScopeInfo.Global("CHF"),
            ),
            devMode = true,
            getCurrencySpec = { null },
            checkPeerPullCredit = { _, _ -> null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
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
            devMode = true,
            getCurrencySpec = { null },
            checkPeerPullCredit = { _, _ -> null },
            onCreateInvoice = { _, _, _, _ -> },
            onTosAccept = {},
        )
    }
}