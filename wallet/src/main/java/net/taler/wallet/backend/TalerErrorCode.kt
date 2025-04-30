/*
     This file is part of GNU Taler
     Copyright (C) 2012-2020 Taler Systems SA

     GNU Taler is free software: you can redistribute it and/or modify it
     under the terms of the GNU Lesser General Public License as published
     by the Free Software Foundation, either version 3 of the License,
     or (at your option) any later version.

     GNU Taler is distributed in the hope that it will be useful, but
     WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
     Lesser General Public License for more details.

     You should have received a copy of the GNU Lesser General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.

     SPDX-License-Identifier: LGPL3.0-or-later

     Note: the LGPL does not apply to all components of GNU Taler,
     but it does apply to this file.
 */
package net.taler.wallet.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(TalerErrorCodeSerializer::class)
enum class TalerErrorCode(val code: Int) {
    UNKNOWN(-1),

    /** Special code to indicate success (no error). */
    NONE(0),

    /** An error response did not include an error code in the format expected by the client. Most likely, the server does not speak the GNU Taler protocol. Check the URL and/or the network connection to the server. */
    INVALID(1),

    /** The response we got from the server was not in the expected format. Most likely, the server does not speak the GNU Taler protocol. Check the URL and/or the network connection to the server. */
    GENERIC_INVALID_RESPONSE(10),

    /** Exchange is badly configured and thus cannot operate. */
    EXCHANGE_GENERIC_BAD_CONFIGURATION(1000),

    /** Operation specified unknown for this endpoint. */
    EXCHANGE_GENERIC_OPERATION_UNKNOWN(1001),

    /** The number of segments included in the URI does not match the number of segments expected by the endpoint. */
    EXCHANGE_GENERIC_WRONG_NUMBER_OF_SEGMENTS(1002),

    /** The same coin was already used with a different denomination previously. */
    EXCHANGE_GENERIC_COIN_CONFLICTING_DENOMINATION_KEY(1003),

    /** The public key of given to a \"/coins/\" endpoint of the exchange was malformed. */
    EXCHANGE_GENERIC_COINS_INVALID_COIN_PUB(1004),

    /** The exchange is not aware of the denomination key the wallet requested for the operation. */
    EXCHANGE_GENERIC_DENOMINATION_KEY_UNKNOWN(1005),

    /** The signature of the denomination key over the coin is not valid. */
    EXCHANGE_DENOMINATION_SIGNATURE_INVALID(1006),

    /** The exchange failed to perform the operation as it could not find the private keys. This is a problem with the exchange setup, not with the client's request. */
    EXCHANGE_GENERIC_KEYS_MISSING(1007),

    /** Validity period of the denomination lies in the future. */
    EXCHANGE_GENERIC_DENOMINATION_VALIDITY_IN_FUTURE(1008),

    /** Denomination key of the coin is past its expiration time for the requested operation. */
    EXCHANGE_GENERIC_DENOMINATION_EXPIRED(1009),

    /** Denomination key of the coin has been revoked. */
    EXCHANGE_GENERIC_DENOMINATION_REVOKED(1010),

    /** An operation where the exchange interacted with a security module timed out. */
    EXCHANGE_GENERIC_SECMOD_TIMEOUT(1011),

    /** The respective coin did not have sufficient residual value for the operation.  The \"history\" in this response provides the \"residual_value\" of the coin, which may be less than its \"original_value\". */
    EXCHANGE_GENERIC_INSUFFICIENT_FUNDS(1012),

    /** The exchange had an internal error reconstructing the transaction history of the coin that was being processed. */
    EXCHANGE_GENERIC_COIN_HISTORY_COMPUTATION_FAILED(1013),

    /** The exchange failed to obtain the transaction history of the given coin from the database while generating an insufficient funds errors. */
    EXCHANGE_GENERIC_HISTORY_DB_ERROR_INSUFFICIENT_FUNDS(1014),

    /** The same coin was already used with a different age hash previously. */
    EXCHANGE_GENERIC_COIN_CONFLICTING_AGE_HASH(1015),

    /** The requested operation is not valid for the cipher used by the selected denomination. */
    EXCHANGE_GENERIC_INVALID_DENOMINATION_CIPHER_FOR_OPERATION(1016),

    /** The provided arguments for the operation use inconsistent ciphers. */
    EXCHANGE_GENERIC_CIPHER_MISMATCH(1017),

    /** The number of denominations specified in the request exceeds the limit of the exchange. */
    EXCHANGE_GENERIC_NEW_DENOMS_ARRAY_SIZE_EXCESSIVE(1018),

    /** The coin is not known to the exchange (yet). */
    EXCHANGE_GENERIC_COIN_UNKNOWN(1019),

    /** The time at the server is too far off from the time specified in the request. Most likely the client system time is wrong. */
    EXCHANGE_GENERIC_CLOCK_SKEW(1020),

    /** The specified amount for the coin is higher than the value of the denomination of the coin. */
    EXCHANGE_GENERIC_AMOUNT_EXCEEDS_DENOMINATION_VALUE(1021),

    /** The exchange was not properly configured with global fees. */
    EXCHANGE_GENERIC_GLOBAL_FEES_MISSING(1022),

    /** The exchange was not properly configured with wire fees. */
    EXCHANGE_GENERIC_WIRE_FEES_MISSING(1023),

    /** The purse public key was malformed. */
    EXCHANGE_GENERIC_PURSE_PUB_MALFORMED(1024),

    /** The purse is unknown. */
    EXCHANGE_GENERIC_PURSE_UNKNOWN(1025),

    /** The purse has expired. */
    EXCHANGE_GENERIC_PURSE_EXPIRED(1026),

    /** The exchange has no information about the \"reserve_pub\" that was given. */
    EXCHANGE_GENERIC_RESERVE_UNKNOWN(1027),

    /** The exchange is not allowed to proceed with the operation until the client has satisfied a KYC check. */
    EXCHANGE_GENERIC_KYC_REQUIRED(1028),

    /** Inconsistency between provided age commitment and attest: either none or both must be provided */
    EXCHANGE_PURSE_DEPOSIT_COIN_CONFLICTING_ATTEST_VS_AGE_COMMITMENT(1029),

    /** The provided attestation for the minimum age couldn't be verified by the exchange. */
    EXCHANGE_PURSE_DEPOSIT_COIN_AGE_ATTESTATION_FAILURE(1030),

    /** The purse was deleted. */
    EXCHANGE_GENERIC_PURSE_DELETED(1031),

    /** The public key of the AML officer in the URL was malformed. */
    EXCHANGE_GENERIC_AML_OFFICER_PUB_MALFORMED(1032),

    /** The signature affirming the GET request of the AML officer is invalid. */
    EXCHANGE_GENERIC_AML_OFFICER_GET_SIGNATURE_INVALID(1033),

    /** The specified AML officer does not have access at this time. */
    EXCHANGE_GENERIC_AML_OFFICER_ACCESS_DENIED(1034),

    /** The requested operation is denied pending the resolution of an anti-money laundering investigation by the exchange operator. This is a manual process, please wait and retry later. */
    EXCHANGE_GENERIC_AML_PENDING(1035),

    /** The requested operation is denied as the account was frozen on suspicion of money laundering. Please contact the exchange operator. */
    EXCHANGE_GENERIC_AML_FROZEN(1036),

    /** The exchange failed to start a KYC attribute conversion helper process. It is likely configured incorrectly. */
    EXCHANGE_GENERIC_KYC_CONVERTER_FAILED(1037),

    /** The KYC operation failed. This could be because the KYC provider rejected the KYC data provided, or because the user aborted the KYC process. */
    EXCHANGE_GENERIC_KYC_FAILED(1038),

    /** A fallback measure for a KYC operation failed. This is a bug. Users should contact the exchange operator. */
    EXCHANGE_GENERIC_KYC_FALLBACK_FAILED(1039),

    /** The specified fallback measure for a KYC operation is unknown. This is a bug. Users should contact the exchange operator. */
    EXCHANGE_GENERIC_KYC_FALLBACK_UNKNOWN(1040),

    /** The exchange is not aware of the bank account (payto URI or hash thereof) specified in the request and thus cannot perform the requested operation. The client should check that the select account is correct. */
    EXCHANGE_GENERIC_BANK_ACCOUNT_UNKNOWN(1041),

    /** The AML processing at the exchange did not terminate in an adequate timeframe. This is likely a configuration problem at the payment service provider. Users should contact the exchange operator. */
    EXCHANGE_GENERIC_AML_PROGRAM_RECURSION_DETECTED(1042),

    /** A check against sanction lists failed. This is indicative of an internal error in the sanction list processing logic. This needs to be investigated by the exchange operator. */
    EXCHANGE_GENERIC_KYC_SANCTION_LIST_CHECK_FAILED(1043),

    /** The operation timed out. Trying again might help. Check the network connection. */
    GENERIC_TIMEOUT(11),

    /** The exchange did not find information about the specified transaction in the database. */
    EXCHANGE_DEPOSITS_GET_NOT_FOUND(1100),

    /** The wire hash of given to a \"/deposits/\" handler was malformed. */
    EXCHANGE_DEPOSITS_GET_INVALID_H_WIRE(1101),

    /** The merchant key of given to a \"/deposits/\" handler was malformed. */
    EXCHANGE_DEPOSITS_GET_INVALID_MERCHANT_PUB(1102),

    /** The hash of the contract terms given to a \"/deposits/\" handler was malformed. */
    EXCHANGE_DEPOSITS_GET_INVALID_H_CONTRACT_TERMS(1103),

    /** The coin public key of given to a \"/deposits/\" handler was malformed. */
    EXCHANGE_DEPOSITS_GET_INVALID_COIN_PUB(1104),

    /** The signature returned by the exchange in a /deposits/ request was malformed. */
    EXCHANGE_DEPOSITS_GET_INVALID_SIGNATURE_BY_EXCHANGE(1105),

    /** The signature of the merchant is invalid. */
    EXCHANGE_DEPOSITS_GET_MERCHANT_SIGNATURE_INVALID(1106),

    /** The provided policy data was not accepted */
    EXCHANGE_DEPOSITS_POLICY_NOT_ACCEPTED(1107),

    /** The given reserve does not have sufficient funds to admit the requested withdraw operation at this time.  The response includes the current \"balance\" of the reserve as well as the transaction \"history\" that lead to this balance. */
    EXCHANGE_WITHDRAW_INSUFFICIENT_FUNDS(1150),

    /** The given reserve does not have sufficient funds to admit the requested age-withdraw operation at this time.  The response includes the current \"balance\" of the reserve as well as the transaction \"history\" that lead to this balance. */
    EXCHANGE_AGE_WITHDRAW_INSUFFICIENT_FUNDS(1151),

    /** The amount to withdraw together with the fee exceeds the numeric range for Taler amounts.  This is not a client failure, as the coin value and fees come from the exchange's configuration. */
    EXCHANGE_WITHDRAW_AMOUNT_FEE_OVERFLOW(1152),

    /** The exchange failed to create the signature using the denomination key. */
    EXCHANGE_WITHDRAW_SIGNATURE_FAILED(1153),

    /** The signature of the reserve is not valid. */
    EXCHANGE_WITHDRAW_RESERVE_SIGNATURE_INVALID(1154),

    /** When computing the reserve history, we ended up with a negative overall balance, which should be impossible. */
    EXCHANGE_RESERVE_HISTORY_ERROR_INSUFFICIENT_FUNDS(1155),

    /** The reserve did not have sufficient funds in it to pay for a full reserve history statement. */
    EXCHANGE_GET_RESERVE_HISTORY_ERROR_INSUFFICIENT_BALANCE(1156),

    /** Withdraw period of the coin to be withdrawn is in the past. */
    EXCHANGE_WITHDRAW_DENOMINATION_KEY_LOST(1158),

    /** The client failed to unblind the blind signature. */
    EXCHANGE_WITHDRAW_UNBLIND_FAILURE(1159),

    /** The client reused a withdraw nonce, which is not allowed. */
    EXCHANGE_WITHDRAW_NONCE_REUSE(1160),

    /** The client provided an unknown commitment for an age-withdraw request. */
    EXCHANGE_WITHDRAW_COMMITMENT_UNKNOWN(1161),

    /** The total sum of amounts from the denominations did overflow. */
    EXCHANGE_WITHDRAW_AMOUNT_OVERFLOW(1162),

    /** The total sum of value and fees from the denominations differs from the committed amount with fees. */
    EXCHANGE_AGE_WITHDRAW_AMOUNT_INCORRECT(1163),

    /** The original commitment differs from the calculated hash */
    EXCHANGE_WITHDRAW_REVEAL_INVALID_HASH(1164),

    /** The maximum age in the commitment is too large for the reserve */
    EXCHANGE_WITHDRAW_MAXIMUM_AGE_TOO_LARGE(1165),

    /** The batch withdraw included a planchet that was already withdrawn. This is not allowed. */
    EXCHANGE_WITHDRAW_IDEMPOTENT_PLANCHET(1175),

    /** The protocol version given by the server does not follow the required format. Most likely, the server does not speak the GNU Taler protocol. Check the URL and/or the network connection to the server. */
    GENERIC_VERSION_MALFORMED(12),

    /** The signature made by the coin over the deposit permission is not valid. */
    EXCHANGE_DEPOSIT_COIN_SIGNATURE_INVALID(1205),

    /** The same coin was already deposited for the same merchant and contract with other details. */
    EXCHANGE_DEPOSIT_CONFLICTING_CONTRACT(1206),

    /** The stated value of the coin after the deposit fee is subtracted would be negative. */
    EXCHANGE_DEPOSIT_NEGATIVE_VALUE_AFTER_FEE(1207),

    /** The stated refund deadline is after the wire deadline. */
    EXCHANGE_DEPOSIT_REFUND_DEADLINE_AFTER_WIRE_DEADLINE(1208),

    /** The stated wire deadline is \"never\", which makes no sense. */
    EXCHANGE_DEPOSIT_WIRE_DEADLINE_IS_NEVER(1209),

    /** The exchange failed to canonicalize and hash the given wire format. For example, the merchant failed to provide the \"salt\" or a valid payto:// URI in the wire details.  Note that while the exchange will do some basic sanity checking on the wire details, it cannot warrant that the banking system will ultimately be able to route to the specified address, even if this check passed. */
    EXCHANGE_DEPOSIT_INVALID_WIRE_FORMAT_JSON(1210),

    /** The hash of the given wire address does not match the wire hash specified in the proposal data. */
    EXCHANGE_DEPOSIT_INVALID_WIRE_FORMAT_CONTRACT_HASH_CONFLICT(1211),

    /** The signature provided by the exchange is not valid. */
    EXCHANGE_DEPOSIT_INVALID_SIGNATURE_BY_EXCHANGE(1221),

    /** The deposited amount is smaller than the deposit fee, which would result in a negative contribution. */
    EXCHANGE_DEPOSIT_FEE_ABOVE_AMOUNT(1222),

    /** The proof of policy fulfillment was invalid. */
    EXCHANGE_EXTENSIONS_INVALID_FULFILLMENT(1240),

    /** The coin history was requested with a bad signature. */
    EXCHANGE_COIN_HISTORY_BAD_SIGNATURE(1251),

    /** The reserve history was requested with a bad signature. */
    EXCHANGE_RESERVE_HISTORY_BAD_SIGNATURE(1252),

    /** The service responded with a reply that was in the right data format, but the content did not satisfy the protocol. Please file a bug report. */
    GENERIC_REPLY_MALFORMED(13),

    /** The exchange encountered melt fees exceeding the melted coin's contribution. */
    EXCHANGE_MELT_FEES_EXCEED_CONTRIBUTION(1302),

    /** The signature made with the coin to be melted is invalid. */
    EXCHANGE_MELT_COIN_SIGNATURE_INVALID(1303),

    /** The denomination of the given coin has past its expiration date and it is also not a valid zombie (that is, was not refreshed with the fresh coin being subjected to recoup). */
    EXCHANGE_MELT_COIN_EXPIRED_NO_ZOMBIE(1305),

    /** The signature returned by the exchange in a melt request was malformed. */
    EXCHANGE_MELT_INVALID_SIGNATURE_BY_EXCHANGE(1306),

    /** The provided transfer keys do not match up with the original commitment.  Information about the original commitment is included in the response. */
    EXCHANGE_REFRESHES_REVEAL_COMMITMENT_VIOLATION(1353),

    /** Failed to produce the blinded signatures over the coins to be returned. */
    EXCHANGE_REFRESHES_REVEAL_SIGNING_ERROR(1354),

    /** The exchange is unaware of the refresh session specified in the request. */
    EXCHANGE_REFRESHES_REVEAL_SESSION_UNKNOWN(1355),

    /** The size of the cut-and-choose dimension of the private transfer keys request does not match #TALER_CNC_KAPPA - 1. */
    EXCHANGE_REFRESHES_REVEAL_CNC_TRANSFER_ARRAY_SIZE_INVALID(1356),

    /** The number of envelopes given does not match the number of denomination keys given. */
    EXCHANGE_REFRESHES_REVEAL_NEW_DENOMS_ARRAY_SIZE_MISMATCH(1358),

    /** The exchange encountered a numeric overflow totaling up the cost for the refresh operation. */
    EXCHANGE_REFRESHES_REVEAL_COST_CALCULATION_OVERFLOW(1359),

    /** The exchange's cost calculation shows that the melt amount is below the costs of the transaction. */
    EXCHANGE_REFRESHES_REVEAL_AMOUNT_INSUFFICIENT(1360),

    /** The signature made with the coin over the link data is invalid. */
    EXCHANGE_REFRESHES_REVEAL_LINK_SIGNATURE_INVALID(1361),

    /** The refresh session hash given to a /refreshes/ handler was malformed. */
    EXCHANGE_REFRESHES_REVEAL_INVALID_RCH(1362),

    /** Operation specified invalid for this endpoint. */
    EXCHANGE_REFRESHES_REVEAL_OPERATION_INVALID(1363),

    /** The client provided age commitment data, but age restriction is not supported on this server. */
    EXCHANGE_REFRESHES_REVEAL_AGE_RESTRICTION_NOT_SUPPORTED(1364),

    /** The client provided invalid age commitment data: missing, not an array, or  array of invalid size. */
    EXCHANGE_REFRESHES_REVEAL_AGE_RESTRICTION_COMMITMENT_INVALID(1365),

    /** There is an error in the client-side configuration, for example an option is set to an invalid value. Check the logs and fix the local configuration. */
    GENERIC_CONFIGURATION_INVALID(14),

    /** The coin specified in the link request is unknown to the exchange. */
    EXCHANGE_LINK_COIN_UNKNOWN(1400),

    /** The public key of given to a /transfers/ handler was malformed. */
    EXCHANGE_TRANSFERS_GET_WTID_MALFORMED(1450),

    /** The exchange did not find information about the specified wire transfer identifier in the database. */
    EXCHANGE_TRANSFERS_GET_WTID_NOT_FOUND(1451),

    /** The exchange did not find information about the wire transfer fees it charged. */
    EXCHANGE_TRANSFERS_GET_WIRE_FEE_NOT_FOUND(1452),

    /** The exchange found a wire fee that was above the total transfer value (and thus could not have been charged). */
    EXCHANGE_TRANSFERS_GET_WIRE_FEE_INCONSISTENT(1453),

    /** The wait target of the URL was not in the set of expected values. */
    EXCHANGE_PURSES_INVALID_WAIT_TARGET(1475),

    /** The signature on the purse status returned by the exchange was invalid. */
    EXCHANGE_PURSES_GET_INVALID_SIGNATURE_BY_EXCHANGE(1476),

    /** The client made a request to a service, but received an error response it does not know how to handle. Please file a bug report. */
    GENERIC_UNEXPECTED_REQUEST_ERROR(15),

    /** The exchange knows literally nothing about the coin we were asked to refund. But without a transaction history, we cannot issue a refund. This is kind-of OK, the owner should just refresh it directly without executing the refund. */
    EXCHANGE_REFUND_COIN_NOT_FOUND(1500),

    /** We could not process the refund request as the coin's transaction history does not permit the requested refund because then refunds would exceed the deposit amount.  The \"history\" in the response proves this. */
    EXCHANGE_REFUND_CONFLICT_DEPOSIT_INSUFFICIENT(1501),

    /** The exchange knows about the coin we were asked to refund, but not about the specific /deposit operation.  Hence, we cannot issue a refund (as we do not know if this merchant public key is authorized to do a refund). */
    EXCHANGE_REFUND_DEPOSIT_NOT_FOUND(1502),

    /** The exchange can no longer refund the customer/coin as the money was already transferred (paid out) to the merchant. (It should be past the refund deadline.) */
    EXCHANGE_REFUND_MERCHANT_ALREADY_PAID(1503),

    /** The refund fee specified for the request is lower than the refund fee charged by the exchange for the given denomination key of the refunded coin. */
    EXCHANGE_REFUND_FEE_TOO_LOW(1504),

    /** The refunded amount is smaller than the refund fee, which would result in a negative refund. */
    EXCHANGE_REFUND_FEE_ABOVE_AMOUNT(1505),

    /** The signature of the merchant is invalid. */
    EXCHANGE_REFUND_MERCHANT_SIGNATURE_INVALID(1506),

    /** Merchant backend failed to create the refund confirmation signature. */
    EXCHANGE_REFUND_MERCHANT_SIGNING_FAILED(1507),

    /** The signature returned by the exchange in a refund request was malformed. */
    EXCHANGE_REFUND_INVALID_SIGNATURE_BY_EXCHANGE(1508),

    /** The failure proof returned by the exchange is incorrect. */
    EXCHANGE_REFUND_INVALID_FAILURE_PROOF_BY_EXCHANGE(1509),

    /** Conflicting refund granted before with different amount but same refund transaction ID. */
    EXCHANGE_REFUND_INCONSISTENT_AMOUNT(1510),

    /** The given coin signature is invalid for the request. */
    EXCHANGE_RECOUP_SIGNATURE_INVALID(1550),

    /** The exchange could not find the corresponding withdraw operation. The request is denied. */
    EXCHANGE_RECOUP_WITHDRAW_NOT_FOUND(1551),

    /** The coin's remaining balance is zero.  The request is denied. */
    EXCHANGE_RECOUP_COIN_BALANCE_ZERO(1552),

    /** The exchange failed to reproduce the coin's blinding. */
    EXCHANGE_RECOUP_BLINDING_FAILED(1553),

    /** The coin's remaining balance is zero.  The request is denied. */
    EXCHANGE_RECOUP_COIN_BALANCE_NEGATIVE(1554),

    /** The coin's denomination has not been revoked yet. */
    EXCHANGE_RECOUP_NOT_ELIGIBLE(1555),

    /** The given coin signature is invalid for the request. */
    EXCHANGE_RECOUP_REFRESH_SIGNATURE_INVALID(1575),

    /** The exchange could not find the corresponding melt operation. The request is denied. */
    EXCHANGE_RECOUP_REFRESH_MELT_NOT_FOUND(1576),

    /** The exchange failed to reproduce the coin's blinding. */
    EXCHANGE_RECOUP_REFRESH_BLINDING_FAILED(1578),

    /** The coin's denomination has not been revoked yet. */
    EXCHANGE_RECOUP_REFRESH_NOT_ELIGIBLE(1580),

    /** The token used by the client to authorize the request does not grant the required permissions for the request. Check the requirements and obtain a suitable authorization token to proceed. */
    GENERIC_TOKEN_PERMISSION_INSUFFICIENT(16),

    /** This exchange does not allow clients to request /keys for times other than the current (exchange) time. */
    EXCHANGE_KEYS_TIMETRAVEL_FORBIDDEN(1600),

    /** A signature in the server's response was malformed. */
    EXCHANGE_WIRE_SIGNATURE_INVALID(1650),

    /** No bank accounts are enabled for the exchange. The administrator should enable-account using the taler-exchange-offline tool. */
    EXCHANGE_WIRE_NO_ACCOUNTS_CONFIGURED(1651),

    /** The payto:// URI stored in the exchange database for its bank account is malformed. */
    EXCHANGE_WIRE_INVALID_PAYTO_CONFIGURED(1652),

    /** No wire fees are configured for an enabled wire method of the exchange. The administrator must set the wire-fee using the taler-exchange-offline tool. */
    EXCHANGE_WIRE_FEES_NOT_CONFIGURED(1653),

    /** This purse was previously created with different meta data. */
    EXCHANGE_RESERVES_PURSE_CREATE_CONFLICTING_META_DATA(1675),

    /** This purse was previously merged with different meta data. */
    EXCHANGE_RESERVES_PURSE_MERGE_CONFLICTING_META_DATA(1676),

    /** The reserve has insufficient funds to create another purse. */
    EXCHANGE_RESERVES_PURSE_CREATE_INSUFFICIENT_FUNDS(1677),

    /** The purse fee specified for the request is lower than the purse fee charged by the exchange at this time. */
    EXCHANGE_RESERVES_PURSE_FEE_TOO_LOW(1678),

    /** The payment request cannot be deleted anymore, as it either already completed or timed out. */
    EXCHANGE_PURSE_DELETE_ALREADY_DECIDED(1679),

    /** The signature affirming the purse deletion is invalid. */
    EXCHANGE_PURSE_DELETE_SIGNATURE_INVALID(1680),

    /** Withdrawal from the reserve requires age restriction to be set. */
    EXCHANGE_RESERVES_AGE_RESTRICTION_REQUIRED(1681),

    /** The exchange failed to talk to the process responsible for its private denomination keys or the helpers had no denominations (properly) configured. */
    EXCHANGE_DENOMINATION_HELPER_UNAVAILABLE(1700),

    /** The response from the denomination key helper process was malformed. */
    EXCHANGE_DENOMINATION_HELPER_BUG(1701),

    /** The helper refuses to sign with the key, because it is too early: the validity period has not yet started. */
    EXCHANGE_DENOMINATION_HELPER_TOO_EARLY(1702),

    /** The signature of the exchange on the reply was invalid. */
    EXCHANGE_PURSE_DEPOSIT_EXCHANGE_SIGNATURE_INVALID(1725),

    /** The exchange failed to talk to the process responsible for its private signing keys. */
    EXCHANGE_SIGNKEY_HELPER_UNAVAILABLE(1750),

    /** The response from the online signing key helper process was malformed. */
    EXCHANGE_SIGNKEY_HELPER_BUG(1751),

    /** The helper refuses to sign with the key, because it is too early: the validity period has not yet started. */
    EXCHANGE_SIGNKEY_HELPER_TOO_EARLY(1752),

    /** The purse expiration time is in the past at the time of its creation. */
    EXCHANGE_RESERVES_PURSE_EXPIRATION_BEFORE_NOW(1775),

    /** The purse expiration time is set to never, which is not allowed. */
    EXCHANGE_RESERVES_PURSE_EXPIRATION_IS_NEVER(1776),

    /** The signature affirming the merge of the purse is invalid. */
    EXCHANGE_RESERVES_PURSE_MERGE_SIGNATURE_INVALID(1777),

    /** The signature by the reserve affirming the merge is invalid. */
    EXCHANGE_RESERVES_RESERVE_MERGE_SIGNATURE_INVALID(1778),

    /** The signature by the reserve affirming the open operation is invalid. */
    EXCHANGE_RESERVES_OPEN_BAD_SIGNATURE(1785),

    /** The signature by the reserve affirming the close operation is invalid. */
    EXCHANGE_RESERVES_CLOSE_BAD_SIGNATURE(1786),

    /** The signature by the reserve affirming the attestion request is invalid. */
    EXCHANGE_RESERVES_ATTEST_BAD_SIGNATURE(1787),

    /** The exchange does not know an origin account to which the remaining reserve balance could be wired to, and the wallet failed to provide one. */
    EXCHANGE_RESERVES_CLOSE_NO_TARGET_ACCOUNT(1788),

    /** The reserve balance is insufficient to pay for the open operation. */
    EXCHANGE_RESERVES_OPEN_INSUFFICIENT_FUNDS(1789),

    /** The auditor that was supposed to be disabled is unknown to this exchange. */
    EXCHANGE_MANAGEMENT_AUDITOR_NOT_FOUND(1800),

    /** The exchange has a more recently signed conflicting instruction and is thus refusing the current change (replay detected). */
    EXCHANGE_MANAGEMENT_AUDITOR_MORE_RECENT_PRESENT(1801),

    /** The signature to add or enable the auditor does not validate. */
    EXCHANGE_MANAGEMENT_AUDITOR_ADD_SIGNATURE_INVALID(1802),

    /** The signature to disable the auditor does not validate. */
    EXCHANGE_MANAGEMENT_AUDITOR_DEL_SIGNATURE_INVALID(1803),

    /** The signature to revoke the denomination does not validate. */
    EXCHANGE_MANAGEMENT_DENOMINATION_REVOKE_SIGNATURE_INVALID(1804),

    /** The signature to revoke the online signing key does not validate. */
    EXCHANGE_MANAGEMENT_SIGNKEY_REVOKE_SIGNATURE_INVALID(1805),

    /** The exchange has a more recently signed conflicting instruction and is thus refusing the current change (replay detected). */
    EXCHANGE_MANAGEMENT_WIRE_MORE_RECENT_PRESENT(1806),

    /** The signingkey specified is unknown to the exchange. */
    EXCHANGE_MANAGEMENT_KEYS_SIGNKEY_UNKNOWN(1807),

    /** The signature to publish wire account does not validate. */
    EXCHANGE_MANAGEMENT_WIRE_DETAILS_SIGNATURE_INVALID(1808),

    /** The signature to add the wire account does not validate. */
    EXCHANGE_MANAGEMENT_WIRE_ADD_SIGNATURE_INVALID(1809),

    /** The signature to disable the wire account does not validate. */
    EXCHANGE_MANAGEMENT_WIRE_DEL_SIGNATURE_INVALID(1810),

    /** The wire account to be disabled is unknown to the exchange. */
    EXCHANGE_MANAGEMENT_WIRE_NOT_FOUND(1811),

    /** The signature to affirm wire fees does not validate. */
    EXCHANGE_MANAGEMENT_WIRE_FEE_SIGNATURE_INVALID(1812),

    /** The signature conflicts with a previous signature affirming different fees. */
    EXCHANGE_MANAGEMENT_WIRE_FEE_MISMATCH(1813),

    /** The signature affirming the denomination key is invalid. */
    EXCHANGE_MANAGEMENT_KEYS_DENOMKEY_ADD_SIGNATURE_INVALID(1814),

    /** The signature affirming the signing key is invalid. */
    EXCHANGE_MANAGEMENT_KEYS_SIGNKEY_ADD_SIGNATURE_INVALID(1815),

    /** The signature conflicts with a previous signature affirming different fees. */
    EXCHANGE_MANAGEMENT_GLOBAL_FEE_MISMATCH(1816),

    /** The signature affirming the fee structure is invalid. */
    EXCHANGE_MANAGEMENT_GLOBAL_FEE_SIGNATURE_INVALID(1817),

    /** The signature affirming the profit drain is invalid. */
    EXCHANGE_MANAGEMENT_DRAIN_PROFITS_SIGNATURE_INVALID(1818),

    /** The signature affirming the AML decision is invalid. */
    EXCHANGE_AML_DECISION_ADD_SIGNATURE_INVALID(1825),

    /** The AML officer specified is not allowed to make AML decisions right now. */
    EXCHANGE_AML_DECISION_INVALID_OFFICER(1826),

    /** There is a more recent AML decision on file. The decision was rejected as timestamps of AML decisions must be monotonically increasing. */
    EXCHANGE_AML_DECISION_MORE_RECENT_PRESENT(1827),

    /** There AML decision would impose an AML check of a type that is not provided by any KYC provider known to the exchange. */
    EXCHANGE_AML_DECISION_UNKNOWN_CHECK(1828),

    /** The signature affirming the change in the AML officer status is invalid. */
    EXCHANGE_MANAGEMENT_UPDATE_AML_OFFICER_SIGNATURE_INVALID(1830),

    /** A more recent decision about the AML officer status is known to the exchange. */
    EXCHANGE_MANAGEMENT_AML_OFFICERS_MORE_RECENT_PRESENT(1831),

    /** The exchange already has this denomination key configured, but with different meta data. This should not be possible, contact the developers for support. */
    EXCHANGE_MANAGEMENT_CONFLICTING_DENOMINATION_META_DATA(1832),

    /** The exchange already has this signing key configured, but with different meta data. This should not be possible, contact the developers for support. */
    EXCHANGE_MANAGEMENT_CONFLICTING_SIGNKEY_META_DATA(1833),

    /** The purse was previously created with different meta data. */
    EXCHANGE_PURSE_CREATE_CONFLICTING_META_DATA(1850),

    /** The purse was previously created with a different contract. */
    EXCHANGE_PURSE_CREATE_CONFLICTING_CONTRACT_STORED(1851),

    /** A coin signature for a deposit into the purse is invalid. */
    EXCHANGE_PURSE_CREATE_COIN_SIGNATURE_INVALID(1852),

    /** The purse expiration time is in the past. */
    EXCHANGE_PURSE_CREATE_EXPIRATION_BEFORE_NOW(1853),

    /** The purse expiration time is \"never\". */
    EXCHANGE_PURSE_CREATE_EXPIRATION_IS_NEVER(1854),

    /** The purse signature over the purse meta data is invalid. */
    EXCHANGE_PURSE_CREATE_SIGNATURE_INVALID(1855),

    /** The signature over the encrypted contract is invalid. */
    EXCHANGE_PURSE_ECONTRACT_SIGNATURE_INVALID(1856),

    /** The signature from the exchange over the confirmation is invalid. */
    EXCHANGE_PURSE_CREATE_EXCHANGE_SIGNATURE_INVALID(1857),

    /** The coin was previously deposited with different meta data. */
    EXCHANGE_PURSE_DEPOSIT_CONFLICTING_META_DATA(1858),

    /** The encrypted contract was previously uploaded with different meta data. */
    EXCHANGE_PURSE_ECONTRACT_CONFLICTING_META_DATA(1859),

    /** The deposited amount is less than the purse fee. */
    EXCHANGE_CREATE_PURSE_NEGATIVE_VALUE_AFTER_FEE(1860),

    /** The signature using the merge key is invalid. */
    EXCHANGE_PURSE_MERGE_INVALID_MERGE_SIGNATURE(1876),

    /** The signature using the reserve key is invalid. */
    EXCHANGE_PURSE_MERGE_INVALID_RESERVE_SIGNATURE(1877),

    /** The targeted purse is not yet full and thus cannot be merged. Retrying the request later may succeed. */
    EXCHANGE_PURSE_NOT_FULL(1878),

    /** The signature from the exchange over the confirmation is invalid. */
    EXCHANGE_PURSE_MERGE_EXCHANGE_SIGNATURE_INVALID(1879),

    /** The exchange of the target account is not a partner of this exchange. */
    EXCHANGE_MERGE_PURSE_PARTNER_UNKNOWN(1880),

    /** The signature affirming the new partner is invalid. */
    EXCHANGE_MANAGEMENT_ADD_PARTNER_SIGNATURE_INVALID(1890),

    /** Conflicting data for the partner already exists with the exchange. */
    EXCHANGE_MANAGEMENT_ADD_PARTNER_DATA_CONFLICT(1891),

    /** The auditor signature over the denomination meta data is invalid. */
    EXCHANGE_AUDITORS_AUDITOR_SIGNATURE_INVALID(1900),

    /** The auditor that was specified is unknown to this exchange. */
    EXCHANGE_AUDITORS_AUDITOR_UNKNOWN(1901),

    /** The auditor that was specified is no longer used by this exchange. */
    EXCHANGE_AUDITORS_AUDITOR_INACTIVE(1902),

    /** The exchange tried to run an AML program, but that program did not terminate on time. Contact the exchange operator to address the AML program bug or performance issue. If it is not a performance issue, the timeout might have to be increased (requires changes to the source code). */
    EXCHANGE_KYC_GENERIC_AML_PROGRAM_TIMEOUT(1918),

    /** The KYC info access token is not recognized. Hence the request was denied. */
    EXCHANGE_KYC_INFO_AUTHORIZATION_FAILED(1919),

    /** The exchange got stuck in a long series of (likely recursive) KYC rules without user-inputs that did not result in a timely conclusion. This is a configuration failure. Please contact the administrator. */
    EXCHANGE_KYC_RECURSIVE_RULE_DETECTED(1920),

    /** The submitted KYC data lacks an attribute that is required by the KYC form. Please submit the complete form. */
    EXCHANGE_KYC_AML_FORM_INCOMPLETE(1921),

    /** The request requires an AML program which is no longer configured at the exchange. Contact the exchange operator to address the configuration issue. */
    EXCHANGE_KYC_GENERIC_AML_PROGRAM_GONE(1922),

    /** The given check is not of type 'form' and thus using this handler for form submission is incorrect. */
    EXCHANGE_KYC_NOT_A_FORM(1923),

    /** The request requires a check which is no longer configured at the exchange. Contact the exchange operator to address the configuration issue. */
    EXCHANGE_KYC_GENERIC_CHECK_GONE(1924),

    /** The signature affirming the wallet's KYC request was invalid. */
    EXCHANGE_KYC_WALLET_SIGNATURE_INVALID(1925),

    /** The exchange received an unexpected malformed response from its KYC backend. */
    EXCHANGE_KYC_PROOF_BACKEND_INVALID_RESPONSE(1926),

    /** The backend signaled an unexpected failure. */
    EXCHANGE_KYC_PROOF_BACKEND_ERROR(1927),

    /** The backend signaled an authorization failure. */
    EXCHANGE_KYC_PROOF_BACKEND_AUTHORIZATION_FAILED(1928),

    /** The exchange is unaware of having made an the authorization request. */
    EXCHANGE_KYC_PROOF_REQUEST_UNKNOWN(1929),

    /** The KYC authorization signature was invalid. Hence the request was denied. */
    EXCHANGE_KYC_CHECK_AUTHORIZATION_FAILED(1930),

    /** The request used a logic specifier that is not known to the exchange. */
    EXCHANGE_KYC_GENERIC_LOGIC_UNKNOWN(1931),

    /** The request requires a logic which is no longer configured at the exchange. */
    EXCHANGE_KYC_GENERIC_LOGIC_GONE(1932),

    /** The logic plugin had a bug in its interaction with the KYC provider. */
    EXCHANGE_KYC_GENERIC_LOGIC_BUG(1933),

    /** The exchange could not process the request with its KYC provider because the provider refused access to the service. This indicates some configuration issue at the Taler exchange operator. */
    EXCHANGE_KYC_GENERIC_PROVIDER_ACCESS_REFUSED(1934),

    /** There was a timeout in the interaction between the exchange and the KYC provider. The most likely cause is some networking problem. Trying again later might succeed. */
    EXCHANGE_KYC_GENERIC_PROVIDER_TIMEOUT(1935),

    /** The KYC provider responded with a status that was completely unexpected by the KYC logic of the exchange. */
    EXCHANGE_KYC_GENERIC_PROVIDER_UNEXPECTED_REPLY(1936),

    /** The rate limit of the exchange at the KYC provider has been exceeded. Trying much later might work. */
    EXCHANGE_KYC_GENERIC_PROVIDER_RATE_LIMIT_EXCEEDED(1937),

    /** The request to the webhook lacked proper authorization or authentication data. */
    EXCHANGE_KYC_WEBHOOK_UNAUTHORIZED(1938),

    /** The exchange is unaware of the requested payto URI with respect to the KYC status. */
    EXCHANGE_KYC_CHECK_REQUEST_UNKNOWN(1939),

    /** The exchange has no account public key to check the KYC authorization signature against. Hence the request was denied. The user should do a wire transfer to the exchange with the KYC authorization key in the subject. */
    EXCHANGE_KYC_CHECK_AUTHORIZATION_KEY_UNKNOWN(1940),

    /** The form has been previously uploaded, and may only be filed once. The user should be redirected to their main KYC page and see if any other steps need to be taken. */
    EXCHANGE_KYC_FORM_ALREADY_UPLOADED(1941),

    /** The internal state of the exchange specifying KYC measures is malformed. Please contact technical support. */
    EXCHANGE_KYC_MEASURES_MALFORMED(1942),

    /** The specified index does not refer to a valid KYC measure. Please check the URL. */
    EXCHANGE_KYC_MEASURE_INDEX_INVALID(1943),

    /** The operation is not supported by the selected KYC logic. This is either caused by a configuration change or some invalid use of the API. Please contact technical support. */
    EXCHANGE_KYC_INVALID_LOGIC_TO_CHECK(1944),

    /** The AML program failed. This is either caused by a configuration change or a bug. Please contact technical support. */
    EXCHANGE_KYC_AML_PROGRAM_FAILURE(1945),

    /** The AML program returned a malformed result. This is a bug. Please contact technical support. */
    EXCHANGE_KYC_AML_PROGRAM_MALFORMED_RESULT(1946),

    /** The response from the KYC provider lacked required attributes. Please contact technical support. */
    EXCHANGE_KYC_GENERIC_PROVIDER_INCOMPLETE_REPLY(1947),

    /** The context of the KYC check lacked required fields. This is a bug. Please contact technical support. */
    EXCHANGE_KYC_GENERIC_PROVIDER_INCOMPLETE_CONTEXT(1948),

    /** The logic plugin had a bug in its AML processing. This is a bug. Please contact technical support. */
    EXCHANGE_KYC_GENERIC_AML_LOGIC_BUG(1949),

    /** The exchange does not know a contract under the given contract public key. */
    EXCHANGE_CONTRACTS_UNKNOWN(1950),

    /** The URL does not encode a valid exchange public key in its path. */
    EXCHANGE_CONTRACTS_INVALID_CONTRACT_PUB(1951),

    /** The returned encrypted contract did not decrypt. */
    EXCHANGE_CONTRACTS_DECRYPTION_FAILED(1952),

    /** The signature on the encrypted contract did not validate. */
    EXCHANGE_CONTRACTS_SIGNATURE_INVALID(1953),

    /** The decrypted contract was malformed. */
    EXCHANGE_CONTRACTS_DECODING_FAILED(1954),

    /** A coin signature for a deposit into the purse is invalid. */
    EXCHANGE_PURSE_DEPOSIT_COIN_SIGNATURE_INVALID(1975),

    /** It is too late to deposit coins into the purse. */
    EXCHANGE_PURSE_DEPOSIT_DECIDED_ALREADY(1976),

    /** The exchange is currently processing the KYC status and is not able to return a response yet. */
    EXCHANGE_KYC_INFO_BUSY(1977),

    /** TOTP key is not valid. */
    EXCHANGE_TOTP_KEY_INVALID(1980),

    /** An internal failure happened on the client side. Details should be in the local logs. Check if you are using the latest available version or file a report with the developers. */
    GENERIC_CLIENT_INTERNAL_ERROR(2),

    /** The HTTP method used is invalid for this endpoint. This is likely a bug in the client implementation. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_METHOD_INVALID(20),

    /** The backend could not find the merchant instance specified in the request. */
    MERCHANT_GENERIC_INSTANCE_UNKNOWN(2000),

    /** The start and end-times in the wire fee structure leave a hole. This is not allowed. */
    MERCHANT_GENERIC_HOLE_IN_WIRE_FEE_STRUCTURE(2001),

    /** The merchant was unable to obtain a valid answer to /wire from the exchange. */
    MERCHANT_GENERIC_EXCHANGE_WIRE_REQUEST_FAILED(2002),

    /** The product category is not known to the backend. */
    MERCHANT_GENERIC_CATEGORY_UNKNOWN(2003),

    /** The proposal is not known to the backend. */
    MERCHANT_GENERIC_ORDER_UNKNOWN(2005),

    /** The order provided to the backend could not be completed, because a product to be completed via inventory data is not actually in our inventory. */
    MERCHANT_GENERIC_PRODUCT_UNKNOWN(2006),

    /** The reward ID is unknown.  This could happen if the reward has expired. */
    MERCHANT_GENERIC_REWARD_ID_UNKNOWN(2007),

    /** The contract obtained from the merchant backend was malformed. */
    MERCHANT_GENERIC_DB_CONTRACT_CONTENT_INVALID(2008),

    /** The order we found does not match the provided contract hash. */
    MERCHANT_GENERIC_CONTRACT_HASH_DOES_NOT_MATCH_ORDER(2009),

    /** The exchange failed to provide a valid response to the merchant's /keys request. */
    MERCHANT_GENERIC_EXCHANGE_KEYS_FAILURE(2010),

    /** The exchange failed to respond to the merchant on time. */
    MERCHANT_GENERIC_EXCHANGE_TIMEOUT(2011),

    /** The merchant failed to talk to the exchange. */
    MERCHANT_GENERIC_EXCHANGE_CONNECT_FAILURE(2012),

    /** The exchange returned a maformed response. */
    MERCHANT_GENERIC_EXCHANGE_REPLY_MALFORMED(2013),

    /** The exchange returned an unexpected response status. */
    MERCHANT_GENERIC_EXCHANGE_UNEXPECTED_STATUS(2014),

    /** The merchant refused the request due to lack of authorization. */
    MERCHANT_GENERIC_UNAUTHORIZED(2015),

    /** The merchant instance specified in the request was deleted. */
    MERCHANT_GENERIC_INSTANCE_DELETED(2016),

    /** The backend could not find the inbound wire transfer specified in the request. */
    MERCHANT_GENERIC_TRANSFER_UNKNOWN(2017),

    /** The backend could not find the template(id) because it is not exist. */
    MERCHANT_GENERIC_TEMPLATE_UNKNOWN(2018),

    /** The backend could not find the webhook(id) because it is not exist. */
    MERCHANT_GENERIC_WEBHOOK_UNKNOWN(2019),

    /** The backend could not find the webhook(serial) because it is not exist. */
    MERCHANT_GENERIC_PENDING_WEBHOOK_UNKNOWN(2020),

    /** The backend could not find the OTP device(id) because it is not exist. */
    MERCHANT_GENERIC_OTP_DEVICE_UNKNOWN(2021),

    /** The account is not known to the backend. */
    MERCHANT_GENERIC_ACCOUNT_UNKNOWN(2022),

    /** The wire hash was malformed. */
    MERCHANT_GENERIC_H_WIRE_MALFORMED(2023),

    /** The currency specified in the operation does not work with the current state of the given resource. */
    MERCHANT_GENERIC_CURRENCY_MISMATCH(2024),

    /** The exchange specified in the operation is not trusted by this exchange. The client should limit its operation to exchanges enabled by the merchant, or ask the merchant to enable additional exchanges in the configuration. */
    MERCHANT_GENERIC_EXCHANGE_UNTRUSTED(2025),

    /** The token family is not known to the backend. */
    MERCHANT_GENERIC_TOKEN_FAMILY_UNKNOWN(2026),

    /** The token family key is not known to the backend. Check the local system time on the client, maybe an expired (or not yet valid) token was used. */
    MERCHANT_GENERIC_TOKEN_KEY_UNKNOWN(2027),

    /** The merchant backend is not configured to support the DONAU protocol. */
    MERCHANT_GENERIC_DONAU_NOT_CONFIGURED(2028),

    /** There is no endpoint defined for the URL provided by the client. Check if you used the correct URL and/or file a report with the developers of the client software. */
    GENERIC_ENDPOINT_UNKNOWN(21),

    /** The exchange failed to provide a valid answer to the tracking request, thus those details are not in the response. */
    MERCHANT_GET_ORDERS_EXCHANGE_TRACKING_FAILURE(2100),

    /** The merchant backend failed to construct the request for tracking to the exchange, thus tracking details are not in the response. */
    MERCHANT_GET_ORDERS_ID_EXCHANGE_REQUEST_FAILURE(2103),

    /** The merchant backend failed trying to contact the exchange for tracking details, thus those details are not in the response. */
    MERCHANT_GET_ORDERS_ID_EXCHANGE_LOOKUP_START_FAILURE(2104),

    /** The claim token used to authenticate the client is invalid for this order. */
    MERCHANT_GET_ORDERS_ID_INVALID_TOKEN(2105),

    /** The contract terms hash used to authenticate the client is invalid for this order. */
    MERCHANT_GET_ORDERS_ID_INVALID_CONTRACT_HASH(2106),

    /** The contract terms version is not invalid. */
    MERCHANT_GET_ORDERS_ID_INVALID_CONTRACT_VERSION(2107),

    /** The exchange responded saying that funds were insufficient (for example, due to double-spending). */
    MERCHANT_POST_ORDERS_ID_PAY_INSUFFICIENT_FUNDS(2150),

    /** The denomination key used for payment is not listed among the denomination keys of the exchange. */
    MERCHANT_POST_ORDERS_ID_PAY_DENOMINATION_KEY_NOT_FOUND(2151),

    /** The denomination key used for payment is not audited by an auditor approved by the merchant. */
    MERCHANT_POST_ORDERS_ID_PAY_DENOMINATION_KEY_AUDITOR_FAILURE(2152),

    /** There was an integer overflow totaling up the amounts or deposit fees in the payment. */
    MERCHANT_POST_ORDERS_ID_PAY_AMOUNT_OVERFLOW(2153),

    /** The deposit fees exceed the total value of the payment. */
    MERCHANT_POST_ORDERS_ID_PAY_FEES_EXCEED_PAYMENT(2154),

    /** After considering deposit and wire fees, the payment is insufficient to satisfy the required amount for the contract.  The client should revisit the logic used to calculate fees it must cover. */
    MERCHANT_POST_ORDERS_ID_PAY_INSUFFICIENT_DUE_TO_FEES(2155),

    /** Even if we do not consider deposit and wire fees, the payment is insufficient to satisfy the required amount for the contract. */
    MERCHANT_POST_ORDERS_ID_PAY_PAYMENT_INSUFFICIENT(2156),

    /** The signature over the contract of one of the coins was invalid. */
    MERCHANT_POST_ORDERS_ID_PAY_COIN_SIGNATURE_INVALID(2157),

    /** When we tried to find information about the exchange to issue the deposit, we failed.  This usually only happens if the merchant backend is somehow unable to get its own HTTP client logic to work. */
    MERCHANT_POST_ORDERS_ID_PAY_EXCHANGE_LOOKUP_FAILED(2158),

    /** The refund deadline in the contract is after the transfer deadline. */
    MERCHANT_POST_ORDERS_ID_PAY_REFUND_DEADLINE_PAST_WIRE_TRANSFER_DEADLINE(2159),

    /** The order was already paid (maybe by another wallet). */
    MERCHANT_POST_ORDERS_ID_PAY_ALREADY_PAID(2160),

    /** The payment is too late, the offer has expired. */
    MERCHANT_POST_ORDERS_ID_PAY_OFFER_EXPIRED(2161),

    /** The \"merchant\" field is missing in the proposal data. This is an internal error as the proposal is from the merchant's own database at this point. */
    MERCHANT_POST_ORDERS_ID_PAY_MERCHANT_FIELD_MISSING(2162),

    /** Failed to locate merchant's account information matching the wire hash given in the proposal. */
    MERCHANT_POST_ORDERS_ID_PAY_WIRE_HASH_UNKNOWN(2163),

    /** The deposit time for the denomination has expired. */
    MERCHANT_POST_ORDERS_ID_PAY_DENOMINATION_DEPOSIT_EXPIRED(2165),

    /** The exchange of the deposited coin charges a wire fee that could not be added to the total (total amount too high). */
    MERCHANT_POST_ORDERS_ID_PAY_EXCHANGE_WIRE_FEE_ADDITION_FAILED(2166),

    /** The contract was not fully paid because of refunds. Note that clients MAY treat this as paid if, for example, contracts must be executed despite of refunds. */
    MERCHANT_POST_ORDERS_ID_PAY_REFUNDED(2167),

    /** According to our database, we have refunded more than we were paid (which should not be possible). */
    MERCHANT_POST_ORDERS_ID_PAY_REFUNDS_EXCEED_PAYMENTS(2168),

    /** Legacy stuff. Remove me with protocol v1. */
    DEAD_QQQ_PAY_MERCHANT_POST_ORDERS_ID_ABORT_REFUND_REFUSED_PAYMENT_COMPLETE(2169),

    /** The payment failed at the exchange. */
    MERCHANT_POST_ORDERS_ID_PAY_EXCHANGE_FAILED(2170),

    /** The payment required a minimum age but one of the coins (of a denomination with support for age restriction) did not provide any age_commitment. */
    MERCHANT_POST_ORDERS_ID_PAY_AGE_COMMITMENT_MISSING(2171),

    /** The payment required a minimum age but one of the coins provided an age_commitment that contained a wrong number of public keys compared to the number of age groups defined in the denomination of the coin. */
    MERCHANT_POST_ORDERS_ID_PAY_AGE_COMMITMENT_SIZE_MISMATCH(2172),

    /** The payment required a minimum age but one of the coins provided a minimum_age_sig that couldn't be verified with the given age_commitment for that particular minimum age. */
    MERCHANT_POST_ORDERS_ID_PAY_AGE_VERIFICATION_FAILED(2173),

    /** The payment required no minimum age but one of the coins (of a denomination with support for age restriction) did not provide the required h_age_commitment. */
    MERCHANT_POST_ORDERS_ID_PAY_AGE_COMMITMENT_HASH_MISSING(2174),

    /** The exchange does not support the selected bank account of the merchant. Likely the merchant had stale data on the bank accounts of the exchange and thus selected an inappropriate exchange when making the offer. */
    MERCHANT_POST_ORDERS_ID_PAY_WIRE_METHOD_UNSUPPORTED(2175),

    /** The payment requires the wallet to select a choice from the choices array and pass it in the 'choice_index' field of the request. */
    MERCHANT_POST_ORDERS_ID_PAY_CHOICE_INDEX_MISSING(2176),

    /** The 'choice_index' field is invalid. */
    MERCHANT_POST_ORDERS_ID_PAY_CHOICE_INDEX_OUT_OF_BOUNDS(2177),

    /** The provided 'tokens' array does not match with the required input tokens of the order. */
    MERCHANT_POST_ORDERS_ID_PAY_INPUT_TOKENS_MISMATCH(2178),

    /** Invalid token issue signature (blindly signed by merchant) for provided token. */
    MERCHANT_POST_ORDERS_ID_PAY_TOKEN_ISSUE_SIG_INVALID(2179),

    /** Invalid token use signature (EdDSA, signed by wallet) for provided token. */
    MERCHANT_POST_ORDERS_ID_PAY_TOKEN_USE_SIG_INVALID(2180),

    /** The provided number of tokens does not match the required number. */
    MERCHANT_POST_ORDERS_ID_PAY_TOKEN_COUNT_MISMATCH(2181),

    /** The provided number of token envelopes does not match the specified number. */
    MERCHANT_POST_ORDERS_ID_PAY_TOKEN_ENVELOPE_COUNT_MISMATCH(2182),

    /** Invalid token because it was already used, is expired or not yet valid. */
    MERCHANT_POST_ORDERS_ID_PAY_TOKEN_INVALID(2183),

    /** The payment violates a transaction limit configured at the given exchange. The wallet has a bug in that it failed to check exchange limits during coin selection. Please report the bug to your wallet developer. */
    MERCHANT_POST_ORDERS_ID_PAY_EXCHANGE_TRANSACTION_LIMIT_VIOLATION(2184),

    /** The JSON in the client's request was malformed. This is likely a bug in the client implementation. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_JSON_INVALID(22),

    /** The contract hash does not match the given order ID. */
    MERCHANT_POST_ORDERS_ID_PAID_CONTRACT_HASH_MISMATCH(2200),

    /** The signature of the merchant is not valid for the given contract hash. */
    MERCHANT_POST_ORDERS_ID_PAID_COIN_SIGNATURE_INVALID(2201),

    /** A token family with this ID but conflicting data exists. */
    MERCHANT_POST_TOKEN_FAMILY_CONFLICT(2225),

    /** The backend is unaware of a token family with the given ID. */
    MERCHANT_PATCH_TOKEN_FAMILY_NOT_FOUND(2226),

    /** The merchant failed to send the exchange the refund request. */
    MERCHANT_POST_ORDERS_ID_ABORT_EXCHANGE_REFUND_FAILED(2251),

    /** The merchant failed to find the exchange to process the lookup. */
    MERCHANT_POST_ORDERS_ID_ABORT_EXCHANGE_LOOKUP_FAILED(2252),

    /** The merchant could not find the contract. */
    MERCHANT_POST_ORDERS_ID_ABORT_CONTRACT_NOT_FOUND(2253),

    /** The payment was already completed and thus cannot be aborted anymore. */
    MERCHANT_POST_ORDERS_ID_ABORT_REFUND_REFUSED_PAYMENT_COMPLETE(2254),

    /** The hash provided by the wallet does not match the order. */
    MERCHANT_POST_ORDERS_ID_ABORT_CONTRACT_HASH_MISSMATCH(2255),

    /** The array of coins cannot be empty. */
    MERCHANT_POST_ORDERS_ID_ABORT_COINS_ARRAY_EMPTY(2256),

    /** We are waiting for the exchange to provide us with key material before checking the wire transfer. */
    MERCHANT_EXCHANGE_TRANSFERS_AWAITING_KEYS(2258),

    /** We are waiting for the exchange to provide us with the list of aggregated transactions. */
    MERCHANT_EXCHANGE_TRANSFERS_AWAITING_LIST(2259),

    /** The endpoint indicated in the wire transfer does not belong to a GNU Taler exchange. */
    MERCHANT_EXCHANGE_TRANSFERS_FATAL_NO_EXCHANGE(2260),

    /** The exchange indicated in the wire transfer claims to know nothing about the wire transfer. */
    MERCHANT_EXCHANGE_TRANSFERS_FATAL_NOT_FOUND(2261),

    /** The interaction with the exchange is delayed due to rate limiting. */
    MERCHANT_EXCHANGE_TRANSFERS_RATE_LIMITED(2262),

    /** We experienced a transient failure in our interaction with the exchange. */
    MERCHANT_EXCHANGE_TRANSFERS_TRANSIENT_FAILURE(2263),

    /** The response from the exchange was unacceptable and should be reviewed with an auditor. */
    MERCHANT_EXCHANGE_TRANSFERS_HARD_FAILURE(2264),

    /** Some of the HTTP headers provided by the client were malformed and caused the server to not be able to handle the request. This is likely a bug in the client implementation. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_HTTP_HEADERS_MALFORMED(23),

    /** We could not claim the order because the backend is unaware of it. */
    MERCHANT_POST_ORDERS_ID_CLAIM_NOT_FOUND(2300),

    /** We could not claim the order because someone else claimed it first. */
    MERCHANT_POST_ORDERS_ID_CLAIM_ALREADY_CLAIMED(2301),

    /** The client-side experienced an internal failure. */
    MERCHANT_POST_ORDERS_ID_CLAIM_CLIENT_INTERNAL_FAILURE(2302),

    /** The backend failed to sign the refund request. */
    MERCHANT_POST_ORDERS_ID_REFUND_SIGNATURE_FAILED(2350),

    /** The payto:// URI provided by the client is malformed. Check that you are using the correct syntax as of RFC 8905 and/or that you entered the bank account number correctly. */
    GENERIC_PAYTO_URI_MALFORMED(24),

    /** The client failed to unblind the signature returned by the merchant. */
    MERCHANT_REWARD_PICKUP_UNBLIND_FAILURE(2400),

    /** The exchange returned a failure code for the withdraw operation. */
    MERCHANT_REWARD_PICKUP_EXCHANGE_ERROR(2403),

    /** The merchant failed to add up the amounts to compute the pick up value. */
    MERCHANT_REWARD_PICKUP_SUMMATION_FAILED(2404),

    /** The reward expired. */
    MERCHANT_REWARD_PICKUP_HAS_EXPIRED(2405),

    /** The requested withdraw amount exceeds the amount remaining to be picked up. */
    MERCHANT_REWARD_PICKUP_AMOUNT_EXCEEDS_REWARD_REMAINING(2406),

    /** The merchant did not find the specified denomination key in the exchange's key set. */
    MERCHANT_REWARD_PICKUP_DENOMINATION_UNKNOWN(2407),

    /** A required parameter in the request was missing. This is likely a bug in the client implementation. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_PARAMETER_MISSING(25),

    /** The merchant instance has no active bank accounts configured. However, at least one bank account must be available to create new orders. */
    MERCHANT_PRIVATE_POST_ORDERS_INSTANCE_CONFIGURATION_LACKS_WIRE(2500),

    /** The proposal had no timestamp and the merchant backend failed to obtain the current local time. */
    MERCHANT_PRIVATE_POST_ORDERS_NO_LOCALTIME(2501),

    /** The order provided to the backend could not be parsed; likely some required fields were missing or ill-formed. */
    MERCHANT_PRIVATE_POST_ORDERS_PROPOSAL_PARSE_ERROR(2502),

    /** A conflicting order (sharing the same order identifier) already exists at this merchant backend instance. */
    MERCHANT_PRIVATE_POST_ORDERS_ALREADY_EXISTS(2503),

    /** The order creation request is invalid because the given wire deadline is before the refund deadline. */
    MERCHANT_PRIVATE_POST_ORDERS_REFUND_AFTER_WIRE_DEADLINE(2504),

    /** The order creation request is invalid because the delivery date given is in the past. */
    MERCHANT_PRIVATE_POST_ORDERS_DELIVERY_DATE_IN_PAST(2505),

    /** The order creation request is invalid because a wire deadline of \"never\" is not allowed. */
    MERCHANT_PRIVATE_POST_ORDERS_WIRE_DEADLINE_IS_NEVER(2506),

    /** The order creation request is invalid because the given payment deadline is in the past. */
    MERCHANT_PRIVATE_POST_ORDERS_PAY_DEADLINE_IN_PAST(2507),

    /** The order creation request is invalid because the given refund deadline is in the past. */
    MERCHANT_PRIVATE_POST_ORDERS_REFUND_DEADLINE_IN_PAST(2508),

    /** The backend does not trust any exchange that would allow funds to be wired to any bank account of this instance using the wire method specified with the order. (Note that right now, we do not support the use of exchange bank accounts with mandatory currency conversion.) One likely cause for this is that the taler-merchant-exchangekeyupdate process is not running. */
    MERCHANT_PRIVATE_POST_ORDERS_NO_EXCHANGES_FOR_WIRE_METHOD(2509),

    /** One of the paths to forget is malformed. */
    MERCHANT_PRIVATE_PATCH_ORDERS_ID_FORGET_PATH_SYNTAX_INCORRECT(2510),

    /** One of the paths to forget was not marked as forgettable. */
    MERCHANT_PRIVATE_PATCH_ORDERS_ID_FORGET_PATH_NOT_FORGETTABLE(2511),

    /** The refund amount would violate a refund transaction limit configured at the given exchange. Please find another way to refund the customer, and inquire with your legislator why they make strange banking regulations. */
    MERCHANT_POST_ORDERS_ID_REFUND_EXCHANGE_TRANSACTION_LIMIT_VIOLATION(2512),

    /** The total order amount exceeds hard legal transaction limits from the available exchanges, thus a customer could never legally make this payment. You may try to increase your limits by passing legitimization checks with exchange operators. You could also inquire with your legislator why the limits are prohibitively low for your business. */
    MERCHANT_PRIVATE_POST_ORDERS_AMOUNT_EXCEEDS_LEGAL_LIMITS(2513),

    /** A currency specified to be paid in the contract is not supported by any exchange that this instance can currently use. Possible solutions include (1) specifying a different currency, (2) adding additional suitable exchange operators to the merchant backend configuration, or (3) satisfying compliance rules of an configured exchange to begin using the service of that provider. */
    MERCHANT_PRIVATE_POST_ORDERS_NO_EXCHANGE_FOR_CURRENCY(2514),

    /** The order provided to the backend could not be deleted, our offer is still valid and awaiting payment. Deletion may work later after the offer has expired if it remains unpaid. */
    MERCHANT_PRIVATE_DELETE_ORDERS_AWAITING_PAYMENT(2520),

    /** The order provided to the backend could not be deleted as the order was already paid. */
    MERCHANT_PRIVATE_DELETE_ORDERS_ALREADY_PAID(2521),

    /** The amount to be refunded is inconsistent: either is lower than the previous amount being awarded, or it exceeds the original price paid by the customer. */
    MERCHANT_PRIVATE_POST_ORDERS_ID_REFUND_INCONSISTENT_AMOUNT(2530),

    /** Only paid orders can be refunded, and the frontend specified an unpaid order to issue a refund for. */
    MERCHANT_PRIVATE_POST_ORDERS_ID_REFUND_ORDER_UNPAID(2531),

    /** The refund delay was set to 0 and thus no refunds are ever allowed for this order. */
    MERCHANT_PRIVATE_POST_ORDERS_ID_REFUND_NOT_ALLOWED_BY_CONTRACT(2532),

    /** The token family slug provided in this order could not be found in the merchant database. */
    MERCHANT_PRIVATE_POST_ORDERS_TOKEN_FAMILY_SLUG_UNKNOWN(2533),

    /** A token family referenced in this order is either expired or not valid yet. */
    MERCHANT_PRIVATE_POST_ORDERS_TOKEN_FAMILY_NOT_VALID(2534),

    /** The exchange says it does not know this transfer. */
    MERCHANT_PRIVATE_POST_TRANSFERS_EXCHANGE_UNKNOWN(2550),

    /** We internally failed to execute the /track/transfer request. */
    MERCHANT_PRIVATE_POST_TRANSFERS_REQUEST_ERROR(2551),

    /** The amount transferred differs between what was submitted and what the exchange claimed. */
    MERCHANT_PRIVATE_POST_TRANSFERS_CONFLICTING_TRANSFERS(2552),

    /** The exchange gave conflicting information about a coin which has been wire transferred. */
    MERCHANT_PRIVATE_POST_TRANSFERS_CONFLICTING_REPORTS(2553),

    /** The exchange charged a different wire fee than what it originally advertised, and it is higher. */
    MERCHANT_PRIVATE_POST_TRANSFERS_BAD_WIRE_FEE(2554),

    /** We did not find the account that the transfer was made to. */
    MERCHANT_PRIVATE_POST_TRANSFERS_ACCOUNT_NOT_FOUND(2555),

    /** The backend could not delete the transfer as the echange already replied to our inquiry about it and we have integrated the result. */
    MERCHANT_PRIVATE_DELETE_TRANSFERS_ALREADY_CONFIRMED(2556),

    /** The backend was previously informed about a wire transfer with the same ID but a different amount. Multiple wire transfers with the same ID are not allowed. If the new amount is correct, the old transfer should first be deleted. */
    MERCHANT_PRIVATE_POST_TRANSFERS_CONFLICTING_SUBMISSION(2557),

    /** The amount transferred differs between what was submitted and what the exchange claimed. */
    MERCHANT_EXCHANGE_TRANSFERS_CONFLICTING_TRANSFERS(2563),

    /** A parameter in the request was malformed. This is likely a bug in the client implementation. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_PARAMETER_MALFORMED(26),

    /** The merchant backend cannot create an instance under the given identifier as one already exists. Use PATCH to modify the existing entry. */
    MERCHANT_PRIVATE_POST_INSTANCES_ALREADY_EXISTS(2600),

    /** The merchant backend cannot create an instance because the authentication configuration field is malformed. */
    MERCHANT_PRIVATE_POST_INSTANCES_BAD_AUTH(2601),

    /** The merchant backend cannot update an instance's authentication settings because the provided authentication settings are malformed. */
    MERCHANT_PRIVATE_POST_INSTANCE_AUTH_BAD_AUTH(2602),

    /** The merchant backend cannot create an instance under the given identifier, the previous one was deleted but must be purged first. */
    MERCHANT_PRIVATE_POST_INSTANCES_PURGE_REQUIRED(2603),

    /** The merchant backend cannot update an instance under the given identifier, the previous one was deleted but must be purged first. */
    MERCHANT_PRIVATE_PATCH_INSTANCES_PURGE_REQUIRED(2625),

    /** The bank account referenced in the requested operation was not found. */
    MERCHANT_PRIVATE_ACCOUNT_DELETE_UNKNOWN_ACCOUNT(2626),

    /** The bank account specified in the request already exists at the merchant. */
    MERCHANT_PRIVATE_ACCOUNT_EXISTS(2627),

    /** The product ID exists. */
    MERCHANT_PRIVATE_POST_PRODUCTS_CONFLICT_PRODUCT_EXISTS(2650),

    /** A category with the same name exists already. */
    MERCHANT_PRIVATE_POST_CATEGORIES_CONFLICT_CATEGORY_EXISTS(2651),

    /** The update would have reduced the total amount of product lost, which is not allowed. */
    MERCHANT_PRIVATE_PATCH_PRODUCTS_TOTAL_LOST_REDUCED(2660),

    /** The update would have mean that more stocks were lost than what remains from total inventory after sales, which is not allowed. */
    MERCHANT_PRIVATE_PATCH_PRODUCTS_TOTAL_LOST_EXCEEDS_STOCKS(2661),

    /** The update would have reduced the total amount of product in stock, which is not allowed. */
    MERCHANT_PRIVATE_PATCH_PRODUCTS_TOTAL_STOCKED_REDUCED(2662),

    /** The update would have reduced the total amount of product sold, which is not allowed. */
    MERCHANT_PRIVATE_PATCH_PRODUCTS_TOTAL_SOLD_REDUCED(2663),

    /** The lock request is for more products than we have left (unlocked) in stock. */
    MERCHANT_PRIVATE_POST_PRODUCTS_LOCK_INSUFFICIENT_STOCKS(2670),

    /** The deletion request is for a product that is locked. */
    MERCHANT_PRIVATE_DELETE_PRODUCTS_CONFLICTING_LOCK(2680),

    /** The reserve public key was malformed. */
    GENERIC_RESERVE_PUB_MALFORMED(27),

    /** The requested wire method is not supported by the exchange. */
    MERCHANT_PRIVATE_POST_RESERVES_UNSUPPORTED_WIRE_METHOD(2700),

    /** The requested exchange does not allow rewards. */
    MERCHANT_PRIVATE_POST_RESERVES_REWARDS_NOT_ALLOWED(2701),

    /** The reserve could not be deleted because it is unknown. */
    MERCHANT_PRIVATE_DELETE_RESERVES_NO_SUCH_RESERVE(2710),

    /** The reserve that was used to fund the rewards has expired. */
    MERCHANT_PRIVATE_POST_REWARD_AUTHORIZE_RESERVE_EXPIRED(2750),

    /** The reserve that was used to fund the rewards was not found in the DB. */
    MERCHANT_PRIVATE_POST_REWARD_AUTHORIZE_RESERVE_UNKNOWN(2751),

    /** The backend knows the instance that was supposed to support the reward, and it was configured for rewardping. However, the funds remaining are insufficient to cover the reward, and the merchant should top up the reserve. */
    MERCHANT_PRIVATE_POST_REWARD_AUTHORIZE_INSUFFICIENT_FUNDS(2752),

    /** The backend failed to find a reserve needed to authorize the reward. */
    MERCHANT_PRIVATE_POST_REWARD_AUTHORIZE_RESERVE_NOT_FOUND(2753),

    /** The body in the request could not be decompressed by the server. This is likely a bug in the client implementation. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_COMPRESSION_INVALID(28),

    /** The merchant backend encountered a failure in computing the deposit total. */
    MERCHANT_PRIVATE_GET_ORDERS_ID_AMOUNT_ARITHMETIC_FAILURE(2800),

    /** The template ID already exists. */
    MERCHANT_PRIVATE_POST_TEMPLATES_CONFLICT_TEMPLATE_EXISTS(2850),

    /** The OTP device ID already exists. */
    MERCHANT_PRIVATE_POST_OTP_DEVICES_CONFLICT_OTP_DEVICE_EXISTS(2851),

    /** Amount given in the using template and in the template contract. There is a conflict. */
    MERCHANT_POST_USING_TEMPLATES_AMOUNT_CONFLICT_TEMPLATES_CONTRACT_AMOUNT(2860),

    /** Subject given in the using template and in the template contract. There is a conflict. */
    MERCHANT_POST_USING_TEMPLATES_SUMMARY_CONFLICT_TEMPLATES_CONTRACT_SUBJECT(2861),

    /** Amount not given in the using template and in the template contract. There is a conflict. */
    MERCHANT_POST_USING_TEMPLATES_NO_AMOUNT(2862),

    /** Subject not given in the using template and in the template contract. There is a conflict. */
    MERCHANT_POST_USING_TEMPLATES_NO_SUMMARY(2863),

    /** A segment in the path of the URL provided by the client is malformed. Check that you are using the correct encoding for the URL. */
    GENERIC_PATH_SEGMENT_MALFORMED(29),

    /** The webhook ID elready exists. */
    MERCHANT_PRIVATE_POST_WEBHOOKS_CONFLICT_WEBHOOK_EXISTS(2900),

    /** The webhook serial elready exists. */
    MERCHANT_PRIVATE_POST_PENDING_WEBHOOKS_CONFLICT_PENDING_WEBHOOK_EXISTS(2910),

    /** The client does not support the protocol version advertised by the server. */
    GENERIC_CLIENT_UNSUPPORTED_PROTOCOL_VERSION(3),

    /** The currency involved in the operation is not acceptable for this server. Check your configuration and make sure the currency specified for a given service provider is one of the currencies supported by that provider. */
    GENERIC_CURRENCY_MISMATCH(30),

    /** The auditor refused the connection due to a lack of authorization. */
    AUDITOR_GENERIC_UNAUTHORIZED(3001),

    /** This method is not allowed here. */
    AUDITOR_GENERIC_METHOD_NOT_ALLOWED(3002),

    /** The URI is longer than the longest URI the HTTP server is willing to parse. If you believe this was a legitimate request, contact the server administrators and/or the software developers to increase the limit. */
    GENERIC_URI_TOO_LONG(31),

    /** The signature from the exchange on the deposit confirmation is invalid. */
    AUDITOR_DEPOSIT_CONFIRMATION_SIGNATURE_INVALID(3100),

    /** The exchange key used for the signature on the deposit confirmation was revoked. */
    AUDITOR_EXCHANGE_SIGNING_KEY_REVOKED(3101),

    /** The requested resource could not be found. */
    AUDITOR_RESOURCE_NOT_FOUND(3102),

    /** The URI is missing a path component. */
    AUDITOR_URI_MISSING_PATH_COMPONENT(3103),

    /** The body is too large to be permissible for the endpoint. If you believe this was a legitimate request, contact the server administrators and/or the software developers to increase the limit. */
    GENERIC_UPLOAD_EXCEEDS_LIMIT(32),

    /** The service refused the request due to lack of proper authorization. Accessing this endpoint requires an access token from the account owner. */
    GENERIC_UNAUTHORIZED(40),

    /** The service refused the request as the given authorization token is unknown. You should request a valid access token from the account owner. */
    GENERIC_TOKEN_UNKNOWN(41),

    /** The service refused the request as the given authorization token expired. You should request a fresh authorization token from the account owner. */
    GENERIC_TOKEN_EXPIRED(42),

    /** The service refused the request as the given authorization token is invalid or malformed. You should check that you have the right credentials. */
    GENERIC_TOKEN_MALFORMED(43),

    /** The service refused the request due to lack of proper rights on the resource. You may need different credentials to be allowed to perform this operation. */
    GENERIC_FORBIDDEN(44),

    /** The service failed initialize its connection to the database. The system administrator should check that the service has permissions to access the database and that the database is running. */
    GENERIC_DB_SETUP_FAILED(50),

    /** The service encountered an error event to just start the database transaction. The system administrator should check that the database is running. */
    GENERIC_DB_START_FAILED(51),

    /** Wire transfer attempted with credit and debit party being the same bank account. */
    BANK_SAME_ACCOUNT(5101),

    /** Wire transfer impossible, due to financial limitation of the party that attempted the payment. */
    BANK_UNALLOWED_DEBIT(5102),

    /** Negative numbers are not allowed (as value and/or fraction) to instantiate an amount object. */
    BANK_NEGATIVE_NUMBER_AMOUNT(5103),

    /** A too big number was used (as value and/or fraction) to instantiate an amount object. */
    BANK_NUMBER_TOO_BIG(5104),

    /** The bank account referenced in the requested operation was not found. */
    BANK_UNKNOWN_ACCOUNT(5106),

    /** The transaction referenced in the requested operation (typically a reject operation), was not found. */
    BANK_TRANSACTION_NOT_FOUND(5107),

    /** Bank received a malformed amount string. */
    BANK_BAD_FORMAT_AMOUNT(5108),

    /** The client does not own the account credited by the transaction which is to be rejected, so it has no rights do reject it. */
    BANK_REJECT_NO_RIGHTS(5109),

    /** This error code is returned when no known exception types captured the exception. */
    BANK_UNMANAGED_EXCEPTION(5110),

    /** This error code is used for all those exceptions that do not really need a specific error code to return to the client. Used for example when a client is trying to register with a unavailable username. */
    BANK_SOFT_EXCEPTION(5111),

    /** The request UID for a request to transfer funds has already been used, but with different details for the transfer. */
    BANK_TRANSFER_REQUEST_UID_REUSED(5112),

    /** The withdrawal operation already has a reserve selected.  The current request conflicts with the existing selection. */
    BANK_WITHDRAWAL_OPERATION_RESERVE_SELECTION_CONFLICT(5113),

    /** The wire transfer subject duplicates an existing reserve public key. But wire transfer subjects must be unique. */
    BANK_DUPLICATE_RESERVE_PUB_SUBJECT(5114),

    /** The client requested a transaction that is so far in the past, that it has been forgotten by the bank. */
    BANK_ANCIENT_TRANSACTION_GONE(5115),

    /** The client attempted to abort a transaction that was already confirmed. */
    BANK_ABORT_CONFIRM_CONFLICT(5116),

    /** The client attempted to confirm a transaction that was already aborted. */
    BANK_CONFIRM_ABORT_CONFLICT(5117),

    /** The client attempted to register an account with the same name. */
    BANK_REGISTER_CONFLICT(5118),

    /** The client attempted to confirm a withdrawal operation before the wallet posted the required details. */
    BANK_POST_WITHDRAWAL_OPERATION_REQUIRED(5119),

    /** The client tried to register a new account under a reserved username (like 'admin' for example). */
    BANK_RESERVED_USERNAME_CONFLICT(5120),

    /** The client tried to register a new account with an username already in use. */
    BANK_REGISTER_USERNAME_REUSE(5121),

    /** The client tried to register a new account with a payto:// URI already in use. */
    BANK_REGISTER_PAYTO_URI_REUSE(5122),

    /** The client tried to delete an account with a non null balance. */
    BANK_ACCOUNT_BALANCE_NOT_ZERO(5123),

    /** The client tried to create a transaction or an operation that credit an unknown account. */
    BANK_UNKNOWN_CREDITOR(5124),

    /** The client tried to create a transaction or an operation that debit an unknown account. */
    BANK_UNKNOWN_DEBTOR(5125),

    /** The client tried to perform an action prohibited for exchange accounts. */
    BANK_ACCOUNT_IS_EXCHANGE(5126),

    /** The client tried to perform an action reserved for exchange accounts. */
    BANK_ACCOUNT_IS_NOT_EXCHANGE(5127),

    /** Received currency conversion is wrong. */
    BANK_BAD_CONVERSION(5128),

    /** The account referenced in this operation is missing tan info for the chosen channel. */
    BANK_MISSING_TAN_INFO(5129),

    /** The client attempted to confirm a transaction with incomplete info. */
    BANK_CONFIRM_INCOMPLETE(5130),

    /** The request rate is too high. The server is refusing requests to guard against brute-force attacks. */
    BANK_TAN_RATE_LIMITED(5131),

    /** This TAN channel is not supported. */
    BANK_TAN_CHANNEL_NOT_SUPPORTED(5132),

    /** Failed to send TAN using the helper script. Either script is not found, or script timeout, or script terminated with a non-successful result. */
    BANK_TAN_CHANNEL_SCRIPT_FAILED(5133),

    /** The client's response to the challenge was invalid. */
    BANK_TAN_CHALLENGE_FAILED(5134),

    /** A non-admin user has tried to change their legal name. */
    BANK_NON_ADMIN_PATCH_LEGAL_NAME(5135),

    /** A non-admin user has tried to change their debt limit. */
    BANK_NON_ADMIN_PATCH_DEBT_LIMIT(5136),

    /** A non-admin user has tried to change their password whihout providing the current one. */
    BANK_NON_ADMIN_PATCH_MISSING_OLD_PASSWORD(5137),

    /** Provided old password does not match current password. */
    BANK_PATCH_BAD_OLD_PASSWORD(5138),

    /** An admin user has tried to become an exchange. */
    BANK_PATCH_ADMIN_EXCHANGE(5139),

    /** A non-admin user has tried to change their cashout account. */
    BANK_NON_ADMIN_PATCH_CASHOUT(5140),

    /** A non-admin user has tried to change their contact info. */
    BANK_NON_ADMIN_PATCH_CONTACT(5141),

    /** The client tried to create a transaction that credit the admin account. */
    BANK_ADMIN_CREDITOR(5142),

    /** The referenced challenge was not found. */
    BANK_CHALLENGE_NOT_FOUND(5143),

    /** The referenced challenge has expired. */
    BANK_TAN_CHALLENGE_EXPIRED(5144),

    /** A non-admin user has tried to create an account with 2fa. */
    BANK_NON_ADMIN_SET_TAN_CHANNEL(5145),

    /** A non-admin user has tried to set their minimum cashout amount. */
    BANK_NON_ADMIN_SET_MIN_CASHOUT(5146),

    /** Amount of currency conversion it less than the minimum allowed. */
    BANK_CONVERSION_AMOUNT_TO_SMALL(5147),

    /** Specified amount will not work for this withdrawal. */
    BANK_AMOUNT_DIFFERS(5148),

    /** The backend requires an amount to be specified. */
    BANK_AMOUNT_REQUIRED(5149),

    /** Provided password is too short. */
    BANK_PASSWORD_TOO_SHORT(5150),

    /** Provided password is too long. */
    BANK_PASSWORD_TOO_LONG(5151),

    /** Bank account is locked and cannot authenticate using his password. */
    BANK_ACCOUNT_LOCKED(5152),

    /** The client attempted to update a transaction' details that was already aborted. */
    BANK_UPDATE_ABORT_CONFLICT(5153),

    /** The wtid for a request to transfer funds has already been used, but with a different request unpaid. */
    BANK_TRANSFER_WTID_REUSED(5154),

    /** The service failed to store information in its database. The system administrator should check that the database is running and review the service logs. */
    GENERIC_DB_STORE_FAILED(52),

    /** The service failed to fetch information from its database. The system administrator should check that the database is running and review the service logs. */
    GENERIC_DB_FETCH_FAILED(53),

    /** The service encountered an unrecoverable error trying to commit a transaction to the database. The system administrator should check that the database is running and review the service logs. */
    GENERIC_DB_COMMIT_FAILED(54),

    /** The service encountered an error event to commit the database transaction, even after repeatedly retrying it there was always a conflicting transaction. This indicates a repeated serialization error; it should only happen if some client maliciously tries to create conflicting concurrent transactions. It could also be a sign of a missing index. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_DB_SOFT_FAILURE(55),

    /** The service's database is inconsistent and violates service-internal invariants. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_DB_INVARIANT_FAILURE(56),

    /** The HTTP server experienced an internal invariant failure (bug). Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_INTERNAL_INVARIANT_FAILURE(60),

    /** The service could not compute a cryptographic hash over some JSON value. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_FAILED_COMPUTE_JSON_HASH(61),

    /** The sync service failed find the account in its database. */
    SYNC_ACCOUNT_UNKNOWN(6100),

    /** The SHA-512 hash provided in the If-None-Match header is malformed. */
    SYNC_BAD_IF_NONE_MATCH(6101),

    /** The SHA-512 hash provided in the If-Match header is malformed or missing. */
    SYNC_BAD_IF_MATCH(6102),

    /** The signature provided in the \"Sync-Signature\" header is malformed or missing. */
    SYNC_BAD_SYNC_SIGNATURE(6103),

    /** The signature provided in the \"Sync-Signature\" header does not match the account, old or new Etags. */
    SYNC_INVALID_SIGNATURE(6104),

    /** The \"Content-length\" field for the upload is not a number. */
    SYNC_MALFORMED_CONTENT_LENGTH(6105),

    /** The \"Content-length\" field for the upload is too big based on the server's terms of service. */
    SYNC_EXCESSIVE_CONTENT_LENGTH(6106),

    /** The server is out of memory to handle the upload. Trying again later may succeed. */
    SYNC_OUT_OF_MEMORY_ON_CONTENT_LENGTH(6107),

    /** The uploaded data does not match the Etag. */
    SYNC_INVALID_UPLOAD(6108),

    /** HTTP server experienced a timeout while awaiting promised payment. */
    SYNC_PAYMENT_GENERIC_TIMEOUT(6109),

    /** Sync could not setup the payment request with its own backend. */
    SYNC_PAYMENT_CREATE_BACKEND_ERROR(6110),

    /** The sync service failed find the backup to be updated in its database. */
    SYNC_PREVIOUS_BACKUP_UNKNOWN(6111),

    /** The \"Content-length\" field for the upload is missing. */
    SYNC_MISSING_CONTENT_LENGTH(6112),

    /** Sync had problems communicating with its payment backend. */
    SYNC_GENERIC_BACKEND_ERROR(6113),

    /** Sync experienced a timeout communicating with its payment backend. */
    SYNC_GENERIC_BACKEND_TIMEOUT(6114),

    /** The service could not compute an amount. Check if you are using the latest available version and/or file a report with the developers. */
    GENERIC_FAILED_COMPUTE_AMOUNT(62),

    /** The HTTP server had insufficient memory to parse the request. Restarting services periodically can help, especially if Postgres is using excessive amounts of memory. Check with the system administrator to investigate. */
    GENERIC_PARSER_OUT_OF_MEMORY(70),

    /** The wallet does not implement a version of the exchange protocol that is compatible with the protocol version of the exchange. */
    WALLET_EXCHANGE_PROTOCOL_VERSION_INCOMPATIBLE(7000),

    /** The wallet encountered an unexpected exception.  This is likely a bug in the wallet implementation. */
    WALLET_UNEXPECTED_EXCEPTION(7001),

    /** The wallet received a response from a server, but the response can't be parsed. */
    WALLET_RECEIVED_MALFORMED_RESPONSE(7002),

    /** The wallet tried to make a network request, but it received no response. */
    WALLET_NETWORK_ERROR(7003),

    /** The wallet tried to make a network request, but it was throttled. */
    WALLET_HTTP_REQUEST_THROTTLED(7004),

    /** The wallet made a request to a service, but received an error response it does not know how to handle. */
    WALLET_UNEXPECTED_REQUEST_ERROR(7005),

    /** The denominations offered by the exchange are insufficient.  Likely the exchange is badly configured or not maintained. */
    WALLET_EXCHANGE_DENOMINATIONS_INSUFFICIENT(7006),

    /** The wallet does not support the operation requested by a client. */
    WALLET_CORE_API_OPERATION_UNKNOWN(7007),

    /** The given taler://pay URI is invalid. */
    WALLET_INVALID_TALER_PAY_URI(7008),

    /** The signature on a coin by the exchange's denomination key is invalid after unblinding it. */
    WALLET_EXCHANGE_COIN_SIGNATURE_INVALID(7009),

    /** The exchange does not know about the reserve (yet), and thus withdrawal can't progress. */
    WALLET_EXCHANGE_WITHDRAW_RESERVE_UNKNOWN_AT_EXCHANGE(7010),

    /** The wallet core service is not available. */
    WALLET_CORE_NOT_AVAILABLE(7011),

    /** The bank has aborted a withdrawal operation, and thus a withdrawal can't complete. */
    WALLET_WITHDRAWAL_OPERATION_ABORTED_BY_BANK(7012),

    /** An HTTP request made by the wallet timed out. */
    WALLET_HTTP_REQUEST_GENERIC_TIMEOUT(7013),

    /** The order has already been claimed by another wallet. */
    WALLET_ORDER_ALREADY_CLAIMED(7014),

    /** A group of withdrawal operations (typically for the same reserve at the same exchange) has errors and will be tried again later. */
    WALLET_WITHDRAWAL_GROUP_INCOMPLETE(7015),

    /** The signature on a coin by the exchange's denomination key (obtained through the merchant via a reward) is invalid after unblinding it. */
    WALLET_REWARD_COIN_SIGNATURE_INVALID(7016),

    /** The wallet does not implement a version of the bank integration API that is compatible with the version offered by the bank. */
    WALLET_BANK_INTEGRATION_PROTOCOL_VERSION_INCOMPATIBLE(7017),

    /** The wallet processed a taler://pay URI, but the merchant base URL in the downloaded contract terms does not match the merchant base URL derived from the URI. */
    WALLET_CONTRACT_TERMS_BASE_URL_MISMATCH(7018),

    /** The merchant's signature on the contract terms is invalid. */
    WALLET_CONTRACT_TERMS_SIGNATURE_INVALID(7019),

    /** The contract terms given by the merchant are malformed. */
    WALLET_CONTRACT_TERMS_MALFORMED(7020),

    /** A pending operation failed, and thus the request can't be completed. */
    WALLET_PENDING_OPERATION_FAILED(7021),

    /** A payment was attempted, but the merchant had an internal server error (5xx). */
    WALLET_PAY_MERCHANT_SERVER_ERROR(7022),

    /** The crypto worker failed. */
    WALLET_CRYPTO_WORKER_ERROR(7023),

    /** The crypto worker received a bad request. */
    WALLET_CRYPTO_WORKER_BAD_REQUEST(7024),

    /** A KYC step is required before withdrawal can proceed. */
    WALLET_WITHDRAWAL_KYC_REQUIRED(7025),

    /** The wallet does not have sufficient balance to create a deposit group. */
    WALLET_DEPOSIT_GROUP_INSUFFICIENT_BALANCE(7026),

    /** The wallet does not have sufficient balance to create a peer push payment. */
    WALLET_PEER_PUSH_PAYMENT_INSUFFICIENT_BALANCE(7027),

    /** The wallet does not have sufficient balance to pay for an invoice. */
    WALLET_PEER_PULL_PAYMENT_INSUFFICIENT_BALANCE(7028),

    /** A group of refresh operations has errors and will be tried again later. */
    WALLET_REFRESH_GROUP_INCOMPLETE(7029),

    /** The exchange's self-reported base URL does not match the one that the wallet is using. */
    WALLET_EXCHANGE_BASE_URL_MISMATCH(7030),

    /** The order has already been paid by another wallet. */
    WALLET_ORDER_ALREADY_PAID(7031),

    /** An exchange that is required for some request is currently not available. */
    WALLET_EXCHANGE_UNAVAILABLE(7032),

    /** An exchange entry is still used by the exchange, thus it can't be deleted without purging. */
    WALLET_EXCHANGE_ENTRY_USED(7033),

    /** The wallet database is unavailable and the wallet thus is not operational. */
    WALLET_DB_UNAVAILABLE(7034),

    /** A taler:// URI is malformed and can't be parsed. */
    WALLET_TALER_URI_MALFORMED(7035),

    /** A wallet-core request was cancelled and thus can't provide a response. */
    WALLET_CORE_REQUEST_CANCELLED(7036),

    /** A wallet-core request failed because the user needs to first accept the exchange's terms of service. */
    WALLET_EXCHANGE_TOS_NOT_ACCEPTED(7037),

    /** An exchange entry could not be updated, as the exchange's new details conflict with the new details. */
    WALLET_EXCHANGE_ENTRY_UPDATE_CONFLICT(7038),

    /** The wallet's information about the exchange is outdated. */
    WALLET_EXCHANGE_ENTRY_OUTDATED(7039),

    /** The merchant needs to do KYC first, the payment could not be completed. */
    WALLET_PAY_MERCHANT_KYC_MISSING(7040),

    /** A peer-pull-debit transaction was aborted because the exchange reported the purse as gone. */
    WALLET_PEER_PULL_DEBIT_PURSE_GONE(7041),

    /** A transaction was aborted on explicit request by the user. */
    WALLET_TRANSACTION_ABORTED_BY_USER(7042),

    /** A transaction was abandoned on explicit request by the user. */
    WALLET_TRANSACTION_ABANDONED_BY_USER(7043),

    /** A payment was attempted, but the merchant claims the order is gone (likely expired). */
    WALLET_PAY_MERCHANT_ORDER_GONE(7044),

    /** The wallet does not have an entry for the requested exchange. */
    WALLET_EXCHANGE_ENTRY_NOT_FOUND(7045),

    /** The wallet is not able to process the request due to the transaction's state. */
    WALLET_REQUEST_TRANSACTION_STATE_UNSUPPORTED(7046),

    /** A transaction could not be processed due to an unrecoverable protocol violation. */
    WALLET_TRANSACTION_PROTOCOL_VIOLATION(7047),

    /** A parameter in the request is malformed or missing. */
    WALLET_CORE_API_BAD_REQUEST(7048),

    /** The HTTP server failed to allocate memory. Restarting services periodically can help, especially if Postgres is using excessive amounts of memory. Check with the system administrator to investigate. */
    GENERIC_ALLOCATION_FAILURE(71),

    /** The HTTP server failed to allocate memory for building JSON reply. Restarting services periodically can help, especially if Postgres is using excessive amounts of memory. Check with the system administrator to investigate. */
    GENERIC_JSON_ALLOCATION_FAILURE(72),

    /** The HTTP server failed to allocate memory for making a CURL request. Restarting services periodically can help, especially if Postgres is using excessive amounts of memory. Check with the system administrator to investigate. */
    GENERIC_CURL_ALLOCATION_FAILURE(73),

    /** The backend could not locate a required template to generate an HTML reply. The system administrator should check if the resource files are installed in the correct location and are readable to the service. */
    GENERIC_FAILED_TO_LOAD_TEMPLATE(74),

    /** The backend could not expand the template to generate an HTML reply. The system administrator should investigate the logs and check if the templates are well-formed. */
    GENERIC_FAILED_TO_EXPAND_TEMPLATE(75),

    /** We encountered a timeout with our payment backend. */
    ANASTASIS_GENERIC_BACKEND_TIMEOUT(8000),

    /** The backend requested payment, but the request is malformed. */
    ANASTASIS_GENERIC_INVALID_PAYMENT_REQUEST(8001),

    /** The backend got an unexpected reply from the payment processor. */
    ANASTASIS_GENERIC_BACKEND_ERROR(8002),

    /** The \"Content-length\" field for the upload is missing. */
    ANASTASIS_GENERIC_MISSING_CONTENT_LENGTH(8003),

    /** The \"Content-length\" field for the upload is malformed. */
    ANASTASIS_GENERIC_MALFORMED_CONTENT_LENGTH(8004),

    /** The backend failed to setup an order with the payment processor. */
    ANASTASIS_GENERIC_ORDER_CREATE_BACKEND_ERROR(8005),

    /** The backend was not authorized to check for payment with the payment processor. */
    ANASTASIS_GENERIC_PAYMENT_CHECK_UNAUTHORIZED(8006),

    /** The backend could not check payment status with the payment processor. */
    ANASTASIS_GENERIC_PAYMENT_CHECK_START_FAILED(8007),

    /** The Anastasis provider could not be reached. */
    ANASTASIS_GENERIC_PROVIDER_UNREACHABLE(8008),

    /** HTTP server experienced a timeout while awaiting promised payment. */
    ANASTASIS_PAYMENT_GENERIC_TIMEOUT(8009),

    /** The key share is unknown to the provider. */
    ANASTASIS_TRUTH_UNKNOWN(8108),

    /** The authorization method used for the key share is no longer supported by the provider. */
    ANASTASIS_TRUTH_AUTHORIZATION_METHOD_NO_LONGER_SUPPORTED(8109),

    /** The client needs to respond to the challenge. */
    ANASTASIS_TRUTH_CHALLENGE_RESPONSE_REQUIRED(8110),

    /** The client's response to the challenge was invalid. */
    ANASTASIS_TRUTH_CHALLENGE_FAILED(8111),

    /** The backend is not aware of having issued the provided challenge code. Either this is the wrong code, or it has expired. */
    ANASTASIS_TRUTH_CHALLENGE_UNKNOWN(8112),

    /** The backend failed to initiate the authorization process. */
    ANASTASIS_TRUTH_AUTHORIZATION_START_FAILED(8114),

    /** The authorization succeeded, but the key share is no longer available. */
    ANASTASIS_TRUTH_KEY_SHARE_GONE(8115),

    /** The backend forgot the order we asked the client to pay for */
    ANASTASIS_TRUTH_ORDER_DISAPPEARED(8116),

    /** The backend itself reported a bad exchange interaction. */
    ANASTASIS_TRUTH_BACKEND_EXCHANGE_BAD(8117),

    /** The backend reported a payment status we did not expect. */
    ANASTASIS_TRUTH_UNEXPECTED_PAYMENT_STATUS(8118),

    /** The backend failed to setup the order for payment. */
    ANASTASIS_TRUTH_PAYMENT_CREATE_BACKEND_ERROR(8119),

    /** The decryption of the key share failed with the provided key. */
    ANASTASIS_TRUTH_DECRYPTION_FAILED(8120),

    /** The request rate is too high. The server is refusing requests to guard against brute-force attacks. */
    ANASTASIS_TRUTH_RATE_LIMITED(8121),

    /** A request to issue a challenge is not valid for this authentication method. */
    ANASTASIS_TRUTH_CHALLENGE_WRONG_METHOD(8123),

    /** The backend failed to store the key share because the UUID is already in use. */
    ANASTASIS_TRUTH_UPLOAD_UUID_EXISTS(8150),

    /** The backend failed to store the key share because the authorization method is not supported. */
    ANASTASIS_TRUTH_UPLOAD_METHOD_NOT_SUPPORTED(8151),

    /** The provided phone number is not an acceptable number. */
    ANASTASIS_SMS_PHONE_INVALID(8200),

    /** Failed to run the SMS transmission helper process. */
    ANASTASIS_SMS_HELPER_EXEC_FAILED(8201),

    /** Provider failed to send SMS. Helper terminated with a non-successful result. */
    ANASTASIS_SMS_HELPER_COMMAND_FAILED(8202),

    /** The provided email address is not an acceptable address. */
    ANASTASIS_EMAIL_INVALID(8210),

    /** Failed to run the E-mail transmission helper process. */
    ANASTASIS_EMAIL_HELPER_EXEC_FAILED(8211),

    /** Provider failed to send E-mail. Helper terminated with a non-successful result. */
    ANASTASIS_EMAIL_HELPER_COMMAND_FAILED(8212),

    /** The provided postal address is not an acceptable address. */
    ANASTASIS_POST_INVALID(8220),

    /** Failed to run the mail transmission helper process. */
    ANASTASIS_POST_HELPER_EXEC_FAILED(8221),

    /** Provider failed to send mail. Helper terminated with a non-successful result. */
    ANASTASIS_POST_HELPER_COMMAND_FAILED(8222),

    /** The provided IBAN address is not an acceptable IBAN. */
    ANASTASIS_IBAN_INVALID(8230),

    /** The provider has not yet received the IBAN wire transfer authorizing the disclosure of the key share. */
    ANASTASIS_IBAN_MISSING_TRANSFER(8231),

    /** The backend did not find a TOTP key in the data provided. */
    ANASTASIS_TOTP_KEY_MISSING(8240),

    /** The key provided does not satisfy the format restrictions for an Anastasis TOTP key. */
    ANASTASIS_TOTP_KEY_INVALID(8241),

    /** The given if-none-match header is malformed. */
    ANASTASIS_POLICY_BAD_IF_NONE_MATCH(8301),

    /** The server is out of memory to handle the upload. Trying again later may succeed. */
    ANASTASIS_POLICY_OUT_OF_MEMORY_ON_CONTENT_LENGTH(8304),

    /** The signature provided in the \"Anastasis-Policy-Signature\" header is malformed or missing. */
    ANASTASIS_POLICY_BAD_SIGNATURE(8305),

    /** The given if-match header is malformed. */
    ANASTASIS_POLICY_BAD_IF_MATCH(8306),

    /** The uploaded data does not match the Etag. */
    ANASTASIS_POLICY_INVALID_UPLOAD(8307),

    /** The provider is unaware of the requested policy. */
    ANASTASIS_POLICY_NOT_FOUND(8350),

    /** The given action is invalid for the current state of the reducer. */
    ANASTASIS_REDUCER_ACTION_INVALID(8400),

    /** The given state of the reducer is invalid. */
    ANASTASIS_REDUCER_STATE_INVALID(8401),

    /** The given input to the reducer is invalid. */
    ANASTASIS_REDUCER_INPUT_INVALID(8402),

    /** The selected authentication method does not work for the Anastasis provider. */
    ANASTASIS_REDUCER_AUTHENTICATION_METHOD_NOT_SUPPORTED(8403),

    /** The given input and action do not work for the current state. */
    ANASTASIS_REDUCER_INPUT_INVALID_FOR_STATE(8404),

    /** We experienced an unexpected failure interacting with the backend. */
    ANASTASIS_REDUCER_BACKEND_FAILURE(8405),

    /** The contents of a resource file did not match our expectations. */
    ANASTASIS_REDUCER_RESOURCE_MALFORMED(8406),

    /** A required resource file is missing. */
    ANASTASIS_REDUCER_RESOURCE_MISSING(8407),

    /** An input did not match the regular expression. */
    ANASTASIS_REDUCER_INPUT_REGEX_FAILED(8408),

    /** An input did not match the custom validation logic. */
    ANASTASIS_REDUCER_INPUT_VALIDATION_FAILED(8409),

    /** Our attempts to download the recovery document failed with all providers. Most likely the personal information you entered differs from the information you provided during the backup process and you should go back to the previous step. Alternatively, if you used a backup provider that is unknown to this application, you should add that provider manually. */
    ANASTASIS_REDUCER_POLICY_LOOKUP_FAILED(8410),

    /** Anastasis provider reported a fatal failure. */
    ANASTASIS_REDUCER_BACKUP_PROVIDER_FAILED(8411),

    /** Anastasis provider failed to respond to the configuration request. */
    ANASTASIS_REDUCER_PROVIDER_CONFIG_FAILED(8412),

    /** The policy we downloaded is malformed. Must have been a client error while creating the backup. */
    ANASTASIS_REDUCER_POLICY_MALFORMED(8413),

    /** We failed to obtain the policy, likely due to a network issue. */
    ANASTASIS_REDUCER_NETWORK_FAILED(8414),

    /** The recovered secret did not match the required syntax. */
    ANASTASIS_REDUCER_SECRET_MALFORMED(8415),

    /** The challenge data provided is too large for the available providers. */
    ANASTASIS_REDUCER_CHALLENGE_DATA_TOO_BIG(8416),

    /** The provided core secret is too large for some of the providers. */
    ANASTASIS_REDUCER_SECRET_TOO_BIG(8417),

    /** The provider returned in invalid configuration. */
    ANASTASIS_REDUCER_PROVIDER_INVALID_CONFIG(8418),

    /** The reducer encountered an internal error, likely a bug that needs to be reported. */
    ANASTASIS_REDUCER_INTERNAL_ERROR(8419),

    /** The reducer already synchronized with all providers. */
    ANASTASIS_REDUCER_PROVIDERS_ALREADY_SYNCED(8420),

    /** The Donau failed to perform the operation as it could not find the private keys. This is a problem with the Donau setup, not with the client's request. */
    DONAU_GENERIC_KEYS_MISSING(8607),

    /** The signature of the charity key is not valid. */
    DONAU_CHARITY_SIGNATURE_INVALID(8608),

    /** The charity is unknown. */
    DONAU_CHARITY_NOT_FOUND(8609),

    /** The donation amount specified in the request exceeds the limit of the charity. */
    DONAU_EXCEEDING_DONATION_LIMIT(8610),

    /** The Donau is not aware of the donation unit requested for the operation. */
    DONAU_GENERIC_DONATION_UNIT_UNKNOWN(8611),

    /** The Donau failed to talk to the process responsible for its private donation unit keys or the helpers had no donation units (properly) configured. */
    DONAU_DONATION_UNIT_HELPER_UNAVAILABLE(8612),

    /** The Donau failed to talk to the process responsible for its private signing keys. */
    DONAU_SIGNKEY_HELPER_UNAVAILABLE(8613),

    /** The response from the online signing key helper process was malformed. */
    DONAU_SIGNKEY_HELPER_BUG(8614),

    /** The number of segments included in the URI does not match the number of segments expected by the endpoint. */
    DONAU_GENERIC_WRONG_NUMBER_OF_SEGMENTS(8615),

    /** The signature of the donation receipt is not valid. */
    DONAU_DONATION_RECEIPT_SIGNATURE_INVALID(8616),

    /** The client reused a unique donor identifier nonce, which is not allowed. */
    DONAU_DONOR_IDENTIFIER_NONCE_REUSE(8617),

    /** A generic error happened in the LibEuFin nexus.  See the enclose details JSON for more information. */
    LIBEUFIN_NEXUS_GENERIC_ERROR(9000),

    /** An uncaught exception happened in the LibEuFin nexus service. */
    LIBEUFIN_NEXUS_UNCAUGHT_EXCEPTION(9001),

    /** A generic error happened in the LibEuFin sandbox.  See the enclose details JSON for more information. */
    LIBEUFIN_SANDBOX_GENERIC_ERROR(9500),

    /** An uncaught exception happened in the LibEuFin sandbox service. */
    LIBEUFIN_SANDBOX_UNCAUGHT_EXCEPTION(9501),

    /** This validation method is not supported by the service. */
    TALDIR_METHOD_NOT_SUPPORTED(9600),

    /** Number of allowed attempts for initiating a challenge exceeded. */
    TALDIR_REGISTER_RATE_LIMITED(9601),

    /** The client is unknown or unauthorized. */
    CHALLENGER_GENERIC_CLIENT_UNKNOWN(9750),

    /** The client is not authorized to use the given redirect URI. */
    CHALLENGER_GENERIC_CLIENT_FORBIDDEN_BAD_REDIRECT_URI(9751),

    /** The service failed to execute its helper process to send the challenge. */
    CHALLENGER_HELPER_EXEC_FAILED(9752),

    /** The grant is unknown to the service (it could also have expired). */
    CHALLENGER_GRANT_UNKNOWN(9753),

    /** The code given is not even well-formed. */
    CHALLENGER_CLIENT_FORBIDDEN_BAD_CODE(9754),

    /** The service is not aware of the referenced validation process. */
    CHALLENGER_GENERIC_VALIDATION_UNKNOWN(9755),

    /** The code given is not valid. */
    CHALLENGER_CLIENT_FORBIDDEN_INVALID_CODE(9756),

    /** Too many attempts have been made, validation is temporarily disabled for this address. */
    CHALLENGER_TOO_MANY_ATTEMPTS(9757),

    /** The PIN code provided is incorrect. */
    CHALLENGER_INVALID_PIN(9758),

    /** The token cannot be valid as no address was ever provided by the client. */
    CHALLENGER_MISSING_ADDRESS(9759),

    /** The client is not allowed to change the address being validated. */
    CHALLENGER_CLIENT_FORBIDDEN_READ_ONLY(9760),

    /** End of error code range. */
    END(9999),
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = TalerErrorCode::class)
object TalerErrorCodeSerializer : KSerializer<TalerErrorCode> {

    override val descriptor =
        PrimitiveSerialDescriptor("TalerErrorCodeSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): TalerErrorCode {
        val code = decoder.decodeInt()
        return enumValues<TalerErrorCode>().firstOrNull {
            code == it.code
        } ?: TalerErrorCode.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: TalerErrorCode) {
        error("Not supported")
    }
}
