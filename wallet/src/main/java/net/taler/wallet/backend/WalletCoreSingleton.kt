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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * App-weiter, referenzgezaehlter Zugriff auf die eine wallet-core-Instanz
 * dieses Prozesses.
 *
 * [BackendManager.run] wird durch ein prozessweites (statisches) Flag
 * abgesichert - eine zweite [WalletBackendApi] im selben Prozess wuerde also
 * nie wirklich starten, und jeder Aufruf darauf wuerde ohne Fehlermeldung
 * ewig haengen. MainViewModel (UI) und TalerLinkService (lokale
 * Signal-Schnittstelle, siehe docs/API.md) teilen sich deshalb diese eine
 * Instanz, statt je eine eigene zu erzeugen.
 *
 * [acquire] startet wallet-core beim allerersten Aufruf (egal ob von der
 * Activity oder vom Service) und liefert danach fuer weitere Aufrufer
 * dieselbe Instanz zurueck. [release] faehrt wallet-core erst herunter, wenn
 * kein Aufrufer mehr eine Referenz haelt.
 */
object WalletCoreSingleton : InitReceiver, NotificationReceiver {
    private val initReceivers = CopyOnWriteArrayList<InitReceiver>()
    private val notificationReceivers = CopyOnWriteArrayList<NotificationReceiver>()

    private var api: WalletBackendApi? = null
    private var refCount = 0

    @Synchronized
    fun acquire(app: Application, config: WalletRunConfig): WalletBackendApi {
        refCount++
        api?.let { return it }
        val created = WalletBackendApi(app, config, this, this)
        api = created
        created.startWallet()
        return created
    }

    @Synchronized
    fun release() {
        if (refCount <= 0) return
        refCount--
        if (refCount == 0) {
            api?.stopWallet()
            api = null
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
