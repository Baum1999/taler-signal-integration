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
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit
import net.taler.wallet.ui.theme.TalerTheme

class MainActivity : FragmentActivity() {
    private val model: MainViewModel by viewModels()

    private var pendingLaunchUri: String? = null
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

                val logExportLauncher = rememberLauncherForActivityResult(CreateDocument("text/plain")) { uri ->
                    uri?.let { model.settingsManager.exportLogcat(it) }
                }

                LaunchedEffect(initError) {
                    if (initError == null) {
                        pendingLaunchUri?.let { uri ->
                            if (navigateOrQueue(uri)) {
                                pendingLaunchUri = null
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    initError?.let { error ->
                        WalletInitErrorScreen(
                            model = model,
                            error = error,
                            onExportLogs = { logExportLauncher.launch("taler-wallet-logcat.txt") },
                            onRetry = { model.startWallet() },
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
                        Log.d(TAG, "Transaction ${tx.transactionId} selected with URI $uri")
                        TalerNfcService.setUri(this@MainActivity, uri)
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

        // For VIEW intents (taler://, payto://, ...) the system sets intent.data;
        // for NDEF_DISCOVERED it is the URI of the first NDEF record on the tag.
        intent.dataString?.let { emitUri(it) }

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
        model.stopWallet()
    }
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
