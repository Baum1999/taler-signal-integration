/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.wallet.donau

import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.wallet.backend.TalerErrorInfo

@Serializable
data class DonauInfo(
    val donauBaseUrl: String,
    val taxPayerId: String,
)

@Serializable
data class GetDonauResponse(
    val currentDonauInfo: DonauInfo? = null,
)

@Serializable
sealed class GetDonauStatus {
    data object None: GetDonauStatus()
    data object Loading: GetDonauStatus()
    data class Success(val donauInfo: DonauInfo?): GetDonauStatus()
    data class Error(val error: TalerErrorInfo): GetDonauStatus()
}

@Serializable
data class DonauStatement(
    val total: Amount,
    val year: Int,
    val legalDomain: String,
    val uri: String,
    val donationStatementSig: String,
    val donauPub: String,
) {
    val host: String? by lazy {
        uri.toUri().host
    }
}

@Serializable
data class DonauSummaryItem(
    /** Base URL of the donau service. */
    val donauBaseUrl: String,

    /** Legal domain of the donau service (if available). */
    val legalDomain: String? = null,

    /** Year of the donation(s). */
    val year: Int,

    /**
     * Sum of donation receipts we received from merchants in the
     * applicable year.
     */
    val amountReceiptsAvailable: Amount,

    /**
     * Sum of donation receipts that were already submitted
     * to the donau in the applicable year.
     */
    val amountReceiptsSubmitted: Amount,

    /**
     * Amount of the latest available statement. Missing if no statement
     * was requested yet.
     */
    val amountStatement: Amount? = null,
)

@Serializable
data class GetDonauStatementsResponse(
    val statements: List<DonauStatement>,
)

@Serializable
sealed class GetDonauStatementsStatus {
    data object None: GetDonauStatementsStatus()
    data object Loading: GetDonauStatementsStatus()
    data class Success(val statements: List<DonauStatement>) : GetDonauStatementsStatus()
    data class Error(val error: TalerErrorInfo): GetDonauStatementsStatus()
}