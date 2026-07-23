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

package net.taler.wallet

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import net.taler.wallet.accounts.AddBankAccountScreen
import net.taler.wallet.accounts.BankAccountsScreen
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.deposit.DepositScreen
import net.taler.wallet.deposit.PayToUriScreen
import net.taler.wallet.donau.DonauStatementScreen
import net.taler.wallet.donau.SetDonauScreen
import net.taler.wallet.exchanges.ExchangeListScreen
import net.taler.wallet.exchanges.ExchangeShoppingScreen
import net.taler.wallet.exchanges.ReviewExchangeTosScreen
import net.taler.wallet.main.MainScreen
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.payment.PayTemplateScreen
import net.taler.wallet.peer.IncomingPullPaymentScreen
import net.taler.wallet.peer.IncomingPushPaymentScreen
import net.taler.wallet.peer.OutgoingPullScreen
import net.taler.wallet.peer.OutgoingPushScreen
import net.taler.wallet.settings.PerformanceStatsScreen
import net.taler.wallet.tokens.TokenListScreen
import net.taler.wallet.tokens.TokenViewMode
import net.taler.wallet.transactions.TransactionDetailScreen
import net.taler.wallet.transfer.WireTransferDetailsScreen
import net.taler.wallet.withdraw.PromptWithdrawScreen

typealias NavigateCallback = (
    dest: WalletDestination,
    popupToStart: Boolean,
) -> Unit

@Composable
fun WalletNavHost(
    navController: NavHostController,
    model: MainViewModel,
    modifier: Modifier = Modifier,
    onScanQr: () -> Unit,
    onFulfillPayment: (url: String) -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val startDestination = WalletDestination.Main

    val onNavigate: NavigateCallback = { dest, popupToStart ->
        if (popupToStart) {
            navController.navigate(WalletDestination.Main) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
        navController.navigate(dest)
    }

    val onNavigateBack: () -> Unit = {
        (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            keyboardController?.hide()
            focusManager.clearFocus()
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
        },
        popEnterTransition = {
            fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        composable<WalletDestination.Main> {
            MainScreen(
                model = model,
                onNavigate = onNavigate,
                onScanQr = onScanQr,
                onFulfillPayment = onFulfillPayment,
                onShowError = onShowError,
            )
        }
        composable<WalletDestination.HandleUri> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.HandleUri>()
            HandleUriScreen(
                model = model,
                uriString = dest.uri,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                onShowError = onShowError,
            )
        }
        composable<WalletDestination.PromptWithdraw> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.PromptWithdraw>()
            PromptWithdrawScreen(
                model = model,
                dest = dest,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.ExchangeList> {
            ExchangeListScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.BankAccounts> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.BankAccounts>()
            BankAccountsScreen(
                model = model,
                currency = dest.currency,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.AddBankAccount> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.AddBankAccount>()
            AddBankAccountScreen(
                model = model,
                bankAccountId = dest.bankAccountId,
                onNavigateBack = onNavigateBack,
                onShowError = onShowError,
            )
        }
        composable<WalletDestination.ReviewExchangeTOS> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.ReviewExchangeTOS>()
            ReviewExchangeTosScreen(
                model = model,
                exchangeBaseUrl = dest.exchangeBaseUrl,
                readOnly = dest.readOnly,
                onNavigateBack = onNavigateBack
            )
        }
        composable<WalletDestination.PaytoUri> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.PaytoUri>()
            PayToUriScreen(
                model = model,
                uri = dest.uri,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.Deposit> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.Deposit>()
            DepositScreen(
                model = model,
                dest = dest,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.OutgoingPush> {
            OutgoingPushScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                onShowError = { onShowError(it) }
            )
        }
        composable<WalletDestination.OutgoingPull> {
            OutgoingPullScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                onShowError = { onShowError(it) }
            )
        }
        composable<WalletDestination.PromptPullPayment> {
            IncomingPullPaymentScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                onShowError = { onShowError(it) }
            )
        }
        composable<WalletDestination.PromptPushPayment> {
            IncomingPushPaymentScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                onShowError = { onShowError(it) }
            )
        }
        composable<WalletDestination.DiscountList> {
            TokenListScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                viewMode = TokenViewMode.Discounts,
            )
        }
        composable<WalletDestination.PassList> {
            TokenListScreen(
                model = model,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                viewMode = TokenViewMode.Passes,
            )
        }
        composable<WalletDestination.SetDonau> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.SetDonau>()
            SetDonauScreen(
                model = model,
                donauBaseUrl = dest.donauBaseUrl,
                onShowMessage = { /* TODO */ },
                onShowError = { onShowError(it) },
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.DonauStatement> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.DonauStatement>()
            DonauStatementScreen(
                model = model,
                host = dest.host,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionPayment> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionPayment,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionWithdrawal> {
        TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionWithdrawal,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionRefund> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionRefund,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionRefresh> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionRefresh,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionDeposit> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionDeposit,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionPeer> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionPeer,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionLoss> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionLoss,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.TransactionDummy> {
            TransactionDetailScreen(
                model = model,
                destination = WalletDestination.TransactionDummy,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.PromptPayTemplate> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.PromptPayTemplate>()
            PayTemplateScreen(
                model = model,
                uri = dest.uri,
                onNavigate = onNavigate,
                onNavigateBack = onNavigateBack,
                onShowError = { onShowError(it) }
            )
        }
        composable<WalletDestination.ExchangeShopping> {
            ExchangeShoppingScreen(
                model = model,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.WireTransferDetails> { backStackEntry ->
            val dest = backStackEntry.toRoute<WalletDestination.WireTransferDetails>()
            WireTransferDetailsScreen(
                model = model,
                showQrCodes = dest.showQrCodes,
                onNavigateBack = onNavigateBack,
            )
        }
        composable<WalletDestination.PerformanceStats> {
            PerformanceStatsScreen(
                model = model,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}