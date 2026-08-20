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

package net.taler.wallet.settings

import android.app.Activity.RESULT_OK
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.os.Build
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.ACTION_FINGERPRINT_ENROLL
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DomainAdd
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import net.taler.wallet.BuildConfig
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletDatabaseBackend
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.withdraw.TestWithdrawStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    model: MainViewModel,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onNavigate: NavigateCallback,
    onShowError: (TalerErrorInfo) -> Unit,
) {
    val context = LocalContext.current
    val dbExportMessage = stringResource(R.string.settings_db_export_message)
    val dbImportMessage = stringResource(R.string.settings_db_import_message)
    val testWithdrawalMessage = stringResource(R.string.settings_test_withdrawal)
    val importCanceledMessage = stringResource(R.string.settings_alert_import_canceled)
    val testRunningMessage = stringResource(R.string.settings_test_running)
    val resetDoneMessage = stringResource(R.string.settings_alert_reset_done)
    val resetCanceledMessage = stringResource(R.string.settings_alert_reset_canceled)
    val migrateDoneMessage = stringResource(R.string.settings_db_migrate_done)
    val migrateCanceledMessage = stringResource(R.string.settings_db_migrate_canceled)
    val biometricAuthUnavailableMessage = stringResource(R.string.biometric_auth_unavailable)
    val scope = rememberCoroutineScope()
    val settingsManager = model.settingsManager
    val withdrawManager = model.withdrawManager

    val biometricLockEnabled by settingsManager.getBiometricLockEnabled(context).collectAsState(false)
    val devModeEnabled by settingsManager.getDevModeEnabled(context).collectAsState(false)
    val withdrawTestStatus by withdrawManager.withdrawTestStatus.collectAsState()

    val walletVersion = model.walletVersion
    val walletVersionHash = model.walletVersionHash?.take(7)
    val exchangeVersion = model.exchangeVersion
    val merchantVersion = model.merchantVersion

    val logLauncher = rememberLauncherForActivityResult(CreateDocument("text/plain")) { uri ->
        uri?.let { settingsManager.exportLogcat(it) }
    }
    val dbExportLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        uri?.let {
            scope.launch { snackbarHostState.showSnackbar(dbExportMessage) }
            settingsManager.exportDb(it)
        }
    }
    val dbImportLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            scope.launch { snackbarHostState.showSnackbar(dbImportMessage) }
            onNavigate(WalletDestination.Main, true)
            settingsManager.importDb(it)
        }
    }
    val rawDbExportLauncher = rememberLauncherForActivityResult(CreateDocument("application/octet-stream")) { uri ->
        uri?.let { settingsManager.exportRawDb(it) }
    }
    var pendingDbExport by remember { mutableStateOf<DbExportType?>(null) }

    val biometricEnrollLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            enableBiometrics(context, settingsManager, false, biometricAuthUnavailableMessage) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsItem(
            title = stringResource(R.string.exchange_settings_title),
            summary = stringResource(R.string.exchange_settings_summary),
            icon = Icons.Default.Dns,
            onClick = { onNavigate(WalletDestination.ExchangeList, false) }
        )

        SettingsItem(
            title = stringResource(R.string.settings_bank_accounts),
            summary = stringResource(R.string.settings_bank_accounts_summary),
            icon = Icons.Default.AccountBalance,
            onClick = { onNavigate(WalletDestination.BankAccounts(), false) }
        )

        SettingsItem(
            title = stringResource(R.string.settings_donau),
            summary = stringResource(R.string.settings_donau_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_donau),
            onClick = { onNavigate(WalletDestination.SetDonau(), false) }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.settings_lock_auth),
            summary = stringResource(R.string.settings_lock_auth_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_shield),
            checked = biometricLockEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    enableBiometrics(context, settingsManager, true, biometricAuthUnavailableMessage) {
                        biometricEnrollLauncher.launch(it)
                    }
                } else {
                    settingsManager.setBiometricLockEnabled(context, false)
                }
            }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.settings_dev_mode),
            summary = stringResource(R.string.settings_dev_mode_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_developer_mode),
            checked = devModeEnabled,
            onCheckedChange = { enabled ->
                settingsManager.setDevModeEnabled(context, enabled)
            }
        )

        if (devModeEnabled) {
            HorizontalDivider()

            SettingsItem(
                title = stringResource(R.string.exchange_list_add_dev),
                icon = Icons.Default.DomainAdd,
                onClick = {
                    model.exchangeManager.addDevExchanges()
                    onNavigate(WalletDestination.ExchangeList, false)
                }
            )

            SettingsItem(
                title = stringResource(R.string.settings_withdraw_testkudos),
                summary = stringResource(R.string.settings_withdraw_testkudos_summary),
                icon = Icons.Default.LocalAtm,
                enabled = withdrawTestStatus !is TestWithdrawStatus.Withdrawing,
                onClick = {
                    withdrawManager.withdrawTestBalance()
                    scope.launch { snackbarHostState.showSnackbar(testWithdrawalMessage) }
                    onNavigate(WalletDestination.Main, true)
                }
            )

            SettingsItem(
                title = stringResource(R.string.settings_logcat),
                summary = stringResource(R.string.settings_logcat_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_bug_report),
                onClick = { logLauncher.launch("taler-wallet-log-${System.currentTimeMillis()}.txt") }
            )

            SettingsItem(
                title = stringResource(R.string.settings_stats),
                summary = stringResource(R.string.settings_stats_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_stats),
                onClick = { onNavigate(WalletDestination.PerformanceStats, false) }
            )

            SettingsItem(
                title = stringResource(R.string.settings_db_export),
                summary = stringResource(R.string.settings_db_export_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_unarchive),
                onClick = { pendingDbExport = DbExportType.Json }
            )

            SettingsItem(
                title = stringResource(R.string.settings_db_export_raw),
                summary = stringResource(R.string.settings_db_export_raw_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_database),
                onClick = { pendingDbExport = DbExportType.Raw }
            )

            SettingsItem(
                title = stringResource(R.string.settings_db_import),
                summary = stringResource(R.string.settings_db_import_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_archive),
                onClick = {
                    MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.settings_dialog_import_message)
                        .setNegativeButton(R.string.import_db) { _, _ ->
                            dbImportLauncher.launch(arrayOf("application/json"))
                        }
                        .setPositiveButton(R.string.cancel) { _, _ ->
                            scope.launch { snackbarHostState.showSnackbar(importCanceledMessage) }
                        }
                        .show()
                }
            )

            if (model.databaseBackend != WalletDatabaseBackend.Sqlite) SettingsItem(
                title = stringResource(R.string.settings_migrate_db),
                summary = stringResource(R.string.settings_migrate_db_summary),
                icon = Icons.Default.Memory,
                onClick = {
                    MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.settings_dialog_migrate_db_message)
                        .setNegativeButton(R.string.settings_migrate_db) { _, _ ->
                            model.enableMigrateNativeDb { onShowError(it) }
                            scope.launch { snackbarHostState.showSnackbar(migrateDoneMessage) }
                        }
                        .setPositiveButton(R.string.cancel) { _, _ ->
                            scope.launch { snackbarHostState.showSnackbar(migrateCanceledMessage) }
                        }
                        .show()
                }
            )

            SettingsItem(
                title = stringResource(R.string.settings_version_app),
                summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR} ${BuildConfig.VERSION_CODE})",
                icon = ImageVector.vectorResource(R.drawable.ic_account_balance_wallet),
            )

            SettingsItem(
                title = stringResource(R.string.settings_version_core),
                summary = "$walletVersion ($walletVersionHash)",
                icon = ImageVector.vectorResource(R.drawable.ic_adjust),
            )

            if (exchangeVersion != null) {
                SettingsItem(
                    title = stringResource(R.string.settings_version_protocol_exchange),
                    summary = exchangeVersion,
                    icon = ImageVector.vectorResource(R.drawable.ic_account_balance),
                )
            }

            if (merchantVersion != null) {
                SettingsItem(
                    title = stringResource(R.string.settings_version_protocol_merchant),
                    summary = merchantVersion,
                    icon = ImageVector.vectorResource(R.drawable.ic_store_mall),
                )
            }

            SettingsItem(
                title = stringResource(R.string.settings_test),
                summary = stringResource(R.string.settings_test_summary),
                icon = Icons.Default.VideoLibrary,
                onClick = {
                    settingsManager.runIntegrationTest { onShowError(it) }
                    scope.launch { snackbarHostState.showSnackbar(testRunningMessage) }
                    onNavigate(WalletDestination.Main, true)
                }
            )

            SettingsItem(
                title = stringResource(R.string.settings_reset),
                summary = stringResource(R.string.settings_reset_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_nuke),
                onClick = {
                    MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.settings_dialog_reset_message)
                        .setNegativeButton(R.string.reset) { _, _ ->
                            settingsManager.clearDb {
                                model.dangerouslyReset()
                            }
                            scope.launch { snackbarHostState.showSnackbar(resetDoneMessage) }
                        }
                        .setPositiveButton(R.string.cancel) { _, _ ->
                            scope.launch { snackbarHostState.showSnackbar(resetCanceledMessage) }
                        }
                        .show()
                }
            )

            pendingDbExport?.let { exportType ->
                AlertDialog(
                    onDismissRequest = { pendingDbExport = null },
                    title = { Text(stringResource(R.string.wallet_export_database)) },
                    text = { Text(stringResource(R.string.wallet_export_database_warning)) },
                    confirmButton = {
                        Button(onClick = {
                            pendingDbExport = null
                            when (exportType) {
                                DbExportType.Json ->
                                    dbExportLauncher.launch("taler-wallet-db-${System.currentTimeMillis()}.json")
                                DbExportType.Raw ->
                                    rawDbExportLauncher.launch("taler-wallet-db-${System.currentTimeMillis()}.sqlite3")
                            }
                        }) {
                            Text(stringResource(R.string.wallet_export_database_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDbExport = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = if (icon != null) 24.dp else 0.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(bottom = 3.dp),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = if (icon != null) 24.dp else 0.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            modifier = Modifier.padding(start = 16.dp),
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun enableBiometrics(
    context: Context,
    settingsManager: SettingsManager,
    prompt: Boolean,
    biometricAuthUnavailableMessage: String,
    onPromptEnrollment: (Intent) -> Unit,
): Boolean {
    val biometricManager = BiometricManager.from(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BIOMETRIC_SUCCESS -> {
                settingsManager.setBiometricLockEnabled(context, true)
                return true
            }

            BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(
                    context,
                    biometricAuthUnavailableMessage,
                    Toast.LENGTH_SHORT,
                ).show()

                if (prompt) {
                    promptAuthEnrollment(onPromptEnrollment)
                }
            }

            else -> Toast.makeText(
                context,
                biometricAuthUnavailableMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    } else {
        val keyguardManager = context.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isDeviceSecure) {
            settingsManager.setBiometricLockEnabled(context, true)
            return true
        } else {
            Toast.makeText(
                context,
                biometricAuthUnavailableMessage,
                Toast.LENGTH_SHORT,
            ).show()

            if (prompt) {
                promptAuthEnrollment(onPromptEnrollment)
            }
        }
    }

    return false
}

private fun promptAuthEnrollment(onPromptEnrollment: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(
                EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            )
        }
        onPromptEnrollment(intent)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val intent = Intent(ACTION_FINGERPRINT_ENROLL)
        onPromptEnrollment(intent)
    } else {
        val intent = Intent(ACTION_SECURITY_SETTINGS)
        onPromptEnrollment(intent)
    }
}

private enum class DbExportType { Json, Raw }
