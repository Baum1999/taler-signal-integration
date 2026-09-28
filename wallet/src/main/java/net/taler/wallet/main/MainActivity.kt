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

import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS
import androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.taler.lib.android.TalerNfcService
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.WalletNavHost
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.ErrorBottomSheet
import net.taler.wallet.events.ObservabilityDialog
import net.taler.wallet.launchInAppBrowser
import net.taler.wallet.link.OwnUriTracker
import net.taler.wallet.link.TalerUriKind
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit
import net.taler.wallet.ui.theme.TalerTheme

class MainActivity : FragmentActivity() {
    private val model: MainViewModel by viewModels()

    private var pendingLaunchUri: String? = null
    private var pendingComposeSend: WalletDestination.ComposeSend? = null
    private var pendingComposeRefund: WalletDestination.ComposeRefund? = null
    private var nav: NavController? = null
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    companion object {
        // Guards against the same URI being delivered and handled twice, e.g.
        // when the activity is recreated and Android re-delivers the launching
        // intent to onCreate, or when an NDEF record matches intent.data.
        private const val INTENT_DEDUP_WINDOW_MS = 2000L
        private var lastHandledUri: String? = null
        private var lastHandledAtMillis: Long = 0
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setupBiometrics()

        TalerNfcService.startService(this)

        setContent {
            TalerTheme {
                val navController = rememberNavController()
                nav = navController
                var errorInfo by remember { mutableStateOf<TalerErrorInfo?>(null) }
                val showObservabilityLog by model.showObservabilityLog.collectAsState(false)
                val devMode by model.devMode.observeAsState(false)
                val initError by model.initError.collectAsState()
                val errorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !devMode)
                val authenticated by model.authenticated.collectAsState()
                val biometricEnabled by model.settingsManager.getBiometricLockEnabled(this).collectAsState(false)
                val databaseMigrationState by model.databaseMigrationState.collectAsState()
                val walletUnlocked = !biometricEnabled || authenticated

                val logExportLauncher = rememberLauncherForActivityResult(CreateDocument("text/plain")) { uri ->
                    uri?.let { model.settingsManager.exportLogcat(it) }
                }
                val dbExportLauncher = rememberLauncherForActivityResult(CreateDocument("application/octet-stream")) { uri ->
                    uri?.let { model.settingsManager.exportRawDb(it) }
                }
                var showDbExportConfirm by remember { mutableStateOf(false) }

                LaunchedEffect(initError) {
                    if (initError == null) {
                        pendingLaunchUri?.let { uri ->
                            if (navigateOrQueue(uri)) {
                                pendingLaunchUri = null
                            }
                        }
                    }
                }

                LaunchedEffect(databaseMigrationState, walletUnlocked) {
                    if (walletUnlocked && databaseMigrationState is DatabaseMigrationState.Failed) {
                        errorInfo = model.consumeDatabaseMigrationFailure()
                    }
                    pendingComposeSend?.let { destination ->
                        // Fix round 2 (Task-A6 Review): hoechstens eine
                        // ComposeSend-Instanz gleichzeitig auf dem Back-Stack
                        // zulassen - siehe emitComposeSend() unten fuer die
                        // ausfuehrliche Begruendung (gleicher Mechanismus).
                        nav?.navigate(destination) {
                            popUpTo<WalletDestination.ComposeSend> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    pendingComposeRefund?.let { destination ->
                        // Meilenstein 6: gleicher Einzelinstanz-Mechanismus wie
                        // pendingComposeSend oben, siehe emitComposeRefund().
                        nav?.navigate(destination) {
                            popUpTo<WalletDestination.ComposeRefund> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    initError?.let { error ->
                        WalletInitErrorScreen(
                            model = model,
                            error = error,
                            onExportLogs = { logExportLauncher.launch("taler-wallet-logcat.txt") },
                            onExportDb = { showDbExportConfirm = true },
                        )
                    } ?: run {
                        WalletNavHost(
                            navController = navController,
                            model = model,
                            modifier = Modifier.fillMaxSize(),
                            onFulfillPayment = { url: String -> launchInAppBrowser(this@MainActivity, url) },
                            onShowError = { errorInfo = it }
                        )
                    }

                    if (!authenticated && biometricEnabled) {
                        BiometricOverlay(
                            onUnlock = { biometricPrompt.authenticate(promptInfo) }
                        )
                    }
                }

                if (showObservabilityLog) {
                    val events by model.observabilityLog.collectAsState()
                    ObservabilityDialog(events.reversed()) {
                        model.hideObservabilityLog()
                    }
                }

                errorInfo?.let {
                    ErrorBottomSheet(
                        error = it,
                        devMode = devMode,
                        sheetState = errorSheetState,
                        onDismiss = { errorInfo = null }
                    )
                }

                if (showDbExportConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDbExportConfirm = false },
                        title = { Text(stringResource(R.string.wallet_export_database)) },
                        text = { Text(stringResource(R.string.wallet_export_database_warning)) },
                        confirmButton = {
                            Button(onClick = {
                                showDbExportConfirm = false
                                dbExportLauncher.launch("taler-wallet-db-${System.currentTimeMillis()}.sqlite3")
                            }) {
                                Text(stringResource(R.string.wallet_export_database_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDbExportConfirm = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                if (walletUnlocked) when (val state = databaseMigrationState) {
                    DatabaseMigrationState.Prompt -> DatabaseMigrationPrompt(
                        onMigrate = model::migrateDatabase,
                        onLater = model::deferDatabaseMigration,
                    )

                    is DatabaseMigrationState.Migrating -> DatabaseMigrationProgressDialog(
                        completionPercent = state.completionPercent,
                        cancelling = false,
                        onCancel = {
                            model.cancelDatabaseMigration { errorInfo = it }
                        },
                    )

                    is DatabaseMigrationState.Cancelling -> DatabaseMigrationProgressDialog(
                        completionPercent = state.completionPercent,
                        cancelling = true,
                        onCancel = {},
                    )

                    else -> {}
                }
            }
        }

        model.startWallet()

        handleIntents(intent)

        // Update devMode in model from Datastore API
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.settingsManager.getDevModeEnabled(this@MainActivity).collect { enabled ->
                    model.setDevMode(enabled) {}
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.transactionManager.selectedTransaction.collect { tx ->
                    TalerNfcService.clearNdefPayload(this@MainActivity)

                    when (tx) {
                        is TransactionPeerPushDebit -> tx.talerUri
                        is TransactionPeerPullCredit -> tx.talerUri
                        else -> return@collect
                    }?.let { uri ->
                        // Fix (Final-Review C1): $uri hier NICHT loggen - es ist die
                        // rohe taler://pay-push/...-URI (Bearer-Instrument), die bei
                        // jedem compose-send ueber transactionManager.selectTransaction
                        // durch diesen bestehenden Collector laeuft. Log.d landet auch in
                        // Release-Builds in logcat (Proguard entfernt es nicht) - Verstoss
                        // gegen Iron Rule 3 ("niemals loggen"). Die transactionId allein
                        // ist als Korrelationshandle unbedenklich.
                        Log.d(TAG, "Transaction ${tx.transactionId} selected")
                        TalerNfcService.setUri(this@MainActivity, uri)
                        // Bug 2 Fix (Signal-Integration): eigene ausgehende URIs merken,
                        // damit TalerLinkService.previewPeerPushCredit spaeter erkennen
                        // kann, wenn dieselbe URI (z.B. an sich selbst per Signal
                        // geschickt) wieder als eingehend angenommen werden soll -
                        // siehe OwnUriTracker. Die transactionId wird mitgemerkt, damit
                        // HandleUriScreen bei einem spaeteren Wieder-Oeffnen (z.B. ueber
                        // Signals "Abbrechen"-Button) direkt zur eigenen Transaktion
                        // zurueckfinden kann, statt sie faelschlich als eingehend zu
                        // behandeln.
                        OwnUriTracker(this@MainActivity).track(uri, tx.transactionId)
                    }
                }
            }
        }

        model.networkManager.networkStatus.observe(this) { online ->
            model.hintNetworkAvailability(online)
        }
    }

    private fun setupBiometrics() {
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == ERROR_NO_BIOMETRICS || errorCode == ERROR_NO_DEVICE_CREDENTIAL) {
                        model.unlockWallet()
                    }
                    Toast.makeText(this@MainActivity, getString(R.string.biometric_auth_error, errString), LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    model.unlockWallet()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, getString(R.string.biometric_auth_failed), LENGTH_SHORT).show()
                }
            },
        )

        promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .setConfirmationRequired(true)
                .build()
        } else {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setDeviceCredentialAllowed(true)
                .setConfirmationRequired(true)
                .build()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent?) {
        if (intent == null) return

        // Ensure each URI from a single intent is handled exactly once.
        val emittedUris = mutableSetOf<String>()

        fun emitUri(uri: String) {
            val trimmed = uri.trim()
            if (trimmed.isEmpty() || !emittedUris.add(trimmed)) return

            val now = System.currentTimeMillis()
            if (trimmed == lastHandledUri && now - lastHandledAtMillis < INTENT_DEDUP_WINDOW_MS) {
                Log.d(TAG, "Ignoring already handled URI: $trimmed")
                return
            }
            lastHandledUri = trimmed
            lastHandledAtMillis = now

            navigateOrQueue(trimmed)
        }

        fun emitComposeSend(destination: WalletDestination.ComposeSend) {
            if (nav != null) {
                // Fix round 2 (Task-A6 Review): hoechstens eine ComposeSend-
                // Instanz gleichzeitig auf dem Back-Stack zulassen. Ohne
                // popUpTo/launchSingleTop koennte ein zweiter
                // talerlink://compose-send Deep-Link (andere correlationId)
                // waehrend ein erster ComposeSendScreen noch offen ist und auf
                // seine Zahlung wartet, eine ZWEITE Instanz oben auf den
                // Stack legen - WalletNavHost.kt's animierte Enter/Exit-
                // Transitions koennen beide Composables dann kurzzeitig
                // gleichzeitig am Leben halten. Die zweite Instanz wuerde
                // beim Betreten peerManager.resetPushPayment() aufrufen und
                // damit den GETEILTEN pushState der ersten, noch wartenden
                // Instanz ueberschreiben - exakt der "payment committed but
                // reported CANCELLED"-Fehlermodus aus Fix round 1 Critical
                // #2, nur ueber einen anderen Ausloeser (Cross-Instance-
                // Ueberschreiben statt liegengebliebener State). popUpTo<
                // WalletDestination.ComposeSend>(inclusive = true) entfernt
                // eine evtl. vorhandene AELTERE ComposeSend-Instanz zuerst
                // (deren onDispose feuert dabei deterministisch CANCELLED,
                // als Teil derselben Navigations-Transaktion - keine Race
                // gegen deren eigenes spaeteres Settling), BEVOR die neue
                // Instanz komponiert wird und ihrerseits resetPushPayment()
                // aufruft. launchSingleTop verhindert zusaetzlich einen
                // doppelten Push, falls dieselbe correlationId zweimal
                // hereinkommt. popUpTo ist scoped auf genau den
                // ComposeSend-Routentyp und poppt daher keine anderen
                // Destinationen (z.B. Main) vom Stack.
                nav?.navigate(destination) {
                    popUpTo<WalletDestination.ComposeSend> { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                pendingComposeSend = destination
            }
        }

        // Meilenstein 6: gleicher Einzelinstanz-Mechanismus wie
        // emitComposeSend oben (siehe dortige ausfuehrliche Begruendung),
        // eigener Routentyp/eigenes pending-Feld, damit ein compose-send- und
        // ein compose-refund-Deep-Link einander nicht gegenseitig vom
        // Stack poppen.
        fun emitComposeRefund(destination: WalletDestination.ComposeRefund) {
            if (nav != null) {
                nav?.navigate(destination) {
                    popUpTo<WalletDestination.ComposeRefund> { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                pendingComposeRefund = destination
            }
        }

        // For VIEW intents (taler://, payto://, ...) the system sets intent.data;
        // for NDEF_DISCOVERED it is the URI of the first NDEF record on the tag.
        intent.dataString?.let { uri ->
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "talerlink" && parsed.host == "compose-send") {
                composeSendFrom(parsed)?.let { destination -> emitComposeSend(destination) }
            } else if (parsed.scheme == "talerlink" && parsed.host == "compose-refund") {
                composeRefundFrom(parsed)?.let { destination -> emitComposeRefund(destination) }
            } else {
                emitUri(uri)
            }
        }

        if (intent.action == ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(EXTRA_TEXT)?.let { emitUri(it) }
        }

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            // Fallback only: if the system did not set a data URI, take the first
            // URI record of the tag. One scan yields exactly one URI.
            if (intent.dataString == null) {
                extractFirstNdefUri(intent)?.let { emitUri(it.toString()) }
            }
        }
    }

    /**
     * Liest den Compose-Kontext aus den Query-Parametern des Deep-Links.
     * Seit dem Wegfall der App-zu-App-Schnittstelle ist der Link die einzige
     * Quelle dafuer - frueher stand hier nur eine correlationId, zu der Taler
     * den Rest aus einem eigenen Zwischenspeicher nachschlug.
     *
     * correlationId und returnUri sind Pflicht: ohne sie koennte Taler das
     * Ergebnis spaeter keinem Signal-Vorgang zuordnen und nirgendwohin
     * zurueckspringen. Fehlt eines davon, wird der Link verworfen.
     */
    private fun composeSendFrom(uri: Uri): WalletDestination.ComposeSend? {
        val correlationId = uri.getQueryParameter("correlationId") ?: return null
        val returnUri = uri.getQueryParameter("returnUri") ?: return null
        val direction = uri.getQueryParameter("direction")
            ?.takeIf { name -> TalerUriKind.entries.any { it.name == name } }
            ?: TalerUriKind.PAY_PUSH.name

        return WalletDestination.ComposeSend(
            correlationId = correlationId,
            returnUri = returnUri,
            recipientHint = uri.getQueryParameter("recipientHint"),
            isGroup = uri.getQueryParameter("isGroup").toBoolean(),
            memberCount = uri.getQueryParameter("memberCount")?.toIntOrNull() ?: 0,
            disappearingMessagesSeconds = uri.getQueryParameter("disappearingMessagesSeconds")?.toIntOrNull() ?: 0,
            direction = direction,
        )
    }

    private fun composeRefundFrom(uri: Uri): WalletDestination.ComposeRefund? {
        val correlationId = uri.getQueryParameter("correlationId") ?: return null
        val returnUri = uri.getQueryParameter("returnUri") ?: return null
        val originalUri = uri.getQueryParameter("originalUri") ?: return null

        return WalletDestination.ComposeRefund(
            correlationId = correlationId,
            returnUri = returnUri,
            originalUri = originalUri,
        )
    }

    private fun extractFirstNdefUri(intent: Intent): Uri? {
        val messages: Array<NdefMessage> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                ?.filterIsInstance<NdefMessage>()
                ?.toTypedArray()
        } ?: return null

        return messages.firstNotNullOfOrNull { message ->
            message.records.firstNotNullOfOrNull { record ->
                record.toUri()
            }
        }
    }

    // Navigate to the URI handling screen, or queue the URI until the wallet is
    // initialized and the NavHost graph is ready (e.g. on cold start with a
    // wallet init error, where navigation would otherwise fail).
    private fun navigateOrQueue(uri: String): Boolean {
        val controller = nav
        if (controller != null) {
            // getGraph() throws if setGraph() was never called (e.g. the wallet
            // failed to initialize and the NavHost was never composed).
            val graphReady = runCatching { controller.graph }.isSuccess
            if (graphReady) {
                controller.navigate(WalletDestination.HandleUri(uri))
                return true
            }
        }
        pendingLaunchUri = uri
        return false
    }

    override fun onResume() {
        super.onResume()
        TalerNfcService.setDefaultHandler(this)
    }

    override fun onPause() {
        super.onPause()
        TalerNfcService.unsetDefaultHandler(this)
    }

    override fun onStop() {
        super.onStop()
        model.lockWallet()
    }

    override fun onDestroy() {
        super.onDestroy()
        TalerNfcService.clearNdefPayload(this)
        TalerNfcService.stopService(this)
        // wallet-core belongs to the retained MainViewModel, not to this activity
        if (!isChangingConfigurations) model.stopWallet()
    }
}

@Composable
private fun DatabaseMigrationPrompt(
    onMigrate: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.wallet_db_migration_title)) },
        text = { Text(stringResource(R.string.wallet_db_migration_message)) },
        confirmButton = {
            Button(onClick = onMigrate) {
                Text(stringResource(R.string.wallet_db_migration_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.wallet_db_migration_later))
            }
        },
    )
}

@Composable
private fun DatabaseMigrationProgressDialog(
    completionPercent: Int,
    cancelling: Boolean,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    if (cancelling) {
                        R.string.wallet_db_migration_cancelling
                    } else {
                        R.string.wallet_db_migration_in_progress
                    },
                ),
            )
        },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { completionPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.wallet_db_migration_progress,
                        completionPercent,
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCancel,
                enabled = !cancelling,
            ) {
                Text(
                    stringResource(
                        if (cancelling) {
                            R.string.wallet_db_migration_cancelling
                        } else {
                            R.string.cancel
                        },
                    ),
                )
            }
        },
    )
}

@Composable
fun BiometricOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 24.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )

            Button(onClick = onUnlock) {
                Text(stringResource(R.string.biometric_unlock_label))
            }
        }
    }
}
