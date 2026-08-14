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

package net.taler.wallet.link

import net.taler.wallet.transactions.TransactionMajorState

/**
 * Ausgelagert aus TalerLinkService (REVIEW.md P3), damit die Zustandsmaschine
 * ohne wallet-core/Service-Kontext testbar ist - siehe TalerTransactionStateMapperTest.
 */
object TalerTransactionStateMapper {

    /**
     * TransactionMajorState (TransactionState.kt) ist die verifizierte,
     * tatsaechliche Zustandsmaschine von wallet-core - nicht geraten. Dialog
     * ist der Wartezustand vor einer Nutzerentscheidung, Done heisst
     * angenommen/abgeschlossen, Expired ist ein echter, eigener Zustand (nicht
     * nur ein Fehlercode). Pending, Finalizing, Suspended, SuspendedFinalizing,
     * SuspendedAborting, Aborting, Unknown und None sind Uebergangs- bzw.
     * unklare Zustaende - bewusst konservativ als
     * UNBEKANNT_OFFLINE behandelt statt hier weiter zu spekulieren.
     */
    fun statusFromMajorState(major: TransactionMajorState): TalerOperationStatus = when (major) {
        TransactionMajorState.Dialog -> TalerOperationStatus.OFFEN
        TransactionMajorState.Done -> TalerOperationStatus.ANGENOMMEN
        TransactionMajorState.Expired -> TalerOperationStatus.ABGELAUFEN
        TransactionMajorState.Failed,
        TransactionMajorState.Aborted,
        TransactionMajorState.Deleted -> TalerOperationStatus.UNGUELTIG
        else -> TalerOperationStatus.UNBEKANNT_OFFLINE
    }
}
