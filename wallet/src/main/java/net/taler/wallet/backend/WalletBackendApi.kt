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

package net.taler.wallet.backend

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import net.taler.wallet.backend.TalerErrorCode.NONE
import org.json.JSONObject
import java.io.File

const val WALLET_DB = "talerwalletdb.sqlite3"

@OptIn(DelicateCoroutinesApi::class)
class WalletBackendApi(
    private val app: Application,
    private val initialConfig: WalletRunConfig,
    private val initReceiver: InitReceiver,
    notificationReceiver: NotificationReceiver,
) {

    private val backendManager = BackendManager(notificationReceiver)

    /**
     * Root cause des Bugs "Fehler-Screen beim Start ueber Signal": startWallet()
     * feuert sendInitMessage() nur fire-and-forget (GlobalScope.launch) und
     * kehrt sofort zurueck. TalerLinkService bindet/antwortet aber sofort nach
     * onCreate() (siehe dortiger Kommentar zu TalerLinkClient), sodass der
     * allererste previewForUri/statusForUri-Aufruf ueber Signal seinen
     * preparePeerPushCredit/preparePeerPullDebit-Request an wallet-core schickt,
     * WAEHREND "init" dort noch nicht verarbeitet ist - das schlaegt fehl bzw.
     * haengt und zeigt Signal einen Fehler. Beim MainViewModel-Pfad (Wallet-App
     * zuerst geoeffnet) tritt der Bug nicht auf, weil dort zwischen App-Start
     * und erster Nutzeraktion genug Zeit vergeht, bis "init" durch ist - reiner
     * Zufall, kein Fix. [awaitInit] macht diese Abhaengigkeit explizit: wird
     * einmalig durch [sendInitMessage] abgeschlossen (Erfolg oder Fehler) und
     * ist danach fuer alle weiteren Aufrufer sofort fertig.
     */
    private val initDeferred = CompletableDeferred<WalletResponse<InitResponse>>()

    suspend fun awaitInit(): WalletResponse<InitResponse> = initDeferred.await()

    fun startWallet() {
        // Verteidigungslinie gegen den Bug "wallet core did not respond":
        // run() wird durch WalletCoreSingleton eigentlich nie mehr fuer eine
        // zweite Instanz aufgerufen (siehe dortiger Kommentar in acquire()),
        // aber falls doch - z.B. durch kuenftigen Code, der WalletBackendApi
        // ohne den Singleton erzeugt - soll der Aufrufer SOFORT einen Fehler
        // sehen statt 60s auf eine Antwort zu warten, die nie kommt, weil
        // backendManager.run() lautlos verweigert wurde und der native Kern
        // gar nicht erst gestartet ist.
        if (!backendManager.run()) {
            val error = TalerErrorInfo.makeCustomError(
                message = "wallet-core already running in this process",
            )
            initReceiver.onInitErrorReceived(error)
            initDeferred.complete(WalletResponse.Error(error))
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            sendInitMessage()
        }
    }

    /**
     * Faehrt wallet-core herunter. Bewusst suspend (nicht mehr fire-and-forget
     * per GlobalScope.launch) - WalletCoreSingleton.acquire() muss den
     * Abschluss dieses Vorgangs abwarten koennen, bevor es eine neue Instanz
     * erzeugt und startet. Sonst kann eine neue Instanz walletCore.run()
     * aufrufen, WAEHREND diese alte Instanz das prozessweite
     * BackendManager.coreRunning-Flag noch nicht zurueckgesetzt hat - die neue
     * Instanz wird dann lautlos verweigert (siehe Bug "wallet core did not
     * respond").
     */
    suspend fun stopWallet() {
        sendRequest("shutdown")
        backendManager.destroy()
    }

    private suspend fun sendInitMessage() {
        val db = if (File(app.filesDir, "talerwalletdb.sql").isFile) {
            // can be removed after a reasonable migration period (2024-02-02)
            "${app.filesDir}/talerwalletdb.sql"
        } else {
            "${app.filesDir}/${WALLET_DB}"
        }

        request("init", InitResponse.serializer()) {
            put("persistentStoragePath", db)
            put("logLevel", "INFO")
            put("config", JSONObject(BackendManager.json.encodeToString(initialConfig)))
        }.onSuccess { response ->
            initReceiver.onInitReceived(response)
            initDeferred.complete(WalletResponse.Success(response))
        }.onError { error ->
            initReceiver.onInitErrorReceived(error)
            initDeferred.complete(WalletResponse.Error(error))
        }
    }

    suspend fun setWalletConfig(config: WalletRunConfig): WalletResponse<InitResponse> {
        return request("initWallet", InitResponse.serializer()) {
            put("config", JSONObject(BackendManager.json.encodeToString(config)))
        }
    }

    suspend fun migrateDatabase(progressToken: String): WalletResponse<MigrateDatabaseResponse> {
        return request(
            "migrateDatabase",
            MigrateDatabaseResponse.serializer(),
            timeoutMs = null,
        ) {
            put("progressToken", progressToken)
        }
    }

    suspend fun cancelDatabaseMigration(progressToken: String): WalletResponse<Unit> {
        return request("cancelProgressToken") {
            put("operation", "migrateDatabase")
            put("progressToken", progressToken)
        }
    }

    suspend fun sendRequest(
        operation: String,
        args: JSONObject? = null,
        timeoutMs: Long? = BackendManager.REQUEST_TIMEOUT_MS,
    ): ApiResponse {
        return backendManager.send(operation, args, timeoutMs)
    }

    suspend inline fun <reified T> request(
        operation: String,
        serializer: KSerializer<T>? = null,
        timeoutMs: Long? = BackendManager.REQUEST_TIMEOUT_MS,
        noinline args: (JSONObject.() -> JSONObject)? = null,
    ): WalletResponse<T> = withContext(Dispatchers.Default) {
        val json = BackendManager.json
        try {
            when (val response = sendRequest(operation, args?.invoke(JSONObject()), timeoutMs)) {
                is ApiResponse.Response -> {
                    val t: T = serializer?.let {
                        json.decodeFromJsonElement(serializer, response.result)
                    } ?: Unit as T
                    WalletResponse.Success(t)
                }

                is ApiResponse.Error -> {
                    val error: TalerErrorInfo = json.decodeFromJsonElement(response.error)
                    WalletResponse.Error(error)
                }
            }
        } catch (e: Exception) {
            val info = TalerErrorInfo(NONE, "", e.toString())
            WalletResponse.Error(info)
        }
    }

    // Returns raw JSON response instead of serialized object
    suspend inline fun rawRequest(
        operation: String,
        noinline args: (JSONObject.() -> JSONObject)? = null,
    ): WalletResponse<JsonObject> = withContext(Dispatchers.Default) {
        val json = BackendManager.json
        try {
            when (val response = sendRequest(operation, args?.invoke(JSONObject()))) {
                is ApiResponse.Response -> {
                    WalletResponse.Success(response.result)
                }

                is ApiResponse.Error -> {
                    val error: TalerErrorInfo = json.decodeFromJsonElement(response.error)
                    WalletResponse.Error(error)
                }
            }
        } catch (e: Exception) {
            val info = TalerErrorInfo(NONE, "", e.toString())
            WalletResponse.Error(info)
        }
    }
}
