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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.cleanExchange

data class AmountScope(
    val amount: Amount = Amount.zero(scope.currency),
    val scope: ScopeInfo,
    // whether fee calculation should be debounced
    val debounce: Boolean = false,
    // whether it originated from user input
    val userInput: Boolean = false,
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
    showAmount: Boolean = true,
    showScope: Boolean = true,
    showShortcuts: Boolean = false,
    onShortcutSelected: ((amount: AmountScope) -> Unit)? = null,
) {
    Column(modifier) {
        if (showScope) {
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
                readOnly = readOnly || !editableScope,
            )
        }

        if (showAmount) {
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
                keyboardActions = keyboardActions,
                keyboardOptions = keyboardOptions,
                showSymbol = true,
            )

            val commonAmounts = amount.amount.spec?.commonAmounts?.map {
                it.withSpec(amount.amount.spec) }
            AnimatedVisibility(showShortcuts && amount.amount.isZero() && commonAmounts != null) {
                if (commonAmounts != null) {
                    AmountInputShortcuts(
                        modifier = Modifier.padding(top = 10.dp),
                        amounts = commonAmounts,
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
}

@OptIn(ExperimentalMaterial3Api::class)
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

        val value = when (scope) {
            is ScopeInfo.Global -> scope.currency
            is ScopeInfo.Exchange -> cleanExchange(scope.url)
            is ScopeInfo.Auditor -> cleanExchange(scope.url)
        }

        val colors = OutlinedTextFieldDefaults.colors()
        val singleLine = true
        val enabled = false
        val interactionSource = remember { MutableInteractionSource() }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier,
        ) {
            BasicTextField(
                value = value,
                modifier = Modifier
                    .height(45.dp)
                    .clickable { if (!readOnly) expanded = true }
                    .fillMaxWidth(),
                onValueChange = { },
                enabled = enabled,
                readOnly = true,
                textStyle = TextStyle(color = colors.focusedTextColor),
                interactionSource = interactionSource,
                singleLine = singleLine,
                decorationBox =
                    @Composable { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = value,
                            innerTextField = innerTextField,
                            singleLine = singleLine,
                            enabled = enabled,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            prefix = {
                                Text(
                                    modifier = Modifier.padding(end = 6.dp),
                                    text = stringResource(R.string.currency_via),
                                )
                            },
                            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                                top = 0.dp,
                                bottom = 0.dp,
                            ),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                            }
                        )
                    },
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MenuDefaults.containerColor,
                shape = MenuDefaults.shape,
            ) {
                scopes.forEachIndexed { index, s ->
                    DropdownMenuItem(
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        leadingIcon = {
                            if (selectedIndex == index) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        text = {
                            Text(
                                text = when (s) {
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
                                }
                            )
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
}

@Composable
private fun AmountInputShortcuts(
    modifier: Modifier = Modifier,
    amounts: List<Amount>,
    onSelected: (amount: Amount) -> Unit,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth(),
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
                amount = Amount.fromJSONString("KUDOS:10").withSpec(
                    CurrencySpecification(
                        name = "Kudos",
                        numFractionalInputDigits = 2,
                        numFractionalNormalDigits = 2,
                        numFractionalTrailingZeroDigits = 2,
                        altUnitNames = mapOf(),
                        commonAmounts = listOf(
                            Amount.fromJSONString("KUDOS:5"),
                            Amount.fromJSONString("KUDOS:10"),
                            Amount.fromJSONString("KUDOS:25"),
                            Amount.fromJSONString("KUDOS:50"),
                        ),
                    ),
                ),
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