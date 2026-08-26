/*
 * This file is part of GNU Taler
 * (C) 2023 Taler Systems S.A.
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.Merchant
import net.taler.common.Timestamp
import net.taler.lib.android.toAbsoluteTime
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.ErrorTransactionButton
import net.taler.wallet.transactions.TransactionAction
import net.taler.wallet.transactions.TransactionAction.Abort
import net.taler.wallet.transactions.TransactionAction.Retry
import net.taler.wallet.transactions.TransactionAction.Suspend
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfo
import net.taler.wallet.transactions.TransactionInfoComposable
import net.taler.wallet.transactions.TransactionLinkComposable
import net.taler.wallet.transactions.TransactionMajorState
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.TransactionMinorState
import net.taler.wallet.transactions.TransactionPayment
import net.taler.wallet.transactions.TransactionState
import net.taler.wallet.transactions.TransactionStateComposable
import net.taler.wallet.transactions.TransitionsComposable

@Composable
fun TransactionPaymentComposable(
    t: TransactionPayment,
    payStatus: PayStatus,
    devMode: Boolean,
    spec: CurrencySpecification?,
    promptMode: Boolean = false,
    modifier: Modifier = Modifier,
    onFulfill: (url: String) -> Unit,
    onTransition: (t: TransactionAction) -> Unit,
    onConfirmPay: (Int?, useDonau: Boolean) -> Unit,
    onSetupDonau: (donauBaseUrl: String) -> Unit,
    checkDonauForChoice: suspend (PayChoiceDetails) -> DonauStatus?,
) {
    if (t.txState.major == TransactionMajorState.Dialog || (
        promptMode &&
            t.txState.major == Pending &&
            t.txState.minor == TransactionMinorState.ClaimProposal
        )
    ) {
        return TransactionPaymentPrompt(
            payStatus = payStatus,
            devMode = devMode,
            modifier = modifier,
            onConfirmPay = onConfirmPay,
            onAbortPay = { onTransition(Abort) },
            onSetupDonau = onSetupDonau,
            checkDonauForChoice = checkDonauForChoice,
        )
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = CenterHorizontally,
    ) {
        TransactionStateComposable(state = t.txState)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(LocalContext.current).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        TransactionAmountComposable(
            label = stringResource(id = R.string.transaction_order_total),
            amount = t.amountRaw.withSpec(spec),
            amountType = AmountType.Neutral,
        )

        if (t.amountEffective > t.amountRaw) {
            val fee = t.amountEffective - t.amountRaw
            TransactionAmountComposable(
                label = stringResource(id = R.string.amount_fee),
                amount = fee.withSpec(spec),
                amountType = AmountType.Negative,
            )
        }

        TransactionAmountComposable(
            label = stringResource(id = R.string.transaction_paid),
            amount = t.amountEffective.withSpec(spec),
            amountType = AmountType.Negative,
        )

        if (t.posConfirmation != null) PayTotpComposable(
            totpString = t.posConfirmation,
            enableNfc = t.posConfirmationViaNfc == true,
        )

        if (t.info != null) PurchaseDetails(info = t.info) {
            onFulfill(t.info.fulfillmentUrl ?: "")
        }

        TransitionsComposable(t, devMode, onTransition)
        if (devMode && t.error != null) {
            ErrorTransactionButton(error = t.error)
        }

        BottomInsetsSpacer()
    }
}

@Composable
fun TransactionPaymentPrompt(
    payStatus: PayStatus,
    devMode: Boolean,
    modifier: Modifier = Modifier,
    onConfirmPay: (Int?, useDonau: Boolean) -> Unit,
    onAbortPay: () -> Unit,
    onSetupDonau: (donauBaseUrl: String) -> Unit,
    checkDonauForChoice: suspend (PayChoiceDetails) -> DonauStatus?,
) {
    var showImage by remember { mutableStateOf<Bitmap?>(null) }
    when (val status = payStatus) {
        is PayStatus.None,
        is PayStatus.Loading,
        is PayStatus.Prepared,
        is PayStatus.Checked -> LoadingScreen()
        is PayStatus.Choices -> PromptPaymentComposable(
            status = status,
            onConfirm = { index, useDonau ->
                onConfirmPay(index, useDonau)
            },
            onCancel = {
                onAbortPay()
            },
            onClickImage = { bitmap ->
                showImage = bitmap
            },
            checkDonauStatus = { index ->
                status.choices.find { it.choiceIndex == index }?.let { choice ->
                    checkDonauForChoice(choice)
                } ?: DonauStatus.Unavailable
            },
            onSetupDonau = { donauBaseUrl ->
                onSetupDonau(donauBaseUrl)
            },
        )

        is PayStatus.Error -> ErrorComposable(
            error = status.error,
            modifier = modifier,
            devMode = devMode,
            message = stringResource(R.string.payment_template_error),
            scrollable = false,
        )

        else -> {}
    }

    if (showImage != null) {
        ProductImageDialog(showImage!!) { showImage = null }
    }
}

@Composable
fun PurchaseDetails(
    info: TransactionInfo,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = CenterHorizontally,
    ) {
        // Summary and fulfillment message
        val text = if (info.fulfillmentMessage == null) {
            info.summary
        } else {
            "${info.summary}\n\n${info.fulfillmentMessage}"
        }
        if (info.fulfillmentUrl != null) {
            TransactionLinkComposable(
                label = stringResource(id = R.string.transaction_order),
                info = text,
            ) { onClick() }
        } else {
            TransactionInfoComposable(
                label = stringResource(id = R.string.transaction_order),
                info = text,
            )
        }
        // Order ID
        Text(
            stringResource(id = R.string.transaction_order_id, info.orderId),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ProductImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
            )
        }
    }
}

@Preview
@Composable
fun TransactionPaymentComposablePreview() {
    val t = TransactionPayment(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(Pending),
        txActions = listOf(Retry, Suspend, Abort),
        info = TransactionInfo(
            orderId = "123",
            merchant = Merchant(name = "Taler"),
            summary = "Some Product that was bought and can have quite a long label",
            fulfillmentMessage = "This is some fulfillment message",
            fulfillmentUrl = "https://bank.demo.taler.net/",
            products = listOf(),
        ),
        amountRaw = Amount.fromString("TESTKUDOS", "42.1337"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.23"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
        scopes = listOf(ScopeInfo.Exchange(
            currency = "TESTKUDOS",
            url = "exchange.test.taler.net",
        ))
    )
    TalerSurface {
        TransactionPaymentComposable(
            t = t,
            payStatus = PayStatus.None,
            devMode = true,
            spec = null,
            onFulfill = {},
            onTransition = {},
            onConfirmPay = { _, _ ->},
            onSetupDonau = {},
            checkDonauForChoice = { _ -> DonauStatus.Available },
        )
    }
}
