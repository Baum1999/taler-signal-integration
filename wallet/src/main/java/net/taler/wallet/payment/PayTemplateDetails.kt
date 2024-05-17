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
    /**
     * Human-readable summary for the template.
     */
    val summary: String? = null,

    /**
     * Required currency for payments to the template. The user may specify
     * any amount, but it must be in this currency. This parameter is
     * optional and should not be present if "amount" is given.
     */
    val currency: String? = null,

    /**
     * The price is imposed by the merchant and cannot be changed by the
     * customer. This parameter is optional.
     */
    val amount: Amount? = null,

    /**
     * Minimum age buyer must have (in years). Default is 0.
     */
    @SerialName("minimum_age")
    val minimumAge: Int,

    /**
     * The time the customer need to pay before his order will be deleted. It
     * is deleted if the customer did not pay and if the duration is over.
     */
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
    /**
     * Hard-coded information about the contract terms for this template.
     */
    @SerialName("template_contract")
    val templateContract: TemplateContractDetails,

    /**
     * Key-value pairs matching a subset of the fields from template_contract
     * that are user-editable defaults for this template.
     */
    @SerialName("editable_defaults")
    val editableDefaults: TemplateContractDetailsDefaults? = null,

    /**
     * Required currency for payments.  Useful if no amount is specified in
     * the template_contract but the user should be required to pay in a
     * particular currency anyway.  Merchant backends may reject requests if
     * the template_contract or editable_defaults do specify an amount in a
     * different currency.  This parameter is optional.
     */
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