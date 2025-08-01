/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.wallet.payment

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.ContractChoice
import net.taler.common.ContractInput
import net.taler.common.ContractOutput
import net.taler.common.ContractProduct
import net.taler.common.ContractTerms
import net.taler.common.ContractTokenDetails
import net.taler.common.ContractTokenFamily
import net.taler.common.Exchange
import net.taler.common.Merchant
import net.taler.common.TalerUtils
import net.taler.common.base64Bitmap
import net.taler.wallet.R
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.compose.ExpandableSection
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.cardPaddings
import net.taler.wallet.payment.GetChoicesForPaymentResponse.ChoiceSelectionDetail.InsufficientBalance
import net.taler.wallet.payment.GetChoicesForPaymentResponse.ChoiceSelectionDetail.PaymentPossible
import net.taler.wallet.payment.TokenAvailabilityHint.MerchantUnexpected
import net.taler.wallet.payment.TokenAvailabilityHint.MerchantUntrusted
import net.taler.wallet.payment.TokenAvailabilityHint.WalletTokensAvailableInsufficient
import net.taler.wallet.systemBarsPaddingBottom

// TODO: error handling
// TODO: info sheets

@Composable
fun PromptPaymentComposable(
    status: PayStatus.Choices,
    onConfirm: (choiceIndex: Int?) -> Unit,
    onCancel: () -> Unit,
    onClickImage: (Bitmap) -> Unit,
) {
    val contractTerms = status.contractTerms
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    OrderCancelDialog(
        showCancelDialog,
        onDismiss = { showCancelDialog = false },
        onConfirm = { onCancel() },
    )

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            MerchantSection(contractTerms, onClickImage)

            // REVIEW ORDER SECTION
            var orderExpanded by rememberSaveable {
                mutableStateOf(contractTerms is ContractTerms.V0)
            }

            ExpandableSection(
                expanded = orderExpanded,
                setExpanded = { orderExpanded = it },
                header = { Text(stringResource(R.string.payment_section_review)) },
            ) {
                OrderSection(contractTerms, onClickImage)
            }

            // PAYMENT OPTIONS SECTION
            if (contractTerms is ContractTerms.V1) {
                var choicesExpanded by rememberSaveable { mutableStateOf(true) }
                var selectedIndex by rememberSaveable { mutableIntStateOf(status.defaultChoiceIndex ?: 0) }
                ExpandableSection(
                    expanded = choicesExpanded,
                    setExpanded = { choicesExpanded = it },
                    header = { Text(stringResource(R.string.payment_section_choices)) },
                ) {
                    ChoicesSection(
                        status,
                        contractTerms.tokenFamilies,
                        selectedIndex,
                        contractTerms.merchantBaseUrl,
                        onSelect = { index -> selectedIndex = index },
                        onConfirm = { index -> onConfirm(index) },
                    )
                }
            }
        }

        BottomButtonBox(Modifier.fillMaxWidth(),
            heading = if (contractTerms is ContractTerms.V0) { ->
                val choice = status.choices.firstOrNull()
                    ?: error("no v0 choice")

                Text(
                    stringResource(
                        R.string.payment_amount_total,
                        choice.details.amountRaw,
                    )
                )

                if (choice.details is PaymentPossible) {
                    PaymentFeeLabel(
                        modifier = Modifier.padding(bottom = 3.dp),
                        amountRaw = choice.details.amountRaw,
                        amountEffective = choice.details.amountEffective,
                    )
                } else if (choice.details is InsufficientBalance) {
                    Text(
                        modifier = Modifier.padding(bottom = 3.dp),
                        text = stringResource(R.string.payment_balance_insufficient),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else null,
            leading = {
                OutlinedButton(
                    modifier = Modifier.systemBarsPaddingBottom(),
                    enabled = true,
                    onClick = { showCancelDialog = true },
                ) {
                    Text(
                        stringResource(R.string.payment_button_cancel),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            trailing = {
                if (contractTerms is ContractTerms.V0) {
                    val choice = status.choices.firstOrNull()
                        ?: error("no v0 choice")
                    Button(
                        modifier = Modifier.systemBarsPaddingBottom(),
                        enabled = choice.details is PaymentPossible,
                        onClick = { onConfirm(null) },
                    ) {
                        if (choice.details is PaymentPossible) {
                            Text(stringResource(
                                R.string.payment_button_confirm_amount,
                                choice.details.amountEffective,
                            ))
                        } else {
                            Text(stringResource(
                                R.string.payment_button_confirm_amount,
                                choice.details.amountRaw,
                            ))
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun MerchantSection(
    contractTerms: ContractTerms,
    onClickImage: (Bitmap) -> Unit,
) {
    val merchant = contractTerms.merchant

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalAlignment = CenterHorizontally,
    ) {
        // MERCHANT LOGO
        val logo = remember(merchant.logo) {
            merchant.logo?.base64Bitmap
        }

        Box(
            Modifier
                .size(60.dp)
                .background(
                    shape = CircleShape,
                    color = if (logo != null) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .clip(CircleShape)
                .clickable { if (logo != null) onClickImage(logo) },
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                Image(
                    logo.asImageBitmap(),
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null,
                )
            } else {
                Icon(
                    Icons.Default.Store,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            }
        }

        // MERCHANT NAME
        Text(
            merchant.name,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        // MERCHANT INFO BUTTON
        // TextButton(onClick = {}) {
        //     Icon(
        //         Icons.Outlined.Info,
        //         contentDescription = null,
        //         modifier = Modifier.size(ButtonDefaults.IconSize),
        //     )
        //     Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        //     Text("Merchant info")
        // }
    }
}

@Composable
fun MerchantInfoSheet() {}

@Composable
fun OrderCancelDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (show) AlertDialog(
        title = { Text(stringResource(R.string.payment_cancel_dialog_title)) },
        text = { Text(stringResource(R.string.payment_cancel_dialog_message)) },
        onDismissRequest = { onDismiss() },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text(stringResource(R.string.button_back))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onConfirm()
            }) {
                Text(stringResource(R.string.payment_cancel_dialog_title))
            }
        },
    )
}

@Composable
fun OrderSection(
    contractTerms: ContractTerms,
    onClickImage: (Bitmap) -> Unit,
) {
    Column(horizontalAlignment = CenterHorizontally) {
        // ORDER SUMMARY
        Text(
            contractTerms.summary,
            modifier = Modifier
                .padding(top = 3.dp, bottom = 12.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )

        // PRODUCT LIST
        // TODO: LazyColumn would be better, but can't be nested
        val products = contractTerms.products
        products?.forEach { product ->
            ProductItem(product, onClickImage)
        }

        // ORDER INFO BUTTON
        // TextButton(
        //     onClick = {},
        //     modifier = Modifier.padding(bottom = 9.dp),
        // ) {
        //     Icon(
        //         Icons.Outlined.Info,
        //         contentDescription = null,
        //         modifier = Modifier.size(ButtonDefaults.IconSize),
        //     )
        //     Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        //     Text("More details")
        // }
    }
}

@Composable
fun ProductItem(
    product: ContractProduct,
    onClickImage: (Bitmap) -> Unit,
) {
    val image = remember(product.image) {
        product.image?.base64Bitmap
    }

    ListItem(
        leadingContent = {
            // IMAGE
            if (image != null) {
                Image(
                    image.asImageBitmap(),
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { onClickImage(image) },
                    contentDescription = null,
                )
            }
        },

        headlineContent = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                // NAME
                Text(
                    product.localizedDescription,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )

                // PRICE
                product.price?.let { price ->
                    Text(
                        if (product.quantity > 1) {
                            stringResource(
                                R.string.payment_product_price_quantity,
                                product.quantity.toString(),
                                price.toString(),
                            )
                        } else {
                            price.toString()
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ListItemDefaults.colors().supportingTextColor,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
fun ChoicesSection(
    status: PayStatus.Choices,
    tokenFamilies: Map<String, ContractTokenFamily>,
    selectedIndex: Int,
    merchantBaseUrl: String,
    onSelect: (choiceIndex: Int) -> Unit,
    onConfirm: (choiceIndex: Int) -> Unit,
) {
    // TODO: CURRENCIES

    // CHOICES
    // TODO: LazyColumn would be better, but can't be nested
    status.choices.forEach { choice ->
        PaymentChoice(
            choice,
            tokenFamilies,
            merchantBaseUrl,
            selectedIndex == choice.choiceIndex,
            onSelect = { onSelect(choice.choiceIndex) },
            onConfirm = { onConfirm(choice.choiceIndex) },
        )
    }
}

@Composable
fun PaymentChoice(
    choice: PayChoiceDetails,
    tokenFamilies: Map<String, ContractTokenFamily>,
    merchantBaseUrl: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onConfirm: () -> Unit,
) {
    OutlinedCard(
        modifier =  Modifier
            .cardPaddings()
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onSelect() },
        border = if (selected) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        Column(Modifier) {
            Column(Modifier.padding(15.dp)) {
                // CHOICE AMOUNT
                Text(
                    text = if (choice.amountRaw.isZero()) {
                        stringResource(R.string.payment_button_confirm_tokens)
                    } else {
                        choice.amountRaw.toString()
                    },
                    style = MaterialTheme.typography.titleLarge,
                )

                if (choice.details is PaymentPossible) {
                    PaymentFeeLabel(
                        amountRaw = choice.details.amountRaw,
                        amountEffective = choice.details.amountEffective,
                    )
                }

                choice.localizedDescription?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 9.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                // INPUTS
                if (choice.inputs.isNotEmpty()) {
                    Column {
                        Text(
                            stringResource(R.string.payment_choice_inputs),
                            modifier = Modifier.padding(top = 9.dp, bottom = 3.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        choice.inputs.forEach { input ->
                            PaymentInput(input, merchantBaseUrl, tokenFamilies, choice.details.tokenDetails)
                        }
                    }
                }

                // OUTPUTS
                if (choice.outputs.isNotEmpty()) {
                    Column {
                        Text(
                            stringResource(R.string.payment_choice_outputs),
                            modifier = Modifier.padding(top = 9.dp, bottom = 3.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        choice.outputs.forEach { output ->
                            PaymentOutput(output, tokenFamilies, merchantBaseUrl)
                        }
                    }
                }

                // CONFIRM BUTTON
                if (selected) Button(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .fillMaxWidth(),
                    onClick = onConfirm,
                    enabled = choice.details is PaymentPossible,
                ) {
                    val tokenDetails = choice.details.tokenDetails
                    Text(
                        if (choice.details is PaymentPossible) {
                            if (choice.details.amountEffective.isZero()) {
                                stringResource(R.string.payment_button_confirm_tokens)
                            } else {
                                stringResource(
                                    R.string.payment_button_confirm_amount,
                                    choice.details.amountEffective
                                )
                            }
                        } else if (tokenDetails != null &&
                            tokenDetails.tokensAvailable
                            < tokenDetails.tokensRequested) {
                            stringResource(
                                R.string.payment_tokens_insufficient,
                            )
                        } else {
                            stringResource(
                                R.string.payment_balance_insufficient,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentInput(
    input: ContractInput,
    merchantBaseUrl: String,
    tokenFamilies: Map<String, ContractTokenFamily>,
    tokenAvailabilityDetails: PaymentTokenAvailabilityDetails?,
) {
    when (input) {
        is ContractInput.Token -> {
            // TODO: calculate from outside?
            // TODO: better error handling
            val family = tokenFamilies[input.tokenFamilySlug]
                ?: error("no token family ${input.tokenFamilySlug}")

            val availability = tokenAvailabilityDetails?.perTokenFamily
                ?.get(input.tokenFamilySlug)

            TokenCard(
                name = family.name,
                description = TalerUtils.getLocalizedString(
                    family.descriptionI18n,
                    family.description,
                ),
                count = input.count,
                details = family.details,
                merchantBaseUrl = merchantBaseUrl,
                availabilityHint = availability?.causeHint,
            )
        }
    }
}

@Composable
fun PaymentOutput(
    output: ContractOutput,
    tokenFamilies: Map<String, ContractTokenFamily>,
    merchantBaseUrl: String,
) {
    when (output) {
        is ContractOutput.Token -> {
            // TODO: calculate from outside?
            // TODO: better error handling
            val family = tokenFamilies[output.tokenFamilySlug]
                ?: error("no token family for ${output.tokenFamilySlug}")

            TokenCard(
                name = family.name,
                description = TalerUtils.getLocalizedString(
                    family.descriptionI18n,
                    family.description,
                ),
                count = output.count,
                details = family.details,
                merchantBaseUrl = merchantBaseUrl,
            )
        }
    }
}

@Composable
fun TokenCard(
    name: String,
    description: String,
    count: Int,
    details: ContractTokenDetails,
    merchantBaseUrl: String,
    availabilityHint: TokenAvailabilityHint? = null,
) {
    Card (
        modifier = Modifier
            .padding(vertical = 5.dp,
            ).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (details) {
                    is ContractTokenDetails.Discount -> Icon(
                        Icons.Default.Percent,
                        contentDescription = stringResource(R.string.payment_token_discount),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is ContractTokenDetails.Subscription -> Icon(
                        Icons.Default.Autorenew,
                        contentDescription = stringResource(R.string.payment_token_subscription),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    if (count > 1) {
                        stringResource(R.string.payment_product_price_quantity, count, name)
                    } else {
                        name
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )

                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (availabilityHint != null) {
                    TokenWarningTooltip(merchantBaseUrl, availabilityHint)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenWarningTooltip(
    merchantBaseUrl: String,
    availabilityHint: TokenAvailabilityHint,
) {
    val icon = when(availabilityHint) {
        WalletTokensAvailableInsufficient -> Icons.Default.Error
        MerchantUntrusted -> Icons.Default.Error
        MerchantUnexpected -> Icons.Default.Warning
        else -> return
    }

    val tint = when(availabilityHint) {
        WalletTokensAvailableInsufficient -> MaterialTheme.colorScheme.error
        MerchantUntrusted -> MaterialTheme.colorScheme.error
        MerchantUnexpected -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }

    val text = when(availabilityHint) {
        WalletTokensAvailableInsufficient -> stringResource(R.string.payment_tokens_insufficient)
        MerchantUntrusted -> stringResource(R.string.payment_tokens_untrusted, cleanExchange(merchantBaseUrl))
        MerchantUnexpected -> stringResource(R.string.payment_tokens_unexpected, cleanExchange(merchantBaseUrl))
        else -> return
    }

    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()

    TooltipBox(
        modifier = Modifier,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState,
    ) {
        IconButton(onClick = {
            coroutineScope.launch {
                tooltipState.show()
            }
        }) {
            Icon(
                icon,
                tint = tint,
                contentDescription = when(availabilityHint) {
                    WalletTokensAvailableInsufficient -> stringResource(R.string.error)
                    MerchantUntrusted -> stringResource(R.string.error)
                    MerchantUnexpected -> stringResource(R.string.warning)
                    else -> return@IconButton
                },
            )
        }
    }
}

@Composable
fun PaymentFeeLabel(
    modifier: Modifier = Modifier,
    amountRaw: Amount,
    amountEffective: Amount,
) {
    if (amountEffective > amountRaw) {
        val fee = amountEffective - amountRaw
        Text(
            modifier = modifier,
            text = stringResource(
                R.string.payment_fee,
                fee.toString(showSymbol = false),
            ),
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

private val contractTermsV0 = ContractTerms.V0(
    merchant = Merchant(
        name = "Demo Shop",
        logo = "data:image/png;base64,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",
    ),
    summary = "order summary",
    orderId = "102910291029",
    merchantBaseUrl = "https://backend.demo.taler.net/",
    amount = Amount.fromJSONString("KUDOS:1"),
    maxFee = Amount.fromJSONString("KUDOS:0"),
    products = listOf(
        ContractProduct(
            description = "something",
            price = Amount.fromJSONString("KUDOS:1"),
            image = "data:image/png;base64,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",
            quantity = 2,
        ),

        ContractProduct(
            description = "something",
            price = Amount.fromJSONString("KUDOS:1"),
            image = "data:image/png;base64,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",
            quantity = 1,
        ),
    ),
    exchanges = listOf(
        Exchange(url = "https://exchange.demo.taler.net/"),
        Exchange(url = "https://exchange.test.taler.net/"),
    ),
)

private val contractTermsV1 = ContractTerms.V1(
    merchant = contractTermsV0.merchant,
    summary = contractTermsV0.summary,
    orderId = contractTermsV0.orderId,
    merchantBaseUrl =  contractTermsV0.merchantBaseUrl,
    products = contractTermsV0.products,
    exchanges = contractTermsV0.exchanges,

    choices = listOf(
        ContractChoice(
            amount = Amount.fromJSONString("KUDOS:10"),
            description = "Movie pass discount",
            maxFee = Amount.fromJSONString("KUDOS:0"),
            inputs = listOf(
                ContractInput.Token(tokenFamilySlug = "half-tax", count = 2),
                ContractInput.Token(tokenFamilySlug = "movie-pass"),
            ),
            outputs = listOf(
                ContractOutput.Token(tokenFamilySlug = "movie-pass"),
            ),
        ),

        ContractChoice(
            amount = Amount.fromJSONString("KUDOS:200"),
            description = "Movie pass access renewal",
            maxFee = Amount.fromJSONString("KUDOS:0"),
            inputs = listOf(
                ContractInput.Token(tokenFamilySlug = "movie-pass"),
            ),
            outputs = listOf(
                ContractOutput.Token(tokenFamilySlug = "movie-pass"),
            ),
        ),

        ContractChoice(
            amount = Amount.fromJSONString("KUDOS:0"),
            description = "Movie pass access renewal",
            maxFee = Amount.fromJSONString("KUDOS:0"),
            inputs = listOf(
                ContractInput.Token(tokenFamilySlug = "movie-pass"),
            ),
            outputs = listOf(
                ContractOutput.Token(tokenFamilySlug = "movie-pass"),
            ),
        ),
    ),

    tokenFamilies = mapOf(
        "half-tax" to ContractTokenFamily(
            name = "half-tax",
            description = "50% discount",
            details = ContractTokenDetails.Discount,
            critical = true,
        ),

        "movie-pass" to ContractTokenFamily(
            name = "movie-pass",
            description = "Monthly movie pass",
            details = ContractTokenDetails.Subscription,
            critical = true,
        ),
    ),
)

@Preview
@Composable
fun PromptPaymentV0Preview() {
    TalerSurface {
        PromptPaymentComposable(PayStatus.Choices(
            transactionId = "txn:payment:2309203920",
            contractTerms = contractTermsV0,
            choices = listOf(
                PayChoiceDetails(
                    choiceIndex = 0,
                    amountRaw = Amount.fromJSONString("KUDOS:10"),
                    inputs = listOf(),
                    outputs = listOf(),
                    details = PaymentPossible(
                        amountRaw = Amount.fromJSONString("KUDOS:10"),
                        amountEffective = Amount.fromJSONString("KUDOS:10.2"),
                    ),
                )
            )
        ), {}, {}, {})
    }
}

@Preview
@Composable
fun PromptPaymentV1Preview() {
    TalerSurface {
        PromptPaymentComposable(PayStatus.Choices(
            transactionId = "txn:payment:2309203920",
            contractTerms = contractTermsV1,
            defaultChoiceIndex = 2,
            choices = listOf(
                PayChoiceDetails(
                    choiceIndex = 0,
                    amountRaw = contractTermsV1.choices[0].amount,
                    description = "Movie pass discount",
                    inputs = contractTermsV1.choices[0].inputs,
                    outputs = contractTermsV1.choices[0].outputs,
                    details = PaymentPossible(
                        amountRaw = contractTermsV1.choices[0].amount,
                        amountEffective = contractTermsV1.choices[0].amount
                            .plus(Amount.fromJSONString("KUDOS:0.1")),
                        tokenDetails = PaymentTokenAvailabilityDetails(
                            tokensRequested = 3,
                            tokensAvailable = 2,
                            tokensUntrusted = 1,
                            tokensUnexpected = 1,
                            perTokenFamily = mapOf(
                                "half-tax" to PaymentTokenAvailabilityDetails.PerTokenFamily(
                                    causeHint = MerchantUntrusted,
                                    requested = 2,
                                    available = 1,
                                    untrusted = 1,
                                    unexpected = 0
                                ),
                                "movie-pass" to PaymentTokenAvailabilityDetails.PerTokenFamily(
                                    causeHint = MerchantUnexpected,
                                    requested = 1,
                                    available = 1,
                                    untrusted = 0,
                                    unexpected = 1,
                                ),
                            ),
                        ),
                    ),
                ),
                PayChoiceDetails(
                    choiceIndex = 1,
                    amountRaw = contractTermsV1.choices[1].amount,
                    description = "Movie pass access renewal",
                    inputs = contractTermsV1.choices[1].inputs,
                    outputs = contractTermsV1.choices[1].outputs,
                    details = InsufficientBalance(
                        amountRaw = contractTermsV1.choices[1].amount,
                    ),
                ),
                PayChoiceDetails(
                    choiceIndex = 2,
                    amountRaw = contractTermsV1.choices[2].amount,
                    description = "Movie pass access renewal",
                    inputs = contractTermsV1.choices[2].inputs,
                    outputs = contractTermsV1.choices[2].outputs,
                    details = PaymentPossible(
                        amountRaw = contractTermsV1.choices[2].amount,
                        amountEffective = contractTermsV1.choices[2].amount,
                    ),
                ),
            )
        ), {}, {}, {})
    }
}