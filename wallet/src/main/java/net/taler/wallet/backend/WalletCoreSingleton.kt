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

package net.taler.wallet.backend

import android.app.Application
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * App-weiter, referenzgezaehlter Zugriff auf die eine wallet-core-Instanz
 * dieses Prozesses.
 *
 * [BackendManager.run] wird durch ein prozessweites (statisches) Flag
 * abgesichert - eine zweite [WalletBackendApi] im selben Prozess wuerde also
 * nie wirklich starten, und jeder Aufruf darauf wuerde ohne Fehlermeldung
 * ewig haengen. Alle Nutzer im Prozess teilen sich deshalb diese eine
 * Instanz, statt je eine eigene zu erzeugen. (Bis zum Wegfall der
 * App-zu-App-Schnittstelle war das neben MainViewModel auch ein gebundener
 * Service fuer Signal - daher die unten beschriebenen Race-Faelle.)
 *
 * [acquire] startet wallet-core beim allerersten Aufruf (egal ob von der
 * Activity oder vom Service) und liefert danach fuer weitere Aufrufer
 * dieselbe Instanz zurueck. [release] faehrt wallet-core erst herunter, wenn
 * kein Aufrufer mehr eine Referenz haelt.
 */
object WalletCoreSingleton : InitReceiver, NotificationReceiver {
    private const val TAG = "WalletCoreSingleton"

    /**
     * Obergrenze fuer das Warten auf ein noch laufendes Herunterfahren in
     * [acquire], siehe dort. Bewusst deutlich kuerzer als
     * BackendManager.REQUEST_TIMEOUT_MS (60s) - "shutdown" ist ein lokaler,
     * synchroner wallet-core-Aufruf ohne Netzwerk-I/O und antwortet unter
     * normalen Umstaenden sofort. Diese Grenze verhindert, dass ein
     * hartnaeckig haengender alter Kern den Hauptthread eines neuen
     * acquire()-Aufrufers (MainViewModel-Init, TalerLinkService.onCreate())
     * fuer volle 60s blockiert (ANR-Risiko).
     */
    private const val TEARDOWN_JOIN_TIMEOUT_MS = 5_000L

    private val initReceivers = CopyOnWriteArrayList<InitReceiver>()
    private val notificationReceivers = CopyOnWriteArrayList<NotificationReceiver>()

    private var api: WalletBackendApi? = null
    private var refCount = 0

    /**
     * Fuehrt das asynchrone Herunterfahren (stopWallet(), siehe
     * WalletBackendApi) der zuletzt freigegebenen Instanz aus. Absichtlich
     * NICHT ueber den Object-Monitor synchronisiert erreichbar - stopWallet()
     * ruft nicht in WalletCoreSingleton zurueck, sonst wuerde
     * runBlocking { teardownJob.join() } unten sich selbst blockieren.
     */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var teardownJob: Job? = null

    /**
     * Root cause des Bugs "wallet core did not respond" (siehe
     * HANDOFF/Statusbericht): [release] unten faehrt die alte Instanz nur
     * ASYNCHRON herunter (WalletBackendApi.stopWallet() sendet "shutdown" und
     * wartet auf Antwort, bevor es BackendManager.destroy() aufruft, welches
     * erst dort das prozessweite coreRunning-Flag zuruecksetzt). TalerLinkService
     * bindet/entbindet pro Aufruf neu (siehe dortiger Kommentar) und durchlaeuft
     * release()+acquire() dabei oft binnen Millisekunden - z.B. weil parallel
     * MainViewModel fuer die vom Deep-Link geoeffnete Taler-UI ebenfalls
     * acquire() aufruft. Ohne dieses Warten haette ein solches acquire(), das
     * WAEHREND des noch laufenden Shutdowns eine neue Instanz erzeugt, das noch
     * nicht zurueckgesetzte coreRunning-Flag gesehen: walletCore.run() der
     * neuen Instanz waere lautlos verweigert worden (BackendManager: "refusing
     * to run a second wallet-core in this process"), und jeder Request auf
     * dieser toten Instanz waere nach 60s mit exakt diesem Fehler getimeoutet.
     * Deshalb hier zuerst synchron auf ein noch laufendes Herunterfahren
     * warten, bevor ueberhaupt eine neue Instanz entsteht.
     */
    @Synchronized
    fun acquire(app: Application, config: WalletRunConfig): WalletBackendApi {
        refCount++
        api?.let {
            Log.i(TAG, "acquire(): reusing existing instance, refCount=$refCount")
            return it
        }
        teardownJob?.let { job ->
            teardownJob = null
            Log.i(TAG, "acquire(): waiting for in-flight teardown to finish first")
            runBlocking {
                val finished = withTimeoutOrNull(TEARDOWN_JOIN_TIMEOUT_MS) { job.join() }
                if (finished == null) {
                    Log.e(
                        TAG,
                        "teardown did not finish within ${TEARDOWN_JOIN_TIMEOUT_MS}ms - " +
                            "proceeding anyway, a stale wallet-core may briefly refuse to start"
                    )
                }
            }
        }
        val created = WalletBackendApi(app, config, this, this)
        api = created
        Log.i(TAG, "acquire(): starting new instance, refCount=$refCount")
        created.startWallet()
        return created
    }

    @Synchronized
    fun release() {
        if (refCount <= 0) return
        refCount--
        Log.i(TAG, "release(): refCount=$refCount")
        if (refCount == 0) {
            val toStop = api
            api = null
            teardownJob = teardownScope.launch {
                toStop?.stopWallet()
                Log.i(TAG, "release(): teardown finished")
            }
        }
    }

    fun addInitReceiver(r: InitReceiver) {
        initReceivers.add(r)
    }

    fun removeInitReceiver(r: InitReceiver) {
        initReceivers.remove(r)
    }

    fun addNotificationReceiver(r: NotificationReceiver) {
        notificationReceivers.add(r)
    }

    fun removeNotificationReceiver(r: NotificationReceiver) {
        notificationReceivers.remove(r)
    }

    override fun onInitReceived(init: InitResponse) {
        initReceivers.forEach { it.onInitReceived(init) }
    }

    override fun onInitErrorReceived(error: TalerErrorInfo) {
        initReceivers.forEach { it.onInitErrorReceived(error) }
    }

    override fun onNotificationReceived(payload: NotificationPayload) {
        notificationReceivers.forEach { it.onNotificationReceived(payload) }
    }
}
