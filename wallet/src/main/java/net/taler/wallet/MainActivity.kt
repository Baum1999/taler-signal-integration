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

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS
import androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.client.android.Intents.Scan.MIXED_SCAN
import com.google.zxing.client.android.Intents.Scan.SCAN_TYPE
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanOptions.QR_CODE
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.taler.common.EventObserver
import net.taler.lib.android.TalerNfcService
import net.taler.wallet.databinding.ActivityMainBinding
import net.taler.wallet.events.ObservabilityDialog
import net.taler.wallet.transactions.TransactionPeerPullCredit
import net.taler.wallet.transactions.TransactionPeerPushDebit

class MainActivity : AppCompatActivity(), OnPreferenceStartFragmentCallback {
    private val model: MainViewModel by viewModels()

    private lateinit var ui: ActivityMainBinding
    private lateinit var nav: NavController
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        model.unlockWallet() // hack to prevent from locking after scanning QR
        if (result == null || result.contents == null) return@registerForActivityResult
        if (model.checkScanQrContext(result.contents)) {
            handleTalerUri(result.contents, "QR code")
        } else {
            confirmTalerUri(result.contents, "QR code")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)
        setupInsets()
        setupBiometrics()

        TalerNfcService.startService(this)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        nav = navHostFragment.navController

        setSupportActionBar(ui.toolbar)
        setupActionBarWithNavController(nav)
        ui.toolbar.setNavigationOnClickListener {
            if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                nav.navigateUp()
            }
        }

        model.startWallet()

        // TODO: refactor and unify progress bar handling
        // model.showProgressBar.observe(this) { show ->
        //     ui.content.progressBar.visibility = if (show) VISIBLE else INVISIBLE
        // }

        handleIntents(intent)

        // Update devMode in model from Datastore API
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.settingsManager.getDevModeEnabled(this@MainActivity).collect { enabled ->
                    model.setDevMode(enabled) { error ->
                        showError(error)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.transactionManager.selectedTransaction.collect { tx ->
                    TalerNfcService.clearUri(this@MainActivity)

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.transactionManager.selectedScope.collect { tx ->
                    model.settingsManager.saveSelectedScope(this@MainActivity, tx)
                }
            }
        }

        model.scanCodeEvent.observe(this, EventObserver {
            val scanOptions = ScanOptions().apply {
                setPrompt("")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setDesiredBarcodeFormats(QR_CODE)
                addExtra(SCAN_TYPE, MIXED_SCAN)
            }
            if (it) barcodeLauncher.launch(scanOptions)
        })

        model.networkManager.networkStatus.observe(this) { online ->
            ui.offlineBanner.visibility = if (online) GONE else VISIBLE
            model.hintNetworkAvailability(online)
        }

        model.devMode.observe(this) {
            invalidateMenu()
        }
    }

    private fun setupInsets() {
        // We really don't want to deal with cutouts!
        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(ui.toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            windowInsets
        }
    }

    private fun setupBiometrics() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    model.authenticated,
                    model.settingsManager.getBiometricLockEnabled(this@MainActivity)
                ) { a, b -> a to b }.collect { c ->
                    val authenticated = c.first
                    val biometricEnabled = c.second
                    if (!authenticated && biometricEnabled) {
                        ui.biometricOverlay.visibility = VISIBLE
                        biometricPrompt.authenticate(promptInfo)
                    } else {
                        ui.biometricOverlay.visibility = GONE
                    }
                }
            }
        }

        ui.unlockButton.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }

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

        if (intent.action == ACTION_VIEW) intent.dataString?.let { uri ->
            handleTalerUri(uri, "intent")
        }

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            val messages: Array<NdefMessage> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.let { rawMessages ->
                    rawMessages.map { it as NdefMessage }
                }?.toTypedArray()
            } ?: return

            messages.forEach { message ->
                message.records?.forEach { record ->
                    record.toUri()?.let { uri ->
                        handleTalerUri(uri.toString(), "nfc")
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (model.devMode.value == true) {
            menuInflater.inflate(R.menu.global_dev, menu)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_logs -> {
                ObservabilityDialog().show(supportFragmentManager, "OBSERVABILITY")
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirmTalerUri(uri: String, from: String) {
        MaterialAlertDialogBuilder(this).apply {
            setTitle(R.string.qr_scan_context_title)
            setMessage(when (model.getScanQrContext()) {
                ScanQrContext.Send -> R.string.qr_scan_context_send_message
                ScanQrContext.Receive -> R.string.qr_scan_context_receive_message
                else -> error("invalid value")
            })

            setNegativeButton(R.string.ok) { _, _ ->
                handleTalerUri(uri, from)
            }

            setNeutralButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    private fun handleTalerUri(uri: String, from: String) {
        val args = bundleOf("uri" to uri, "from" to from)
        nav.navigate(R.id.action_global_handle_uri, args)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        when (pref.key) {
            "pref_exchanges" -> nav.navigate(R.id.nav_settings_exchanges)
        }
        return true
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
        TalerNfcService.stopService(this)
        TalerNfcService.clearUri(this)
        model.stopWallet()
    }
}
