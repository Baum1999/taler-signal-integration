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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment.Companion.CenterVertically
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
import net.taler.common.RelativeTime
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
    devMode: Boolean,
    getCurrencySpec: suspend (scope: ScopeInfo) -> CurrencySpecification?,
    getFees: suspend (amount: AmountScope) -> CheckFeeResult?,
    onSend: (amount: AmountScope, summary: String, hours: Long) -> Unit,
    modifier: Modifier = Modifier,
    // Optionale Vorbefuellung, Default null aendert das normale Verhalten
    // (leeres Formular) nicht. Genutzt vom Signal-Fork's ComposeRefundScreen,
    // um den Rueckerstattungsbetrag/-zweck aus der bereits Taler-seitig
    // aufgeloesten Original-Transaktion vorzubefuellen, statt Signal
    // irgendetwas erfinden zu lassen (die Zahl kommt weiterhin aus einem
    // frueheren Taler-Aufruf, nie aus Signal) - bleibt trotzdem editierbar,
    // eine Rueckerstattung ist eine neue Zahlung, keine erzwungene Kopie.
    initialAmount: Amount? = null,
    initialSubject: String? = null,
    // Fix (Signal-Fork UX-Befund #3): Chat hat disappearing messages auf X
    // Stunden stehen - der Zahlungslink darf die sichtbare Nachricht nicht
    // ueberleben, sonst bleibt eine laengst aus dem Chat verschwundene
    // Zahlung serverseitig weiter offen. Nur gesetzt, wenn Signal einen
    // aktiven disappearing-messages-Timer mitgibt (ComposeSendScreen).
    initialExpirationHours: Long? = null,
    // Fix (Signal-Fork UX-Befund #1/#2/#4): ersetzt die vormals separate
    // GroupSplitCalculator-Banner-UI oberhalb dieses Formulars. Nur gesetzt
    // bei Gruppen-Versand (ComposeSendScreen) - zeigt direkt unter dem
    // Betragsfeld einen Live-Hinweis, der den bereits eingetippten Betrag
    // (denselben, der auch gesendet wird - keine zweite Eingabe mehr) durch
    // die Gruppengroesse teilt. Kein eigenes "Vorschlag uebernehmen" mehr
    // noetig, der Nutzer tippt den Pro-Kopf-Betrag danach einfach selbst ins
    // selbe Feld.
    groupSplitMemberCount: Int? = null,
) {
    when(state) {
        is OutgoingChecking, is OutgoingCreating, is OutgoingResponse -> LoadingScreen(modifier)
        is OutgoingIntro, is OutgoingError -> OutgoingPushIntroComposable(
            state = state,
            defaultScope = defaultScope,
            scopes = scopes,
            devMode = devMode,
            getCurrencySpec = getCurrencySpec,
            getFees = getFees,
            onSend = onSend,
            modifier = modifier,
            initialAmount = initialAmount,
            initialSubject = initialSubject,
            initialExpirationHours = initialExpirationHours,
            groupSplitMemberCount = groupSplitMemberCount,
        )
    }
}

@Composable
fun OutgoingPushIntroComposable(
    state: OutgoingState,
    defaultScope: ScopeInfo?,
    scopes: List<ScopeInfo>,
    devMode: Boolean,
    getCurrencySpec: suspend (scope: ScopeInfo) -> CurrencySpecification?,
    getFees: suspend (amount: AmountScope) -> CheckFeeResult?,
    onSend: (amount: AmountScope, summary: String, hours: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialAmount: Amount? = null,
    initialSubject: String? = null,
    initialExpirationHours: Long? = null,
    groupSplitMemberCount: Int? = null,
) {
    var amount by remember {
        val scope = defaultScope ?: scopes[0]
        val currency = scope.currency
        mutableStateOf(AmountScope(initialAmount ?: Amount.zero(currency), scope))
    }
    val selectedSpec by produceState<CurrencySpecification?>(
        initialValue = null,
        key1 = amount.scope,
    ) {
        value = getCurrencySpec(amount.scope)
    }
    var feeResult by remember { mutableStateOf<CheckFeeResult>(None()) }
    var subject by rememberSaveable { mutableStateOf(initialSubject ?: "") }

    var option by rememberSaveable {
        mutableStateOf(if (initialExpirationHours != null) ExpirationOption.CUSTOM else DEFAULT_EXPIRY)
    }
    var hours by rememberSaveable { mutableLongStateOf(initialExpirationHours ?: DEFAULT_EXPIRY.hours) }

    amount.useDebounce {
        feeResult = getFees(it) ?: None()
        // Fix (Signal-Fork UX-Befund #3): ist die Gueltigkeit bereits durch
        // den disappearing-messages-Timer des Chats vorgegeben, darf der vom
        // Exchange vorgeschlagene Standard das nicht mehr stillschweigend
        // ueberschreiben - sonst zeigt der Screen weiterhin "verschwindet
        // nach 8h", waehrend die Zahlung tatsaechlich 1 Tag (o.ae.) gueltig
        // bleibt.
        if (initialExpirationHours == null) {
            (feeResult as? Success)?.let { res ->
                option = ExpirationOption.CUSTOM
                hours = res.defaultExpiration.toHours()
            }
        }
    }

    val amountFocusRequester = remember { FocusRequester() }
    val subjectFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        amountFocusRequester.requestFocus()
    }

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

            var shortcutSelected by remember { mutableStateOf(false) }
            AmountScopeField(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
                amount = amount.copy(amount = amount.amount.withSpec(selectedSpec)),
                scopes = scopes,
                readOnly = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                showAmount = state !is OutgoingError,
                showShortcuts = true,
                onAmountChanged = {
                    amount = it
                    shortcutSelected = false
                },
                onShortcutSelected = {
                    amount = it
                    shortcutSelected = true
                },
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

            groupSplitMemberCount?.let { count ->
                GroupSplitHint(memberCount = count, total = amount.amount, spec = selectedSpec)
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

            AnimatedVisibility(feeResult is Success && !amount.amount.isZero()) {
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalAlignment = CenterHorizontally,
                ) {
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
                        placeholder = { Text(stringResource(R.string.send_peer_default_purpose)) },
                        label = { Text(stringResource(R.string.send_peer_purpose)) },
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

                    if (devMode) {
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
                    }

                    (feeResult as? Success)?.let {
                        if (amount.scope is ScopeInfo.Global) {
                            TransactionInfoComposable(
                                label = stringResource(id = R.string.withdraw_exchange),
                                info = cleanExchange(it.exchangeBaseUrl),
                                marquee = true,
                            )
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    // do not steal focus when manually typing amount
                    if (shortcutSelected) subjectFocusRequester.requestFocus()
                }
            }

            BottomInsetsSpacer()
        }

        val defaultSubject = stringResource(R.string.send_peer_default_purpose)
        BottomButtonBox(Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier.systemBarsPaddingBottom(),
                enabled = feeResult is Success && !amount.amount.isZero(),
                onClick = { onSend(
                    amount,
                    subject.ifBlank { defaultSubject },
                    hours,
                ) },
            ) {
                Text(text = stringResource(R.string.send_peer_create_button_amount,
                    amount.amount.withSpec(selectedSpec)))
            }
        }
    }
}

/**
 * Ersetzt die vormalige, separate GroupSplitCalculator-Banner-UI (Signal-Fork
 * UX-Befund #1/#2/#4): kein eigenes Eingabefeld fuer den Gesamtbetrag mehr
 * (der bereits eingetippte Betrag oben IST der Gesamtbetrag - eine zweite
 * Eingabe dafuer war die vom Nutzer gemeldete Dopplung) und kein "Vorschlag
 * uebernehmen"-Button mehr - der Pro-Kopf-Betrag ist nur eine Live-Anzeige,
 * der Nutzer traegt ihn bei Bedarf selbst ins Betragsfeld oben ein.
 */
@Composable
private fun GroupSplitHint(
    memberCount: Int,
    total: Amount,
    spec: CurrencySpecification?,
) {
    var splitEnabled by rememberSaveable { mutableStateOf(false) }
    var includeSelf by rememberSaveable { mutableStateOf(true) }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = CenterVertically,
    ) {
        Checkbox(checked = splitEnabled, onCheckedChange = { splitEnabled = it })
        Text(stringResource(R.string.compose_send_split_checkbox))
    }
    if (splitEnabled) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = CenterVertically,
        ) {
            Checkbox(checked = includeSelf, onCheckedChange = { includeSelf = it })
            Text(stringResource(R.string.compose_send_split_include_self_checkbox))
        }
        val perPerson = if (total.isZero()) null else try {
            splitAmountEvenly(total, memberCount, includeSelf)
        } catch (e: IllegalArgumentException) {
            null
        }
        perPerson?.let {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.compose_send_split_suggestion, it.withSpec(spec)),
            )
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
            devMode = true,
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net",
                defaultExpiration = RelativeTime.fromMillis(10 * 24 * 60 * 60 * 1000),
            ) },
            onSend = { _, _, _ -> },
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
            devMode = true,
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net",
                defaultExpiration = RelativeTime.fromMillis(10 * 24 * 60 * 60 * 1000),
            ) },
            onSend = { _, _, _ -> },
        )
    }
}

@Preview
@Composable
fun PeerPushComposableCheckedPreview() {
    TalerSurface {
        val amountEffective = Amount.fromString("TESTKUDOS", "42.42")
        val amountRaw = Amount.fromString("TESTKUDOS", "42.23")
        val state = OutgoingIntro
        OutgoingPushComposable(
            state = state,
            devMode = true,
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
                exchangeBaseUrl = "https://exchange.demo.taler.net",
                defaultExpiration = RelativeTime.fromMillis(10 * 24 * 60 * 60 * 1000),
            ) },
            onSend = { _, _, _ -> },
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
            devMode = true,
            getCurrencySpec = { null },
            getFees = { Success(
                amountEffective = Amount.fromJSONString("KUDOS:10"),
                amountRaw = Amount.fromJSONString("KUDOS:12"),
                maxDepositAmountEffective = Amount.fromJSONString("KUDOS:12"),
                exchangeBaseUrl = "https://exchange.demo.taler.net",
                defaultExpiration = RelativeTime.fromMillis(10 * 24 * 60 * 60 * 1000),
            ) },
            onSend = { _, _, _ -> },
        )
    }
}