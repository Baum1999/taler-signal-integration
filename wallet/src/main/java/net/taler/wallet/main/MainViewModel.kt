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

package net.taler.wallet.main

import android.app.Application
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
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.common.Event
import net.taler.wallet.accounts.AccountManager
import net.taler.wallet.backend.BackendManager
import net.taler.wallet.backend.NotificationPayload
import net.taler.wallet.backend.NotificationReceiver
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.InitReceiver
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletCoreSingleton
import net.taler.wallet.backend.WalletCoreVersion
import net.taler.wallet.backend.WalletDatabaseBackend
import net.taler.wallet.backend.WalletRunConfig
import net.taler.wallet.backend.WalletRunConfig.Features
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
import net.taler.wallet.transactions.TransactionManager
import net.taler.wallet.transactions.TransactionStateFilter
import net.taler.wallet.withdraw.WithdrawManager
import net.taler.wallet.BuildConfig
import net.taler.wallet.NetworkManager
import net.taler.wallet.backend.InitResponse
import net.taler.wallet.backend.MigrateDatabaseResponse
import net.taler.wallet.donau.DonauManager
import net.taler.wallet.link.OwnUriTracker
import net.taler.wallet.link.PaymentPreviewer
import net.taler.wallet.link.TalerPaymentPreviewer
import net.taler.wallet.tokens.TokenManager
import java.util.UUID

const val TAG = "taler-wallet"
const val OBSERVABILITY_LIMIT = 100

class MainViewModel(
    app: Application,
) : AndroidViewModel(app), InitReceiver, NotificationReceiver {

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
    private val mDatabaseBackend = MutableStateFlow<WalletDatabaseBackend?>(null)
    val databaseBackend: StateFlow<WalletDatabaseBackend?> = mDatabaseBackend

    @set:Synchronized
    private var walletConfig = WalletRunConfig(
        testing = Testing(
            emitObservabilityEvents = true,
            devModeActive = devMode.value == true,
        ),
        features = Features(
            enableV1Contracts = true,
            useNativeDb = true,
        ),
        logLevel = if (devMode.value == true) "TRACE" else "INFO",
    )

    // Geteilte wallet-core-Instanz, siehe WalletCoreSingleton - eine zweite
    // WalletBackendApi im selben Prozess (z.B. im TalerLinkService fuer die
    // lokale Signal-Schnittstelle) wuerde wegen BackendManagers statischem
    // initialized-Flag nie wirklich starten.
    private val api = WalletCoreSingleton.acquire(app, walletConfig)

    /**
     * Fuer die Sammelkarte, die EnterLinkTab (ScanQrScreen.kt) zeigt, wenn
     * eine manuell eingefuegte Zwischenablage mehrere Taler-URIs enthaelt
     * (siehe MultiUriSummary.kt/buildAggregate). Nutzt dieselbe geteilte
     * [api]-Instanz wie der Rest dieser ViewModel - kein zweiter
     * WalletCoreSingleton.acquire()-Aufruf noetig, der einen eigenen
     * release()-Gegenpart brauchen wuerde.
     */
    fun newPaymentPreviewer(): PaymentPreviewer =
        TalerPaymentPreviewer(api, OwnUriTracker(getApplication<Application>()))

    init {
        WalletCoreSingleton.addInitReceiver(this)
        WalletCoreSingleton.addNotificationReceiver(this)
    }

    override fun onCleared() {
        WalletCoreSingleton.removeInitReceiver(this)
        WalletCoreSingleton.removeNotificationReceiver(this)
        super.onCleared()
    }

    val networkManager = NetworkManager(app.applicationContext)
    val exchangeManager: ExchangeManager = ExchangeManager(api, viewModelScope)
    val balanceManager = BalanceManager(api, viewModelScope, exchangeManager)
    val paymentManager = PaymentManager(api, viewModelScope, exchangeManager)
    val transactionManager: TransactionManager = TransactionManager(api, viewModelScope)
    val refundManager = RefundManager(api, viewModelScope)
    val withdrawManager = WithdrawManager(api, viewModelScope, exchangeManager, transactionManager)
    val peerManager: PeerManager = PeerManager(api, exchangeManager, viewModelScope)
    val settingsManager: SettingsManager = SettingsManager(app.applicationContext, api, viewModelScope, balanceManager)
    val accountManager: AccountManager = AccountManager(api, viewModelScope)
    val depositManager: DepositManager = DepositManager(api, viewModelScope, balanceManager)
    val tokenManager: TokenManager = TokenManager(api)
    val donauManager: DonauManager = DonauManager(api, viewModelScope, exchangeManager)

    private val mAuthenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = mAuthenticated

    private val mTransactionsEvent = MutableLiveData<Event<ScopeInfo>>()
    val transactionsEvent: LiveData<Event<ScopeInfo>> = mTransactionsEvent

    private val mObservabilityLog = MutableStateFlow<List<ObservabilityEvent>>(emptyList())
    val observabilityLog: StateFlow<List<ObservabilityEvent>> = mObservabilityLog

    private val mShowObservabilityLog = MutableStateFlow(false)
    val showObservabilityLog: StateFlow<Boolean> = mShowObservabilityLog

    private val mViewMode = MutableStateFlow<ViewMode>(ViewMode.Assets)
    val viewMode: StateFlow<ViewMode> = mViewMode

    private val mInitError = MutableStateFlow<TalerErrorInfo?>(null)
    val initError: StateFlow<TalerErrorInfo?> = mInitError

    private val mDatabaseMigrationState =
        MutableStateFlow<DatabaseMigrationState>(DatabaseMigrationState.None)
    val databaseMigrationState: StateFlow<DatabaseMigrationState> = mDatabaseMigrationState

    fun startWallet() {
        // no-op: WalletCoreSingleton.acquire() oben hat wallet-core bereits
        // gestartet, falls es nicht schon lief. Bleibt als Methode erhalten,
        // damit MainActivity.onCreate() unveraendert bleibt.
    }

    fun stopWallet() {
        WalletCoreSingleton.release()
    }

    override fun onInitErrorReceived(error: TalerErrorInfo) {
        mInitError.value = error
    }

    override fun onInitReceived(init: InitResponse) {
        walletVersion = init.versionInfo.implementationSemver
        walletVersionHash = init.versionInfo.implementationGitHash
        exchangeVersion = init.versionInfo.exchange
        merchantVersion = init.versionInfo.merchant
        mDatabaseBackend.value = init.databaseBackend
        if (
            init.databaseBackend == WalletDatabaseBackend.IndexedDB &&
            mDatabaseMigrationState.value == DatabaseMigrationState.None
        ) {
            mDatabaseMigrationState.value = DatabaseMigrationState.Prompt
        }
        mInitError.value = null
    }

    override fun onNotificationReceived(payload: NotificationPayload) {
        val str = BackendManager.json.encodeToString(payload)
        Log.i(TAG, "Received notification from wallet-core: $str")

        when (payload) {
            // Only update balances when we're told they changed
            is NotificationPayload.BalanceChange -> {
                viewModelScope.launch(Dispatchers.Main) {
                    balanceManager.loadAssets()
                }
            }

            is NotificationPayload.TransactionStateTransition -> {
                paymentManager.onTransactionStateTransition(payload)
                viewModelScope.launch(Dispatchers.Main) {
                    payload.transactionId?.let { id ->
                        // update currently selected transaction
                        transactionManager.updateTransactionIfSelected(id)
                        // update currently selected transaction list
                        transactionManager.getTransactionById(id)?.let { tx ->
                            val v = viewMode.value
                            if (v is ViewMode.Transactions && v.selectedScope in tx.scopes) {
                                transactionManager.loadTransactions(v.selectedScope)
                            }
                        }
                    }
                }
            }

            is NotificationPayload.TaskObservabilityEvent,
            is NotificationPayload.RequestObservabilityEvent -> {
                val event = when(payload) {
                    is NotificationPayload.TaskObservabilityEvent -> payload.event
                    is NotificationPayload.RequestObservabilityEvent -> payload.event
                }
                if (event != null) {
                    mObservabilityLog.getAndUpdate { logs ->
                        logs.takeLast(OBSERVABILITY_LIMIT)
                            .toMutableList().apply {
                                add(event)
                            }
                    }
                }
            }

            is NotificationPayload.DatabaseMaintenanceProgress -> {
                updateDatabaseMigrationProgress(payload)
            }

            else -> {}
        }
    }

    @UiThread
    fun lockWallet() {
        mAuthenticated.value = false
    }

    @UiThread
    fun unlockWallet() {
        mAuthenticated.value = true
    }

    fun setViewMode(v: ViewMode?) = viewModelScope.launch {
        mViewMode.value = when(v) {
            null -> ViewMode.Assets
            is ViewMode.Transactions -> v.copy(
                // fill-in currency spec from DB
                selectedSpec = exchangeManager.getCurrencySpecification(v.selectedScope),
            )
            else -> v
        }
    }

    fun selectScope(scopeInfo: ScopeInfo?) {
        if (scopeInfo != null) {
            setViewMode(ViewMode.Transactions(scopeInfo))
        } else {
            setViewMode(ViewMode.Assets)
        }
    }

    fun showAssets() {
        if (viewMode.value != ViewMode.Assets) {
            selectScope(null)
        }
    }

    /**
     * Navigates to the given scope info's transaction list, when [MainScreen] is shown.
     */
    @UiThread
    fun showTransactions(scopeInfo: ScopeInfo, stateFilter: TransactionStateFilter? = null) {
        mViewMode.value = ViewMode.Transactions(scopeInfo, stateFilter = stateFilter)
    }

    // FIXME: get rid of this ugliness! use wallet-core!
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

    fun setDevMode(enabled: Boolean, onError: (error: TalerErrorInfo) -> Unit) {
        mDevMode.postValue(enabled)
        viewModelScope.launch {
            val config = walletConfig.copy(
                testing = walletConfig.testing?.copy(
                    devModeActive = enabled,
                ) ?: Testing(
                    devModeActive = enabled,
                ),
                logLevel = if (enabled) "TRACE" else "INFO",
            )

            api.setWalletConfig(config)
                .onSuccess {
                    walletConfig = config
                }.onError(onError)
        }
    }

    fun offerDatabaseMigration() {
        if (
            mDatabaseBackend.value == WalletDatabaseBackend.IndexedDB &&
            mDatabaseMigrationState.value !is DatabaseMigrationState.Migrating &&
            mDatabaseMigrationState.value !is DatabaseMigrationState.Cancelling
        ) {
            mDatabaseMigrationState.value = DatabaseMigrationState.Prompt
        }
    }

    fun deferDatabaseMigration() {
        if (
            mDatabaseMigrationState.value == DatabaseMigrationState.Prompt ||
            mDatabaseMigrationState.value is DatabaseMigrationState.Failed
        ) {
            mDatabaseMigrationState.value = DatabaseMigrationState.Deferred
        }
    }

    fun migrateDatabase() {
        if (mDatabaseBackend.value != WalletDatabaseBackend.IndexedDB) return
        if (
            mDatabaseMigrationState.value is DatabaseMigrationState.Migrating ||
            mDatabaseMigrationState.value is DatabaseMigrationState.Cancelling
        ) return

        val progressToken = UUID.randomUUID().toString()
        mDatabaseMigrationState.value = DatabaseMigrationState.Migrating(progressToken, 0)

        viewModelScope.launch {
            api.migrateDatabase(progressToken)
                .onSuccess { response ->
                    mDatabaseBackend.value = response.databaseBackend
                    mDatabaseMigrationState.value = response.toDatabaseMigrationState()
                }
                .onError { error ->
                    mDatabaseMigrationState.value =
                        mDatabaseMigrationState.value.withMigrationError(error)
                }
        }
    }

    fun cancelDatabaseMigration(onError: (error: TalerErrorInfo) -> Unit) {
        val state = mDatabaseMigrationState.value as? DatabaseMigrationState.Migrating ?: return
        mDatabaseMigrationState.value = DatabaseMigrationState.Cancelling(
            progressToken = state.progressToken,
            completionPercent = state.completionPercent,
        )
        viewModelScope.launch {
            api.cancelDatabaseMigration(state.progressToken).onError { error ->
                val current = mDatabaseMigrationState.value
                if (
                    current is DatabaseMigrationState.Cancelling &&
                    current.progressToken == state.progressToken
                ) {
                    mDatabaseMigrationState.value = DatabaseMigrationState.Migrating(
                        progressToken = state.progressToken,
                        completionPercent = current.completionPercent,
                    )
                    onError(error)
                }
            }
        }
    }

    fun acknowledgeDatabaseMigrationComplete() {
        if (mDatabaseMigrationState.value == DatabaseMigrationState.Complete) {
            mDatabaseMigrationState.value = DatabaseMigrationState.None
        }
    }

    fun consumeDatabaseMigrationFailure(): TalerErrorInfo? {
        val state = mDatabaseMigrationState.value as? DatabaseMigrationState.Failed ?: return null
        mDatabaseMigrationState.value = DatabaseMigrationState.Deferred
        return state.error
    }

    private fun updateDatabaseMigrationProgress(
        payload: NotificationPayload.DatabaseMaintenanceProgress,
    ) {
        mDatabaseMigrationState.value = mDatabaseMigrationState.value.withProgress(payload)
    }

    fun showObservabilityLog() {
        mShowObservabilityLog.value = true
    }

    fun hideObservabilityLog() {
        mShowObservabilityLog.value = false
    }

    fun hintNetworkAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            api.request<Unit>("hintNetworkAvailability") {
                put("isNetworkAvailable", isAvailable)
            }
        }
    }

    fun applyDevExperiment(uri: String, onError: (error: TalerErrorInfo) -> Unit) {
        viewModelScope.launch {
            api.request<Unit>("applyDevExperiment") {
                put("devExperimentUri", uri)
            }.onError(onError)
        }
    }
}

sealed class AmountResult {
    data class Success(val amount: Amount) : AmountResult()
    data class InsufficientBalance(val amount: Amount) : AmountResult()
    data object InvalidAmount : AmountResult()
}

sealed interface DatabaseMigrationState {
    data object None : DatabaseMigrationState
    data object Prompt : DatabaseMigrationState
    data object Deferred : DatabaseMigrationState
    data class Migrating(
        val progressToken: String,
        val completionPercent: Int,
    ) : DatabaseMigrationState

    data class Cancelling(
        val progressToken: String,
        val completionPercent: Int,
    ) : DatabaseMigrationState

    data object Complete : DatabaseMigrationState
    data class Failed(val error: TalerErrorInfo) : DatabaseMigrationState
}

internal fun DatabaseMigrationState.withProgress(
    payload: NotificationPayload.DatabaseMaintenanceProgress,
): DatabaseMigrationState {
    if (payload.operation != "indexeddb-to-native-migration") return this
    val percent = payload.completionPercent?.coerceIn(0, 100)

    return when (this) {
        is DatabaseMigrationState.Migrating -> {
            if (payload.progressToken != progressToken) this
            else copy(completionPercent = percent ?: completionPercent)
        }

        is DatabaseMigrationState.Cancelling -> {
            if (payload.progressToken != progressToken) this
            else copy(completionPercent = percent ?: completionPercent)
        }

        else -> this
    }
}

internal fun DatabaseMigrationState.withMigrationError(
    error: TalerErrorInfo,
): DatabaseMigrationState =
    if (
        this is DatabaseMigrationState.Cancelling &&
        error.code == TalerErrorCode.WALLET_CORE_REQUEST_CANCELLED
    ) {
        DatabaseMigrationState.Deferred
    } else {
        DatabaseMigrationState.Failed(error)
    }

internal fun MigrateDatabaseResponse.toDatabaseMigrationState(): DatabaseMigrationState =
    if (databaseBackend == WalletDatabaseBackend.Sqlite) {
        DatabaseMigrationState.Complete
    } else {
        DatabaseMigrationState.Failed(
            TalerErrorInfo.makeCustomError(
                message = "Database migration completed without switching to SQLite",
            ),
        )
    }
