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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat.START
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.client.android.Intents.Scan.MIXED_SCAN
import com.google.zxing.client.android.Intents.Scan.SCAN_TYPE
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanOptions.QR_CODE
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

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result == null || result.contents == null) return@registerForActivityResult
        if (model.checkScanQrContext(result.contents)) {
            handleTalerUri(result.contents, "QR code")
        } else {
            confirmTalerUri(result.contents, "QR code")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        nav = navHostFragment.navController

        setSupportActionBar(ui.content.toolbar)
        ui.content.toolbar.setupWithNavController(nav)

        // TODO: refactor and unify progress bar handling
        // model.showProgressBar.observe(this) { show ->
        //     ui.content.progressBar.visibility = if (show) VISIBLE else INVISIBLE
        // }

        handleIntents(intent)

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
            ui.content.offlineBanner.visibility = if (online) GONE else VISIBLE
            model.hintNetworkAvailability(online)
        }

        model.devMode.observe(this) {
            invalidateMenu()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (ui.drawerLayout.isDrawerOpen(START)) ui.drawerLayout.closeDrawer(START)
        else if (nav.currentDestination?.id == R.id.nav_main) {
            if (model.transactionManager.selectedScope.value != null) {
                model.transactionManager.selectScope(null)
            }
        } else {
            super.onBackPressed()
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

    override fun onDestroy() {
        super.onDestroy()
        TalerNfcService.clearUri(this)
    }
}
