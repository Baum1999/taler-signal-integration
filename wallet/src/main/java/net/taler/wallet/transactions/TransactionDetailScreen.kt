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

package net.taler.wallet.transactions

import android.content.Context
import android.util.Log
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.taler.common.Amount
import net.taler.common.CurrencySpecification
import net.taler.common.Timestamp
import net.taler.lib.android.copyToClipBoard
import net.taler.lib.android.toAbsoluteTime
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.deposit.TransactionDepositComposable
import net.taler.wallet.launchInAppBrowser
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.main.TAG
import net.taler.wallet.payment.PayStatus
import net.taler.wallet.payment.TransactionPaymentComposable
import net.taler.wallet.peer.TransactionPeerPullCreditComposable
import net.taler.wallet.peer.TransactionPeerPullDebitComposable
import net.taler.wallet.peer.TransactionPeerPushCreditComposable
import net.taler.wallet.peer.TransactionPeerPushDebitComposable
import net.taler.wallet.refund.TransactionRefundComposable
import net.taler.wallet.ui.theme.TalerTheme
import net.taler.wallet.withdraw.TransactionWithdrawalComposable

@Composable
fun TransactionDetailScreen(
    model: MainViewModel,
    destination: WalletDestination,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val transactionManager = model.transactionManager
    val exchangeManager = model.exchangeManager
    val withdrawManager = model.withdrawManager
    val devMode by model.devMode.observeAsState(false)
    val context = LocalContext.current
    var keepSelectedTx: Boolean by remember { mutableStateOf(false) }

    DisposableEffect(keepSelectedTx) {
        onDispose {
            if (!keepSelectedTx)  {
                transactionManager.selectTransaction(null)
            }
        }
    }

    GlobalScaffold(
        model = model,
        title = {
            val title = when (destination) {
                is WalletDestination.TransactionPayment -> stringResource(R.string.transaction_order)
                is WalletDestination.TransactionWithdrawal -> stringResource(R.string.withdraw_title)
                is WalletDestination.TransactionDeposit -> stringResource(R.string.transaction_deposit)
                is WalletDestination.TransactionRefund -> stringResource(R.string.transaction_refund)
                is WalletDestination.TransactionRefresh -> stringResource(R.string.transaction_refresh)
                is WalletDestination.TransactionPeer -> stringResource(R.string.transaction_peer_push_debit) // Approximation
                is WalletDestination.TransactionLoss -> stringResource(R.string.transaction_denom_loss)
                else -> stringResource(R.string.transactions_detail_title)
            }
            Text(title)
        },
        onNavigateBack = onNavigateBack,
    ) { paddingValues ->
        val t by transactionManager.selectedTransaction.collectAsStateLifecycleAware()
        val modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        Box(Modifier.fillMaxSize()) {
            when (destination) {
                is WalletDestination.TransactionPayment -> {
                    (t as? TransactionPayment)?.let { tx ->
                        LaunchedEffect(tx.txState) {
                            if (tx.txState.major == TransactionMajorState.Dialog) {
                                model.paymentManager.getPaymentChoices(tx.transactionId) {}
                            }
                        }

                        TransactionPaymentComposable(
                            t = tx,
                            payStatus = model.paymentManager.payStatus
                                .observeAsState(PayStatus.None).value,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                            modifier = modifier,
                            onFulfill = { url ->
                                launchInAppBrowser(context, url)
                            },
                            onTransition = { action ->
                                handleTransactionAction(tx, action, model, onNavigateBack)
                            },
                            onConfirmPay = { choiceIndex, useDonau ->
                                model.paymentManager.confirmPay(
                                    transactionId = tx.transactionId,
                                    choiceIndex = choiceIndex,
                                    useDonau = useDonau,
                                )
                            },
                            onSetupDonau = { donauBaseUrl ->
                                onNavigate(WalletDestination.SetDonau(donauBaseUrl), false)
                            },
                            checkDonauForChoice = { choice ->
                                model.paymentManager.checkDonauForChoice(choice)
                            }
                        )
                    }
                }

                is WalletDestination.TransactionWithdrawal -> {
                    (t as? TransactionWithdrawal)?.let { tx ->
                        val qrCode = remember(tx) {
                            (tx.withdrawalDetails as? WithdrawalDetails.ManualTransfer)?.let { details ->
                                if (details.exchangeCreditAccountDetails?.size == 1) {
                                    val account0 = details.exchangeCreditAccountDetails[0]
                                    val qrCodes = withdrawManager.getQrCodesForPayto(account0.paytoUri)
                                    if (qrCodes.size == 1) qrCodes[0]
                                    else null
                                } else null
                            }
                        }

                        TransactionWithdrawalComposable(
                            modifier = modifier,
                            t = tx,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                            qrCode = qrCode,
                            onConfirmKyc = { url ->
                                launchInAppBrowser(context, url)
                            },
                            onConfirmBank = {
                                if (tx.withdrawalDetails is WithdrawalDetails.TalerBankIntegrationApi) {
                                    tx.withdrawalDetails.bankConfirmationUrl?.let { url ->
                                        launchInAppBrowser(context, url)
                                    }
                                }
                            },
                            onConfirmManual = {
                                keepSelectedTx = true
                                onNavigate(WalletDestination.WireTransferDetails(false), false)
                            },
                            onShowQrCodes = {
                                keepSelectedTx = true
                                onNavigate(WalletDestination.WireTransferDetails(true), false)
                            },
                            onTransition = { action ->
                                handleTransactionAction(tx, action, model, onNavigateBack)
                            }
                        )
                    }
                }

                is WalletDestination.TransactionDeposit -> {
                    (t as? TransactionDeposit)?.let { tx ->
                        TransactionDepositComposable(
                            modifier = modifier,
                            t = tx,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                            onWireTransfer = {
                                keepSelectedTx = true
                                onNavigate(
                                    WalletDestination.WireTransferDetails(false),
                                    false
                                )
                            },
                            onShowQrCodes = {
                                keepSelectedTx = true
                                onNavigate(
                                    WalletDestination.WireTransferDetails(true),
                                    false
                                )
                            },
                            onTransition = {
                                handleTransactionAction(tx, it, model, onNavigateBack)
                            },
                        )
                    }
                }

                is WalletDestination.TransactionRefund -> {
                    (t as? TransactionRefund)?.let { tx ->
                        TransactionRefundComposable(
                            modifier = modifier,
                            t = tx,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                            onTransition = {
                                handleTransactionAction(tx, it, model, onNavigateBack)
                            },
                        )
                    }
                }

                is WalletDestination.TransactionRefresh -> {
                    (t as? TransactionRefresh)?.let { tx ->
                        TransactionRefreshComposable(
                            modifier = modifier,
                            t = tx,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                        ) {
                            handleTransactionAction(tx, it, model, onNavigateBack)
                        }
                    }
                }

                is WalletDestination.TransactionPeer -> {
                    val tx = t
                    if (tx != null) {
                        TransactionPeerComposable(
                            modifier = modifier,
                            t = tx,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes),
                            onConfirmKyc = { url -> launchInAppBrowser(context, url) }
                        ) {
                            handleTransactionAction(tx, it, model, onNavigateBack)
                        }
                    }
                }

                is WalletDestination.TransactionLoss -> {
                    (t as? TransactionDenomLoss)?.let { tx ->
                        TransitionLossComposable(
                            modifier = modifier,
                            t = tx,
                            devMode = devMode,
                            spec = exchangeManager.getSpecForCurrency(tx.amountRaw.currency, tx.scopes)
                        ) {
                            handleTransactionAction(tx, it, model, onNavigateBack)
                        }
                    }
                }

                is WalletDestination.TransactionDummy -> {
                    (t as? DummyTransaction)?.let { tx ->
                        TransactionDummyComposable(
                            modifier = modifier,
                            t = tx,
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
fun TransactionRefreshComposable(
    t: TransactionRefresh,
    devMode: Boolean,
    spec: CurrencySpecification?,
    modifier: Modifier = Modifier,
    onTransition: (t: TransactionAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransactionStateComposable(state = t.txState)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(LocalContext.current).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_fee),
            amount = t.amountEffective.withSpec(spec),
            amountType = AmountType.Negative,
        )

        TransitionsComposable(t, devMode, onTransition)
        t.error?.let { error ->
            if (devMode) {
                ErrorTransactionButton(error = error)
            }
        }

        BottomInsetsSpacer()
    }
}

@Composable
fun TransactionPeerComposable(
    t: Transaction,
    devMode: Boolean,
    spec: CurrencySpecification?,
    modifier: Modifier = Modifier,
    onConfirmKyc: (url: String) -> Unit,
    onTransition: (t: TransactionAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransactionStateComposable(state = t.txState)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(LocalContext.current).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        when (t) {
            is TransactionPeerPullCredit -> TransactionPeerPullCreditComposable(t, spec, onConfirmKyc)
            is TransactionPeerPushCredit -> TransactionPeerPushCreditComposable(t, spec, onConfirmKyc)
            is TransactionPeerPullDebit -> TransactionPeerPullDebitComposable(t, spec)
            is TransactionPeerPushDebit -> TransactionPeerPushDebitComposable(t, spec)
            else -> {}
        }

        TransitionsComposable(t, devMode, onTransition)

        t.error?.let { error ->
            if (devMode) {
                ErrorTransactionButton(error = error)
            }
        }

        BottomInsetsSpacer()
    }
}

@Composable
fun TransitionLossComposable(
    t: TransactionDenomLoss,
    devMode: Boolean,
    spec: CurrencySpecification?,
    modifier: Modifier = Modifier,
    onTransition: (t: TransactionAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransactionStateComposable(state = t.txState)

        Text(
            modifier = Modifier.padding(16.dp),
            text = t.timestamp.ms.toAbsoluteTime(LocalContext.current).toString(),
            style = MaterialTheme.typography.bodyLarge,
        )

        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_lost),
            amount = t.amountEffective.withSpec(spec),
            amountType = AmountType.Negative,
        )

        TransactionInfoComposable(
            label = stringResource(id = R.string.loss_reason),
            info = stringResource(
                when(t.lossEventType) {
                    LossEventType.DenomExpired -> R.string.loss_reason_expired
                    LossEventType.DenomVanished -> R.string.loss_reason_vanished
                    LossEventType.DenomUnoffered -> R.string.loss_reason_unoffered
                }
            )
        )

        TransitionsComposable(t, devMode, onTransition)

        t.error?.let { error ->
            if (devMode) {
                ErrorTransactionButton(error = error)
            }
        }

        BottomInsetsSpacer()
    }
}

@Composable
fun TransactionDummyComposable(
    t: DummyTransaction,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ErrorTransactionButton(error = t.error)
        BottomInsetsSpacer()
    }
}

@Composable
fun TransactionAmountComposable(
    label: String,
    amount: Amount,
    amountType: AmountType,
    context: Context? = null,
    copy: Boolean = false,
) {
    Text(
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
        text = label,
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .padding(
                    top = 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ).weight(1f),
            text = amount.toString(negative = amountType == AmountType.Negative),
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = when (amountType) {
                AmountType.Positive -> TalerTheme.extraColors.success
                AmountType.Negative -> MaterialTheme.colorScheme.error
                AmountType.Neutral -> Color.Unspecified
            },
        )

        if (copy) {
            TextButton(
                onClick = { context?.let {
                    copyToClipBoard(context, label, amount.toString(showSymbol = false))
                }  },
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.copy))
            }
        }
    }
}

@Composable
fun TransactionInfoComposable(
    label: String,
    info: String,
    marquee: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Text(
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
        text = label,
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(
        modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = if (marquee) Modifier.basicMarquee() else Modifier,
            text = info,
            fontSize = 24.sp,
        )

        trailing?.let { it() }
    }
}

private fun handleTransactionAction(
    tx: Transaction,
    action: TransactionAction,
    model: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val transactionManager = model.transactionManager
    val onError: (TalerErrorInfo) -> Unit = { error ->
        Log.e(TAG, "Error handling transaction action $action: $error")
    }

    when (action) {
        TransactionAction.Delete -> transactionManager.deleteTransaction(tx.transactionId, onError).invokeOnCompletion { onNavigateBack() }
        TransactionAction.Retry -> transactionManager.retryTransaction(tx.transactionId, onError)
        TransactionAction.Abort -> transactionManager.abortTransaction(tx.transactionId, { onNavigateBack() }, onError)
        TransactionAction.Fail -> transactionManager.failTransaction(tx.transactionId, onError)
        TransactionAction.Suspend -> transactionManager.suspendTransaction(tx.transactionId, onError)
        TransactionAction.Resume -> transactionManager.resumeTransaction(tx.transactionId, onError)
    }
}

@Preview
@Composable
fun TransactionRefreshComposablePreview() {
    val t = TransactionRefresh(
        transactionId = "transactionId",
        timestamp = Timestamp.fromMillis(System.currentTimeMillis() - 360 * 60 * 1000),
        txState = TransactionState(TransactionMajorState.Pending),
        txActions = listOf(TransactionAction.Retry, TransactionAction.Suspend, TransactionAction.Abort),
        amountRaw = Amount.fromString("TESTKUDOS", "42.23"),
        amountEffective = Amount.fromString("TESTKUDOS", "42.1337"),
        error = TalerErrorInfo(code = TalerErrorCode.WALLET_WITHDRAWAL_KYC_REQUIRED),
        scopes = listOf(ScopeInfo.Exchange(
            currency = "TESTKUDOS",
            url = "exchange.test.taler.net",
        ))
    )
    Surface {
        TransactionRefreshComposable(t, true, null) {}
    }
}
