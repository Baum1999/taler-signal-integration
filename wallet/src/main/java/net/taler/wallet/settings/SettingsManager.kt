/*
 * This file is part of GNU Taler
 * (C) 2022 Taler Systems S.A.
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

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WALLET_DB
import net.taler.wallet.main.ViewMode
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.backend.WalletResponse.Error
import net.taler.wallet.backend.WalletResponse.Success
import net.taler.wallet.balances.BalanceManager
import net.taler.wallet.main.TAG
import org.json.JSONObject
import java.io.File

class SettingsManager(
    private val context: Context,
    private val api: WalletBackendApi,
    private val scope: CoroutineScope,
    private val balanceManager: BalanceManager,
) {
    private val mPerformanceTable = MutableStateFlow<PerformanceTable?>(null)
    val performanceTable: StateFlow<PerformanceTable?> = mPerformanceTable

    fun getViewMode(c: Context) = c.userPreferencesDataStore.data.map { prefs ->
        if (prefs.hasViewMode()) {
            ViewMode.fromPrefs(prefs.viewMode)
        } else {
            null
        }
    }

    fun saveViewMode(c: Context, viewMode: ViewMode?) = scope.launch {
        c.userPreferencesDataStore.updateData { current ->
            if (viewMode != null) {
                current.toBuilder()
                    .setViewMode(viewMode.toPrefs())
                    .build()
            } else {
                current.toBuilder()
                    .clearViewMode()
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

    fun saveActionButtonUsed(c: Context) = scope.launch {
        c.userPreferencesDataStore.updateData { current ->
            current.toBuilder()
                .setActionButtonUsed(true)
                .build()
        }
    }

    fun getDevModeEnabled(c: Context) = c.userPreferencesDataStore.data.map { prefs ->
        if (prefs.hasDevModeEnabled()) {
            prefs.devModeEnabled
        } else {
            false
        }
    }

    fun setDevModeEnabled(c: Context, enabled: Boolean) = scope.launch {
        c.userPreferencesDataStore.updateData { current ->
            current.toBuilder()
                .setDevModeEnabled(enabled)
                .build()
        }
    }

    fun getBiometricLockEnabled(c: Context) = c.userPreferencesDataStore.data.map { prefs ->
        if (prefs.hasBiometricLockEnabled()) {
            prefs.biometricLockEnabled
        } else {
            false
        }
    }

    fun setBiometricLockEnabled(c: Context, enabled: Boolean) = scope.launch {
        c.userPreferencesDataStore.updateData { current ->
            current.toBuilder()
                .setBiometricLockEnabled(enabled)
                .build()
        }
    }

    fun exportLogcat(uri: Uri?) {
        if (uri == null) {
            onLogExportError()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    val command = arrayOf("logcat", "-d", "*:V")
                    val proc = Runtime.getRuntime().exec(command)
                    proc.inputStream.copyTo(outputStream)
                } ?: onLogExportError()
            } catch (e: Exception) {
                Log.e(SettingsManager::class.simpleName, "Error exporting log: ", e)
                onLogExportError()
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.settings_logcat_success, LENGTH_LONG).show()
            }
        }
    }

    private fun onLogExportError() {
        Toast.makeText(context, R.string.settings_logcat_error, LENGTH_LONG).show()
    }

    fun exportDb(uri: Uri?) {
        if (uri == null) {
            onDbExportError()
            return
        }

        scope.launch(Dispatchers.IO) {
            when (val response = api.rawRequest("exportDb")) {
                is Success -> {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                            val data = Json.encodeToString(response.result)
                            val writer = outputStream.bufferedWriter()
                            writer.write(data)
                            writer.close()
                        }
                    } catch(e: Exception) {
                        Log.e(SettingsManager::class.simpleName, "Error exporting db: ", e)
                        withContext(Dispatchers.Main) {
                            onDbExportError()
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.settings_db_export_success, LENGTH_LONG).show()
                    }
                }
                is Error -> {
                    Log.e(SettingsManager::class.simpleName, "Error exporting db: ${response.error}")
                    withContext(Dispatchers.Main) {
                        onDbExportError()
                    }
                    return@launch
                }
            }
        }
    }

    fun exportRawDb(uri: Uri?) {
        if (uri == null) {
            onDbExportError()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    val dbFile = File(context.filesDir, WALLET_DB)
                    dbFile.inputStream().use { it.copyTo(outputStream) }
                } ?: onDbExportError()
            } catch (e: Exception) {
                Log.e(SettingsManager::class.simpleName, "Error exporting raw db: ", e)
                withContext(Dispatchers.Main) {
                    onDbExportError()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.settings_db_export_success, LENGTH_LONG).show()
            }
        }
    }

    fun importDb(uri: Uri?) {
        if (uri == null) {
            onDbImportError()
            return
        }

        scope.launch(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use {  inputStream ->
                try {
                    val reader = inputStream.bufferedReader()
                    val strData = reader.readText()
                    reader.close()
                    val jsonData = JSONObject(strData)
                    when (val response = api.rawRequest("importDb") {
                        put("dump", jsonData)
                    }) {
                        is Success -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.settings_db_import_success, LENGTH_LONG).show()
                                balanceManager.loadAssets(true)
                            }
                        }
                        is Error -> {
                            Log.e(SettingsManager::class.simpleName, "Error importing db: ${response.error}")
                            withContext(Dispatchers.Main) {
                                onDbImportError()
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.e(SettingsManager::class.simpleName, "Error importing db: ", e)
                    withContext(Dispatchers.Main) {
                        onDbImportError()
                    }
                    return@launch
                }
            }
        }
    }

    fun clearDb(onSuccess: () -> Unit) {
        scope.launch {
            when (val response = api.rawRequest("clearDb")) {
                is Success -> {
                    onSuccess()
                    balanceManager.resetBalances()
                }
                is Error -> {
                    Log.e(SettingsManager::class.simpleName, "Error cleaning db: ${response.error}")
                    onDbClearError()
                }
            }
        }
    }

    private fun onDbExportError() {
        Toast.makeText(context, R.string.settings_db_export_error, LENGTH_LONG).show()
    }

    private fun onDbImportError() {
        Toast.makeText(context, R.string.settings_db_import_error, LENGTH_LONG).show()
    }

    private fun onDbClearError() {
        Toast.makeText(context, R.string.settings_db_clear_error, LENGTH_LONG).show()
    }

    fun runIntegrationTest(onError: (error: TalerErrorInfo) -> Unit) {
        scope.launch {
            api.request<Unit>("runIntegrationTestV2") {
                put("amountToWithdraw", "KUDOS:42")
                put("amountToSpend", "KUDOS:23")
                put("corebankApiBaseUrl", "https://bank.demo.taler.net/")
                put("exchangeBaseUrl", "https://exchange.demo.taler.net/")
                put("merchantBaseUrl", "https://backend.demo.taler.net/instances/sandbox/")
                put("merchantAuthToken", "secret-token:sandbox")
            }.onError(onError)
        }
    }

    fun loadPerformanceStats(limit: Int? = 10) {
        scope.launch {
            api.request(
                "testingGetPerformanceStats",
                TestingGetPerformanceStatsResponse.serializer(),
            ) {
                limit?.let { put("limit", limit) }
                this
            }.onError { error ->
                Log.e(TAG, "got testingGetPerformanceStats error result $error")
            }.onSuccess { res ->
                mPerformanceTable.value = res.stats
            }
        }
    }
}
