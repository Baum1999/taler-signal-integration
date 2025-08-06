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

package net.taler.wallet.settings

import android.app.Activity.RESULT_OK
import android.app.KeyguardManager
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.ACTION_FINGERPRINT_ENROLL
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_SHORT
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import net.taler.common.showError
import net.taler.wallet.BuildConfig.FLAVOR
import net.taler.wallet.BuildConfig.VERSION_CODE
import net.taler.wallet.BuildConfig.VERSION_NAME
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.showError
import net.taler.wallet.withdraw.TestWithdrawStatus
import java.lang.System.currentTimeMillis


class SettingsFragment : PreferenceFragmentCompat() {

    private val model: MainViewModel by activityViewModels()
    private val settingsManager get() = model.settingsManager
    private val withdrawManager by lazy { model.withdrawManager }
    private lateinit var biometricManager: BiometricManager

    private lateinit var prefDevMode: SwitchPreference
    private lateinit var prefBiometricLock: SwitchPreference
    private lateinit var prefWithdrawTest: Preference
    private lateinit var prefLogcat: Preference
    private lateinit var prefExportDb: Preference
    private lateinit var prefImportDb: Preference
    private lateinit var prefVersionApp: Preference
    private lateinit var prefVersionCore: Preference
    private lateinit var prefVersionExchange: Preference
    private lateinit var prefVersionMerchant: Preference
    private lateinit var prefTest: Preference
    private lateinit var prefReset: Preference
    private val devPrefs by lazy {
        listOf(
            prefVersionCore,
            prefWithdrawTest,
            prefLogcat,
            prefExportDb,
            prefImportDb,
            prefVersionExchange,
            prefVersionMerchant,
            prefTest,
            prefReset,
        )
    }

    private val biometricEnrollLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            enableBiometrics(false)
        }
    }

    private val logLauncher = registerForActivityResult(CreateDocument("text/plain")) { uri ->
        settingsManager.exportLogcat(uri)
    }
    private val dbExportLauncher =
        registerForActivityResult(CreateDocument("application/json")) { uri ->
            Snackbar.make(requireView(), getString(R.string.settings_db_export_message), LENGTH_LONG).show()
            settingsManager.exportDb(uri)
        }
    private val dbImportLauncher =
        registerForActivityResult(OpenDocument()) { uri ->
            Snackbar.make(requireView(), getString(R.string.settings_db_import_message), LENGTH_LONG).show()
            findNavController().navigate(R.id.nav_main)
            settingsManager.importDb(uri)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_main, rootKey)
        prefDevMode = findPreference("pref_dev_mode")!!
        prefBiometricLock = findPreference("pref_biometric_lock")!!
        prefWithdrawTest = findPreference("pref_testkudos")!!
        prefLogcat = findPreference("pref_logcat")!!
        prefExportDb = findPreference("pref_export_db")!!
        prefImportDb = findPreference("pref_import_db")!!
        prefVersionApp = findPreference("pref_version_app")!!
        prefVersionCore = findPreference("pref_version_core")!!
        prefVersionExchange = findPreference("pref_version_protocol_exchange")!!
        prefVersionMerchant = findPreference("pref_version_protocol_merchant")!!
        prefTest = findPreference("pref_test")!!
        prefReset = findPreference("pref_reset")!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        biometricManager = BiometricManager.from(requireContext())

        prefVersionApp.summary = "$VERSION_NAME ($FLAVOR $VERSION_CODE)"
        prefVersionCore.summary = "${model.walletVersion} (${model.walletVersionHash?.take(7)})"
        model.exchangeVersion?.let { prefVersionExchange.summary = it }
        model.merchantVersion?.let { prefVersionMerchant.summary = it }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.getBiometricLockEnabled(requireContext()).collect { enabled ->
                    prefBiometricLock.isChecked = enabled
                }
            }
        }

        prefBiometricLock.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            if (enabled) {
                return@setOnPreferenceChangeListener enableBiometrics(true)
            } else {
                disableBiometrics()
                true
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.getDevModeEnabled(requireContext()).collect { enabled ->
                    prefDevMode.isChecked = enabled
                    devPrefs.forEach { it.isVisible = enabled }
                }
            }
        }

        prefDevMode.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDevModeEnabled(requireContext(), newValue as Boolean)
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                withdrawManager.withdrawTestStatus.collect { status ->
                    if (status is TestWithdrawStatus.None) return@collect
                    val loading = status is TestWithdrawStatus.Withdrawing
                    prefWithdrawTest.isEnabled = !loading
                    model.showProgressBar.value = loading
                    if (status is TestWithdrawStatus.Error) {
                        requireActivity().showError(R.string.withdraw_error_test, status.message)
                    }
                    withdrawManager.resetTestWithdrawal()
                }
            }
        }

        prefWithdrawTest.setOnPreferenceClickListener {
            withdrawManager.withdrawTestBalance()
            Snackbar.make(requireView(), getString(R.string.settings_test_withdrawal), LENGTH_LONG).show()
            findNavController().navigate(R.id.nav_main)
            true
        }

        prefLogcat.setOnPreferenceClickListener {
            logLauncher.launch("taler-wallet-log-${currentTimeMillis()}.txt")
            true
        }
        prefExportDb.setOnPreferenceClickListener {
            dbExportLauncher.launch("taler-wallet-db-${currentTimeMillis()}.json")
            true
        }
        prefImportDb.setOnPreferenceClickListener {
            showImportDialog()
            true
        }
        prefTest.setOnPreferenceClickListener {
            model.runIntegrationTest { error ->
                requireActivity().showError(error)
            }
            Snackbar.make(requireView(), getString(R.string.settings_test_running), LENGTH_LONG).show()
            findNavController().navigate(R.id.nav_main)
            true
        }
        prefReset.setOnPreferenceClickListener {
            showResetDialog()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().title = getString(R.string.menu_settings)
    }

    private fun enableBiometrics(prompt: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
                BIOMETRIC_SUCCESS -> {
                    settingsManager.setBiometricLockEnabled(requireContext(), true)
                    return true
                }

                BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.biometric_auth_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()

                    if (prompt) {
                        promptAuthEnrollment()
                    }
                }

                else -> Toast.makeText(
                    requireContext(),
                    getString(R.string.biometric_auth_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            val keyguardManager = requireContext()
                .getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager.isDeviceSecure) {
                settingsManager.setBiometricLockEnabled(requireContext(), true)
                return true
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.biometric_auth_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()

                if (prompt) {
                promptAuthEnrollment()
                }
            }
        }

        return false
    }

    /**
     * Prompt the user to enroll valid credentials
     */
    private fun promptAuthEnrollment() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                )
            }
            biometricEnrollLauncher.launch(intent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val intent = Intent(ACTION_FINGERPRINT_ENROLL)
            biometricEnrollLauncher.launch(intent)
        } else {
            val intent = Intent(ACTION_SECURITY_SETTINGS)
            biometricEnrollLauncher.launch(intent)
        }
    }

    private fun disableBiometrics() {
        settingsManager.setBiometricLockEnabled(requireContext(), false)
    }

    private fun showImportDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Material3)
            .setMessage(R.string.settings_dialog_import_message)
            .setNegativeButton(R.string.import_db) { _, _ ->
                dbImportLauncher.launch(arrayOf("application/json"))
            }
            .setPositiveButton(R.string.cancel) { _, _ ->
                Snackbar.make(requireView(), getString(R.string.settings_alert_import_canceled), LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Material3)
            .setMessage(R.string.settings_dialog_reset_message)
            .setNegativeButton(R.string.reset) { _, _ ->
                settingsManager.clearDb {
                    model.dangerouslyReset()
                }
                Snackbar.make(requireView(), getString(R.string.settings_alert_reset_done), LENGTH_SHORT).show()
            }
            .setPositiveButton(R.string.cancel) { _, _ ->
                Snackbar.make(requireView(), getString(R.string.settings_alert_reset_canceled), LENGTH_SHORT).show()
            }
            .show()
    }
}
