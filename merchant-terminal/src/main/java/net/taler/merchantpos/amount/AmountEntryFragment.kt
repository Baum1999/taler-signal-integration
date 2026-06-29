/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
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

package net.taler.merchantpos.amount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.taler.common.Amount
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.order.Order
import net.taler.merchantpos.showPosError

private const val QUICK_AMOUNT_ORDER_ID = -1
private const val QUICK_AMOUNT_PRODUCT_ID = "quick_amount"

class AmountEntryFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val paymentManager by lazy { viewModel.paymentManager }

    private var selectedCurrency by mutableStateOf<String?>(null)
    private var amount by mutableStateOf<Amount?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        initializeAmountState()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmountEntryScreen(
                    amountText = amount?.toString(showSymbol = false) ?: "0.00",
                    selectedCurrency = selectedCurrency,
                    currencyOptions = viewModel.configManager.currency?.let(::listOf) ?: emptyList(),
                    chargeEnabled = amount?.isZero() == false,
                    onCurrencySelected = ::setCurrency,
                    onDigitPressed = ::onDigitPressed,
                    onClearPressed = ::clearAmount,
                    onBackspacePressed = ::onBackspacePressed,
                    onChargePressed = ::onChargePressed,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.configManager.config.isValid()) {
            (requireActivity() as net.taler.merchantpos.MainActivity).navigateTo(PosDestination.Config)
        } else if (viewModel.configManager.currency == null) {
            (requireActivity() as net.taler.merchantpos.MainActivity).navigateTo(PosDestination.ConfigFetcher)
        }
    }

    private fun initializeAmountState() {
        val configuredCurrency = viewModel.configManager.currency ?: return
        if (selectedCurrency == null) {
            selectedCurrency = configuredCurrency
        }
        if (amount == null) {
            amount = Amount.zero(configuredCurrency).withSpec(viewModel.configManager.currencySpec)
        }
    }

    private fun setCurrency(currency: String) {
        selectedCurrency = currency
        val currentAmount = amount
        val spec = viewModel.configManager.currencySpec
        amount = when {
            currentAmount == null -> Amount.zero(currency)
            currentAmount.currency == currency -> currentAmount
            else -> currentAmount.withCurrency(currency)
        }.withSpec(spec)
    }

    private fun onDigitPressed(digit: Char) {
        val currentAmount = amount ?: return
        amount = currentAmount.addInputDigit(digit)?.withSpec(currentAmount.spec) ?: currentAmount
    }

    private fun onBackspacePressed() {
        val currentAmount = amount ?: return
        amount = currentAmount.removeInputDigit()?.withSpec(currentAmount.spec) ?: currentAmount
    }

    private fun clearAmount() {
        val currency = selectedCurrency ?: return
        amount = Amount.zero(currency).withSpec(viewModel.configManager.currencySpec)
    }

    private fun onChargePressed() {
        val configuredCurrency = viewModel.configManager.currency ?: run {
            (requireActivity() as net.taler.merchantpos.MainActivity).navigateTo(PosDestination.ConfigFetcher)
            return
        }
        val enteredCurrency = selectedCurrency ?: configuredCurrency
        val enteredAmount = amount
            ?: Amount.zero(enteredCurrency).withSpec(viewModel.configManager.currencySpec)

        if (enteredAmount.isZero()) {
            requireActivity().showPosError(R.string.amount_entry_error_zero)
            return
        }
        if (enteredCurrency != configuredCurrency) {
            requireActivity().showPosError(R.string.amount_entry_error_wrong_currency)
            return
        }

        val order = Order(
            id = QUICK_AMOUNT_ORDER_ID,
            currency = configuredCurrency,
            currencySpec = viewModel.configManager.currencySpec,
            availableCategories = emptyMap(),
        )
        val product = ConfigProduct(
            description = getString(R.string.amount_entry_product_description),
            productId = QUICK_AMOUNT_PRODUCT_ID,
            price = enteredAmount.withSpec(viewModel.configManager.currencySpec),
            categories = listOf(Int.MIN_VALUE),
        )
        val orderWithProduct = order + product

        // Backend doesn't require products; omit them for this "quick amount" flow.
        paymentManager.createPayment(orderWithProduct, includeProducts = false)
        (requireActivity() as net.taler.merchantpos.MainActivity).navigateTo(PosDestination.ProcessPayment)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountEntryScreen(
    amountText: String,
    selectedCurrency: String?,
    currencyOptions: List<String>,
    chargeEnabled: Boolean,
    onCurrencySelected: (String) -> Unit,
    onDigitPressed: (Char) -> Unit,
    onClearPressed: () -> Unit,
    onBackspacePressed: () -> Unit,
    onChargePressed: () -> Unit,
) {
    PosTheme {
        val configuration = LocalConfiguration.current
        val isTabletLayout = configuration.smallestScreenWidthDp >= 600
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (!isTabletLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AmountPane(
                        amountText = amountText,
                        selectedCurrency = selectedCurrency,
                        currencyOptions = currencyOptions,
                        isTabletLayout = false,
                        onCurrencySelected = onCurrencySelected,
                        modifier = Modifier
                            .weight(0.32f)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    KeypadPane(
                        isTabletLayout = false,
                        chargeEnabled = chargeEnabled,
                        onDigitPressed = onDigitPressed,
                        onClearPressed = onClearPressed,
                        onBackspacePressed = onBackspacePressed,
                        onChargePressed = onChargePressed,
                        modifier = Modifier
                            .weight(0.68f)
                            .padding(4.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AmountPane(
                        amountText = amountText,
                        selectedCurrency = selectedCurrency,
                        currencyOptions = currencyOptions,
                        isTabletLayout = true,
                        onCurrencySelected = onCurrencySelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.65f),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        KeypadPane(
                            isTabletLayout = true,
                            chargeEnabled = chargeEnabled,
                            onDigitPressed = onDigitPressed,
                            onClearPressed = onClearPressed,
                            onBackspacePressed = onBackspacePressed,
                            onChargePressed = onChargePressed,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountPane(
    amountText: String,
    selectedCurrency: String?,
    currencyOptions: List<String>,
    isTabletLayout: Boolean,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val dropdownComposable: @Composable () -> Unit = {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (currencyOptions.isNotEmpty()) expanded = !expanded },
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = currencyOptions.isNotEmpty(),
                    )
                    .widthIn(min = 96.dp),
                value = selectedCurrency.orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.amount_entry_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                currencyOptions.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(currency) },
                        onClick = {
                            expanded = false
                            onCurrencySelected(currency)
                        },
                    )
                }
            }
        }
    }

    if (isTabletLayout) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = amountText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 56.sp),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(12.dp))
            dropdownComposable()
        }
    } else {
        val phoneAmountFontSize = when (amountText.length) {
            in 0..6 -> 56.sp
            in 7..8 -> 48.sp
            in 9..10 -> 40.sp
            in 11..12 -> 32.sp
            in 13..14 -> 26.sp
            else -> 22.sp
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = amountText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = phoneAmountFontSize),
                maxLines = 1,
                softWrap = false,
            )

            Spacer(modifier = Modifier.height(12.dp))
            dropdownComposable()
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeypadPane(
    isTabletLayout: Boolean,
    chargeEnabled: Boolean,
    onDigitPressed: (Char) -> Unit,
    onClearPressed: () -> Unit,
    onBackspacePressed: () -> Unit,
    onChargePressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isCompactPhone = !isTabletLayout && configuration.screenHeightDp <= 720
    val rowSpacing = if (isCompactPhone) 6.dp else 8.dp
    val digitFontSize = if (isCompactPhone) 24.sp else 28.sp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        val keyContainerColor = colorResource(R.color.amount_entry_key_background)
        val keyContentColor = colorResource(R.color.amount_entry_key_text)
        val clearLabel = stringResource(R.string.amount_entry_clear)
        val clearTextSize = when {
            clearLabel.length >= 14 -> if (isCompactPhone) 12.sp else 14.sp
            clearLabel.length >= 10 -> if (isCompactPhone) 14.sp else 16.sp
            else -> if (isCompactPhone) 16.sp else 20.sp
        }

        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                row.forEach { key ->
                    KeyButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        containerColor = keyContainerColor,
                        contentColor = keyContentColor,
                        fontSize = digitFontSize,
                        onClick = { onDigitPressed(key.first()) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            KeyButton(
                text = clearLabel,
                modifier = Modifier.weight(1f),
                containerColor = keyContainerColor,
                contentColor = keyContentColor,
                fontSize = clearTextSize,
                onClick = onClearPressed,
            )
            KeyButton(
                text = "0",
                modifier = Modifier.weight(1f),
                containerColor = keyContainerColor,
                contentColor = keyContentColor,
                onClick = { onDigitPressed('0') },
            )
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                onClick = onBackspacePressed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = keyContainerColor,
                    contentColor = keyContentColor,
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_backspace),
                    contentDescription = stringResource(R.string.amount_entry_backspace),
                    tint = keyContentColor,
                )
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            onClick = onChargePressed,
            enabled = chargeEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.colorPrimary),
                contentColor = colorResource(R.color.colorOnPrimary),
                disabledContainerColor = colorResource(R.color.colorSecondary).copy(alpha = 0.12f),
            ),
        ) {
            Text(
                text = stringResource(R.string.amount_entry_create_order_charge),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    fontSize: TextUnit? = null,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier.fillMaxSize(),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = fontSize?.let {
                MaterialTheme.typography.headlineMedium.copy(
                    fontSize = it,
                    fontWeight = FontWeight.SemiBold,
                )
            } ?: MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountEntryScreenContent(
    amountText: String,
    selectedCurrency: String,
    currencyOptions: List<String>,
    chargeEnabled: Boolean,
) {
    AmountEntryScreen(
        amountText = amountText,
        selectedCurrency = selectedCurrency,
        currencyOptions = currencyOptions,
        chargeEnabled = chargeEnabled,
        onCurrencySelected = {},
        onDigitPressed = {},
        onClearPressed = {},
        onBackspacePressed = {},
        onChargePressed = {},
    )
}
