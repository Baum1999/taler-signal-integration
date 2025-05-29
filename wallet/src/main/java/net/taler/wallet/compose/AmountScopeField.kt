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

package net.taler.wallet.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange

data class AmountScope(
    val amount: Amount,
    val scope: ScopeInfo,
)

@Composable
fun AmountScopeField(
    modifier: Modifier = Modifier,
    amount: AmountScope,
    editableScope: Boolean = true,
    scopes: List<ScopeInfo>,
    onAmountChanged: (amount: AmountScope) -> Unit,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabledAmount: Boolean = true,
    enabledScope: Boolean = true,
    showShortcuts: Boolean = false,
    onShortcutSelected: ((amount: AmountScope) -> Unit)? = null,
) {
    Column(modifier) {
        if (editableScope) {
            ScopeDropdown(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                scopes = scopes,
                onScopeChanged = { scope ->
                    onAmountChanged(amount.copy(
                        scope = scope,
                        amount = Amount.zero(scope.currency),
                    ))
                },
                initialScope = amount.scope,
                readOnly = readOnly || !enabledScope,
            )
        }

        if (enabledAmount) {
            AmountInputFieldBase(
                modifier = Modifier
                    .fillMaxWidth(),
                amount = amount.amount,
                onAmountChanged = {
                    onAmountChanged(amount.copy(amount = it))
                },
                label = label,
                isError = isError,
                supportingText = supportingText,
                readOnly = readOnly,
                showSymbol = true,
            )

            if (showShortcuts) {
                val currency = amount.amount.currency
                AmountInputShortcuts(
                    // TODO: currency-appropriate presets
                    amounts = listOf(
                        Amount.fromString(currency, "50").withSpec(amount.amount.spec),
                        Amount.fromString(currency, "25").withSpec(amount.amount.spec),
                        Amount.fromString(currency, "10").withSpec(amount.amount.spec),
                        Amount.fromString(currency, "5").withSpec(amount.amount.spec),
                    ),
                    onSelected = { shortcut ->
                        onShortcutSelected?.let {
                            it(amount.copy(amount = shortcut))
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun ScopeDropdown(
    scopes: List<ScopeInfo>,
    onScopeChanged: (ScopeInfo) -> Unit,
    modifier: Modifier = Modifier,
    initialScope: ScopeInfo? = null,
    readOnly: Boolean = false,
) {
    val initialIndex = scopes.indexOf(initialScope).let { if (it < 0) 0 else it }
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier,
    ) {
        val scope = scopes.getOrNull(selectedIndex)
            ?: initialScope
            ?: error("no scope available")

        OutlinedTextField(
            modifier = Modifier
                .clickable(onClick = { if (!readOnly) expanded = true })
                .fillMaxWidth(),
            value = when (scope) {
                is ScopeInfo.Global -> scope.currency
                is ScopeInfo.Exchange -> cleanExchange(scope.url)
                is ScopeInfo.Auditor -> cleanExchange(scope.url)
            },
            prefix = { Text(
                modifier = Modifier.padding(end = 6.dp),
                text = stringResource(R.string.currency_via)
            ) },
            onValueChange = { },
            readOnly = true,
            enabled = false,
            textStyle = LocalTextStyle.current.copy( // show text as if not disabled
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier,
        ) {
            scopes.forEachIndexed { index, s ->
                DropdownMenuItem(
                    text = {
                        Text(text = when (s) {
                            is ScopeInfo.Global -> s.currency
                            is ScopeInfo.Exchange -> stringResource(
                                R.string.currency_url,
                                s.currency,
                                cleanExchange(s.url),
                            )
                            is ScopeInfo.Auditor -> stringResource(
                                R.string.currency_url,
                                s.currency,
                                cleanExchange(s.url),
                            )
                        })
                    },
                    onClick = {
                        selectedIndex = index
                        onScopeChanged(scopes[index])
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AmountInputShortcuts(
    amounts: List<Amount>,
    onSelected: (amount: Amount) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        amounts.forEach {
            SelectionChip (
                selected = false,
                label = { Text(it.toString()) },
                value = it,
                onSelected = onSelected,
            )
        }
    }
}

@Preview
@Composable
fun AmountInputFieldPreview() {
    TalerSurface {
        var amount by remember {
            mutableStateOf(AmountScope(
                amount = Amount.fromJSONString("KUDOS:10"),
                scope = ScopeInfo.Exchange("KUDOS", "https://exchange.demo.taler.net/"),
            ))
        }
        AmountScopeField(
            amount = amount,
            editableScope = true,
            scopes = listOf(
                ScopeInfo.Global("CHF"),
                ScopeInfo.Exchange("KUDOS", url = "https://exchange.demo.taler.net/"),
                ScopeInfo.Auditor("TESTKUDOS", url = "https://auditor.test.taler.net/"),
            ),
            onAmountChanged = { amount = it },
            label = { Text("Amount to withdraw") },
            isError = false,
            readOnly = false,
            showShortcuts = true,
            onShortcutSelected = { amount = it },
        )
    }
}