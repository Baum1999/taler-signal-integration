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
import org.junit.Assert.assertEquals
import org.junit.Test

class TalerTransactionStateMapperTest {

    /**
     * Bewusst KEIN `else`-Zweig (REVIEW.md P3): faellt wallet-core-Upstream
     * ein neuer TransactionMajorState-Wert ein, kompiliert diese Testdatei
     * nicht mehr, statt dass der Test still gruen bleibt und
     * TalerTransactionStateMapper den neuen Wert unbemerkt auf
     * UNBEKANNT_OFFLINE fallen laesst.
     */
    private fun expected(major: TransactionMajorState): TalerOperationStatus = when (major) {
        TransactionMajorState.Dialog -> TalerOperationStatus.OFFEN
        TransactionMajorState.Done -> TalerOperationStatus.ANGENOMMEN
        TransactionMajorState.Expired -> TalerOperationStatus.ABGELAUFEN
        TransactionMajorState.Failed -> TalerOperationStatus.UNGUELTIG
        TransactionMajorState.Aborted -> TalerOperationStatus.UNGUELTIG
        TransactionMajorState.Deleted -> TalerOperationStatus.UNGUELTIG
        TransactionMajorState.Unknown -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.None -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.Pending -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.Aborting -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.Finalizing -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.Suspended -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.SuspendedFinalizing -> TalerOperationStatus.UNBEKANNT_OFFLINE
        TransactionMajorState.SuspendedAborting -> TalerOperationStatus.UNBEKANNT_OFFLINE
    }

    @Test
    fun mapsEveryTransactionMajorStateValueAsExpected() {
        for (major in TransactionMajorState.entries) {
            assertEquals(
                "unerwartetes Mapping fuer $major",
                expected(major),
                TalerTransactionStateMapper.statusFromMajorState(major),
            )
        }
    }
}
