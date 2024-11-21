/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
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

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.common.Event
import net.taler.common.toEvent
import net.taler.wallet.accounts.AccountManager
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.NotificationPayload
import net.taler.wallet.backend.NotificationReceiver
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.VersionReceiver
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletCoreVersion
import net.taler.wallet.backend.WalletRunConfig
import net.taler.wallet.backend.WalletRunConfig.Testing
import net.taler.wallet.balances.BalanceManager
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.deposit.DepositManager
import net.taler.wallet.events.ObservabilityEvent
import net.taler.wallet.exchanges.ExchangeManager
import net.taler.wallet.payment.PaymentManager
import net.taler.wallet.peer.PeerManager
import net.taler.wallet.refund.RefundManager
import net.taler.wallet.settings.SettingsManager
import net.taler.wallet.settings.userPreferencesDataStore
import net.taler.wallet.transactions.TransactionManager
import net.taler.wallet.withdraw.WithdrawManager
import org.json.JSONObject

const val TAG = "taler-wallet"
const val OBSERVABILITY_LIMIT = 100

private val transactionNotifications = listOf(
    "transaction-state-transition",
)

private val observabilityNotifications = listOf(
    "task-observability-event",
    "request-observability-event",
)

private val sendUriActions = listOf(
    "pay",
    "tip",
    "pay-pull",
    "pay-template",
)

private val receiveUriActions = listOf(
    "withdraw",
    "refund",
    "pay-push",
)

class MainViewModel(
    app: Application,
) : AndroidViewModel(app), VersionReceiver, NotificationReceiver {

    private val mDevMode = MutableLiveData(BuildConfig.DEBUG)
    val devMode: LiveData<Boolean> = mDevMode

    val showProgressBar = MutableLiveData<Boolean>()
    var walletVersion: String? = null
        private set
    var walletVersionHash: String? = null
        private set
    var exchangeVersion: String? = null
        private set
    var merchantVersion: String? = null
        private set

    @set:Synchronized
    private var walletConfig = WalletRunConfig(
        testing = Testing(
            emitObservabilityEvents = true,
            devModeActive = devMode.value ?: false,
        )
    )

    private val api = WalletBackendApi(app, walletConfig, this, this)

    val networkManager = NetworkManager(app.applicationContext)
    val paymentManager = PaymentManager(api, viewModelScope)
    val transactionManager: TransactionManager = TransactionManager(api, viewModelScope)
    val refundManager = RefundManager(api, viewModelScope)
    val balanceManager = BalanceManager(api, viewModelScope)
    val exchangeManager: ExchangeManager = ExchangeManager(api, viewModelScope)
    val withdrawManager = WithdrawManager(api, viewModelScope, exchangeManager)
    val peerManager: PeerManager = PeerManager(api, exchangeManager, viewModelScope)
    val settingsManager: SettingsManager = SettingsManager(app.applicationContext, api, viewModelScope, balanceManager)
    val accountManager: AccountManager = AccountManager(api, viewModelScope)
    val depositManager: DepositManager = DepositManager(api, viewModelScope)

    private val mTransactionsEvent = MutableLiveData<Event<ScopeInfo>>()
    val transactionsEvent: LiveData<Event<ScopeInfo>> = mTransactionsEvent

    private val mObservabilityLog = MutableStateFlow<List<ObservabilityEvent>>(emptyList())
    val observabilityLog: StateFlow<List<ObservabilityEvent>> = mObservabilityLog

    private val mScanCodeEvent = MutableLiveData<Event<Boolean>>()
    val scanCodeEvent: LiveData<Event<Boolean>> = mScanCodeEvent

    @set:Synchronized
    private var scanQrContext = ScanQrContext.Unknown

    override fun onVersionReceived(versionInfo: WalletCoreVersion) {
        walletVersion = versionInfo.implementationSemver
        walletVersionHash = versionInfo.implementationGitHash
        exchangeVersion = versionInfo.exchange
        merchantVersion = versionInfo.merchant
    }

    override fun onNotificationReceived(payload: NotificationPayload) {
        if (payload.type == "waiting-for-retry") return // ignore ping)

        val str = BackendManager.json.encodeToString(payload)
        Log.i(TAG, "Received notification from wallet-core: $str")

        // Only update balances when we're told they changed
        if (payload.type == "balance-change") viewModelScope.launch(Dispatchers.Main) {
            balanceManager.loadBalances()
        }

        if (payload.type in observabilityNotifications && payload.event != null) {
            mObservabilityLog.getAndUpdate { logs ->
                logs.takeLast(OBSERVABILITY_LIMIT)
                    .toMutableList().apply {
                        add(payload.event)
                    }
            }
        }

        if (payload.type in transactionNotifications) viewModelScope.launch(Dispatchers.Main) {
            // TODO notification API should give us a currency to update
            // update currently selected transaction
            payload.transactionId?.let { transactionManager.updateTransactionIfSelected(it) }
            // update currently selected transaction list
            transactionManager.loadTransactions()
        }
    }

    /**
     * Navigates to the given scope info's transaction list, when [MainFragment] is shown.
     */
    @UiThread
    fun showTransactions(scopeInfo: ScopeInfo) {
        Log.d(TAG, "selectedScope should change to $scopeInfo")
        transactionManager.selectScope(scopeInfo)
    }

    @UiThread
    fun createAmount(amountText: String, currency: String, incoming: Boolean = false): AmountResult {
        val amount = try {
            Amount.fromString(currency, amountText)
        } catch (e: AmountParserException) {
            return AmountResult.InvalidAmount
        }
        if (incoming || balanceManager.hasSufficientBalance(amount)) return AmountResult.Success(amount)
        return AmountResult.InsufficientBalance(amount)
    }

    @UiThread
    fun dangerouslyReset() {
        withdrawManager.resetTestWithdrawal()
        balanceManager.resetBalances()
    }

    fun startTunnel() {
        viewModelScope.launch {
            api.sendRequest("startTunnel")
        }
    }

    fun stopTunnel() {
        viewModelScope.launch {
            api.sendRequest("stopTunnel")
        }
    }

    fun tunnelResponse(resp: String) {
        viewModelScope.launch {
            api.sendRequest("tunnelResponse", JSONObject(resp))
        }
    }

    @UiThread
    fun scanCode(context: ScanQrContext = ScanQrContext.Unknown) {
        scanQrContext = context
        mScanCodeEvent.value = true.toEvent()
    }

    fun getScanQrContext() = scanQrContext

    fun checkScanQrContext(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        val action = parsed.host
        return when (scanQrContext) {
            ScanQrContext.Send -> action in sendUriActions
            ScanQrContext.Receive -> action in receiveUriActions
            else -> true
        }
    }

    fun setDevMode(enabled: Boolean, onError: (error: TalerErrorInfo) -> Unit) {
        mDevMode.postValue(enabled)
        viewModelScope.launch {
            val config = walletConfig.copy(
                testing = walletConfig.testing?.copy(
                    devModeActive = enabled,
                ) ?: Testing(
                    devModeActive = enabled,
                ),
            )

            api.setWalletConfig(config)
                .onSuccess {
                    walletConfig = config
                }.onError(onError)
        }
    }

    fun hintNetworkAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            api.request<Unit>("hintNetworkAvailability") {
                put("isNetworkAvailable", isAvailable)
            }
        }
    }

    fun runIntegrationTest(onError: (error: TalerErrorInfo) -> Unit) {
        viewModelScope.launch {
            api.request<Unit>("runIntegrationTestV2") {
                put("amountToWithdraw", "KUDOS:42")
                put("amountToSpend", "KUDOS:23")
                put("corebankApiBaseUrl", "https://bank.demo.taler.net/")
                put("exchangeBaseUrl", "https://exchange.demo.taler.net/")
                put("merchantBaseUrl", "https://backend.demo.taler.net/")
                put("merchantAuthToken", "secret-token:sandbox")
            }.onError(onError)
        }
    }

    fun applyDevExperiment(uri: String, onError: (error: TalerErrorInfo) -> Unit) {
        viewModelScope.launch {
            api.request<Unit>("applyDevExperiment") {
                put("devExperimentUri", uri)
            }.onError(onError)
        }
    }

    fun getSelectedScope(c: Context) = c.userPreferencesDataStore.data.map { prefs ->
        if (prefs.hasSelectedScope()) {
            ScopeInfo.fromPrefs(prefs.selectedScope)
        } else {
            null
        }
    }

    fun saveSelectedScope(c: Context, scopeInfo: ScopeInfo?) = viewModelScope.launch {
        c.userPreferencesDataStore.updateData { current ->
            if (scopeInfo != null) {
                current.toBuilder()
                    .setSelectedScope(scopeInfo.toPrefs())
                    .build()
            } else {
                current.toBuilder()
                    .clearSelectedScope()
                    .build()
            }
        }
    }

    fun getActionButtonUsed(c: Context) = c.userPreferencesDataStore.data.map { prefs ->
        if (prefs.hasActionButtonUsed()) {
            prefs.actionButtonUsed
        } else {
            false
        }
    }

    fun saveActionButtonUsed(c: Context) = viewModelScope.launch {
        c.userPreferencesDataStore.updateData { current ->
            current.toBuilder()
                .setActionButtonUsed(true)
                .build()
        }
    }
}

enum class ScanQrContext {
    Send,
    Receive,
    Unknown,
}

sealed class AmountResult {
    data class Success(val amount: Amount) : AmountResult()
    data class InsufficientBalance(val amount: Amount) : AmountResult()
    data object InvalidAmount : AmountResult()
}
