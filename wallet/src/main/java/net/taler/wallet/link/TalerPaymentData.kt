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

import kotlinx.serialization.Serializable

/**
 * Datenstruktur für das JSON im taler-return-Rücksprung an den Messenger.
 *
 * Kein legacyText (mehr): den Klartext vor den URIs setzt seit 2026-10-11 der
 * Messenger selbst (z.B. Signal), weil nur er weiß, welcher Satz
 * zu ihm passt. Messenger lesen ein fehlendes Feld als "nicht da".
 *
 * - version: Versionsnummer des JSON-Formats (aktuell 1)
 * - includeSelf: Bei Gruppen-Split: ob der Sender sich selbst mitgezählt hat (null = keine Split-Info)
 * - totalAmount: Bei Gruppen-Split: der ursprüngliche Gesamtbetrag vor dem Split (null = keine Split-Info)
 * - uri: Liste der Taler-URIs (bei einem Gruppen-Split-Versand eine pro Empfaenger-Anteil,
 *   siehe PROMPT_parallel_group_split.md; bei einer regulaeren Einzelzahlung genau 1 Element)
 */
@Serializable
data class TalerPaymentData(
    val version: Int = 1,
    val includeSelf: Boolean? = null,
    val totalAmount: String? = null,
    val uri: List<String>
)
