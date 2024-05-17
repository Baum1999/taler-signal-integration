/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.wallet.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.taler.common.Amount
import net.taler.common.RelativeTime

@Serializable
data class TemplateContractDetails(
    val summary: String? = null,
    val currency: String? = null,
    val amount: Amount? = null,
    @SerialName("minimum_age")
    val minimumAge: Int,
    @SerialName("pay_duration")
    val payDuration: RelativeTime,
)

@Serializable
data class TemplateContractDetailsDefaults(
    val summary: String? = null,
    val currency: String? = null,
    /**
     * Amount *or* a plain currency string.
     */
    val amount: String? = null,
    @SerialName("minimum_age")
    val minimumAge: Int? = null,
)

fun TemplateContractDetailsDefaults?.isNullOrEmpty() =
    this == null || (summary == null
            && currency == null
            && amount == null
            && minimumAge == null)

@Serializable
class WalletTemplateDetails(
    @SerialName("template_contract")
    val templateContract: TemplateContractDetails,
    @SerialName("editable_defaults")
    val editableDefaults: TemplateContractDetailsDefaults? = null,
    @SerialName("required_currency")
    val requiredCurrency: String? = null,
)

@Serializable
data class TemplateParams(
    val amount: Amount? = null,
    val summary: String? = null,
) {
    companion object {
        fun fromTemplateDetails(details: WalletTemplateDetails) = TemplateParams(
            amount = details.templateContract.amount,
            summary = details.templateContract.summary,
        )
    }
}