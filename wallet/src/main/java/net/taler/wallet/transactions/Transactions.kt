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

package net.taler.wallet.transactions

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import net.taler.common.Amount
import net.taler.common.Bech32
import net.taler.common.ContractProduct
import net.taler.common.ContractTerms
import net.taler.common.Timestamp
import net.taler.wallet.R
import net.taler.wallet.TAG
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.common.CurrencySpecification
import net.taler.common.Merchant
import net.taler.common.RelativeTime
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.refund.RefundPaymentInfo
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionMajorState.None
import net.taler.wallet.transactions.TransactionMajorState.Pending
import net.taler.wallet.transactions.WithdrawalDetails.ManualTransfer
import net.taler.wallet.transactions.WithdrawalDetails.TalerBankIntegrationApi
import net.taler.wallet.withdraw.TransferData
import java.util.UUID

@Serializable
data class Transactions(
    @Serializable(with = TransactionListSerializer::class)
    val transactions: List<Transaction>,
)

class TransactionListSerializer : KSerializer<List<Transaction>> {
    private val serializer = ListSerializer(TransactionSerializer())
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): List<Transaction> {
        return decoder.decodeSerializableValue(serializer)
    }

    override fun serialize(encoder: Encoder, value: List<Transaction>) {
        throw NotImplementedError()
    }
}

class TransactionSerializer : KSerializer<Transaction> {

    private val serializer = Transaction.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor
    private val jsonSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    override fun deserialize(decoder: Decoder): Transaction {
        return try {
            decoder.decodeSerializableValue(serializer)
        } catch (e: SerializationException) {
            Log.e(TAG, "Error deserializing transaction.", e)
            DummyTransaction(
                transactionId = UUID.randomUUID().toString(),
                timestamp = Timestamp.now(),
                error = TalerErrorInfo(
                    code = TalerErrorCode.UNKNOWN,
                    message = e.message,
                    extra = decoder.decodeSerializableValue(jsonSerializer)
                ),
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Transaction) {
        throw NotImplementedError()
    }
}

@Serializable
sealed class Transaction {
    abstract val transactionId: String
    abstract val timestamp: Timestamp
    abstract val txState: TransactionState
    abstract val txActions: List<TransactionAction>
    abstract val error: TalerErrorInfo?
    abstract val amountRaw: Amount
    abstract val amountEffective: Amount
    abstract val scopes: List<ScopeInfo>

    @get:DrawableRes
    abstract val icon: Int

    @get:IdRes
    abstract val detailPageNav: Int

    abstract val amountType: AmountType

    abstract fun getTitle(context: Context): String

    @get:StringRes
    abstract val generalTitleRes: Int
}

@Serializable
enum class TransactionAction {
    // Common States
    @SerialName("delete")
    Delete,

    @SerialName("suspend")
    Suspend,

    @SerialName("resume")
    Resume,

    @SerialName("abort")
    Abort,

    @SerialName("fail")
    Fail,

    @SerialName("retry")
    Retry,
}

sealed class AmountType {
    object Positive : AmountType()
    object Negative : AmountType()
    object Neutral : AmountType()
}

@Serializable
@SerialName("withdrawal")
class TransactionWithdrawal(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val kycUrl: String? = null,
    val exchangeBaseUrl: String? = null,
    val withdrawalDetails: WithdrawalDetails,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
) : Transaction() {
    override val icon = R.drawable.transaction_withdrawal

    override val detailPageNav = R.id.action_nav_transactions_detail_withdrawal

    @Transient
    override val amountType = AmountType.Positive
    override fun getTitle(context: Context) = context.getString(R.string.withdraw_title)
    override val generalTitleRes = R.string.withdraw_title
    val confirmed: Boolean
        get() = txState.major != Pending && (
                (withdrawalDetails is TalerBankIntegrationApi && withdrawalDetails.confirmed) ||
                        withdrawalDetails is ManualTransfer
                )
}

@Serializable
sealed class WithdrawalDetails {
    @Serializable
    @SerialName("manual-transfer")
    class ManualTransfer(
        val exchangeCreditAccountDetails: List<WithdrawalExchangeAccountDetails>? = null,
        val reserveClosingDelay: RelativeTime,
    ) : WithdrawalDetails()

    @Serializable
    @SerialName("taler-bank-integration-api")
    class TalerBankIntegrationApi(
        /**
         * Set to true if the bank has confirmed the withdrawal, false if not.
         * An unconfirmed withdrawal usually requires user-input
         * and should be highlighted in the UI.
         * See also bankConfirmationUrl below.
         */
        val confirmed: Boolean,

        /**
         * If the withdrawal is unconfirmed, this can include a URL for user-initiated confirmation.
         */
        val bankConfirmationUrl: String? = null,
    ) : WithdrawalDetails()
}

@Serializable
data class WithdrawalExchangeAccountDetails (
    /**
     * Payto URI to credit the exchange.
     *
     * Depending on whether the (manual!) withdrawal is accepted or just
     * being checked, this already includes the subject with the
     * reserve public key.
     */
    val paytoUri: String,

    /**
     * Status that indicates whether the account can be used
     * by the user to send funds for a withdrawal.
     *
     * ok: account should be shown to the user
     * error: account should not be shown to the user, UIs might render the error (in conversionError),
     *   especially in dev mode.
     */
    val status: Status,

    /**
     * Transfer amount. Might be in a different currency than the requested
     * amount for withdrawal.
     *
     * Redundant with the amount in paytoUri, just included to avoid parsing.
     */
    val transferAmount: Amount? = null,

    /**
     * Currency specification for the external currency.
     *
     * Only included if this account requires a currency conversion.
     */
    val currencySpecification: CurrencySpecification? = null,

    /**
     * Further restrictions for sending money to the
     * exchange.
     */
    val creditRestrictions: List<AccountRestriction>? = null,

    /**
     * Label given to the account or the account's bank by the exchange.
     */
    val bankLabel: String? = null,

    val priority: Int? = null,
) {
    @Serializable
    enum class Status {
        @SerialName("ok")
        Ok,

        @SerialName("error")
        Error;
    }

    fun getTransferDetails(
        amountRaw: Amount,
        amountEffective: Amount,
    ): TransferData? {
        val uri = paytoUri.trim().toUri()
        val transferAmount = (transferAmount
            ?: uri.getQueryParameter("amount")
                ?.let { Amount.fromJSONString(it) }
            ?: amountEffective).withSpec(currencySpecification)
        return if ("bitcoin".equals(uri.authority, true)) {
            val msg = uri.getQueryParameter("message").orEmpty()
            val reg = "\\b([A-Z0-9]{52})\\b".toRegex().find(msg)
            val reserve = reg?.value ?: uri.getQueryParameter("subject")!!
            val segwitAddresses =
                Bech32.generateFakeSegwitAddress(reserve, uri.pathSegments.first())
            TransferData.Bitcoin(
                account = uri.lastPathSegment!!,
                segwitAddresses = segwitAddresses,
                subject = reserve,
                amountRaw = amountRaw,
                amountEffective = amountEffective,
                transferAmount = transferAmount,
                withdrawalAccount = copy(paytoUri = uri.toString()),
            )
        } else if (uri.authority.equals("x-taler-bank", true)) {
            TransferData.Taler(
                account = uri.lastPathSegment!!,
                receiverName = uri.getQueryParameter("receiver-name"),
                subject = uri.getQueryParameter("message") ?: "Error: No message in URI",
                amountRaw = amountRaw,
                amountEffective = amountEffective,
                exchangeBaseUrl = uri.pathSegments[0] ?: return null,
                transferAmount = transferAmount,
                withdrawalAccount = copy(paytoUri = uri.toString()),
            )
        } else if (uri.authority.equals("iban", true)) {
            TransferData.IBAN(
                iban = uri.lastPathSegment!!,
                receiverName = uri.getQueryParameter("receiver-name"),
                receiverTown = uri.getQueryParameter("receiver-town"),
                receiverPostalCode = uri.getQueryParameter("receiver-postal-code"),
                subject = uri.getQueryParameter("message") ?: "Error: No message in URI",
                amountRaw = amountRaw,
                amountEffective = amountEffective,
                transferAmount = transferAmount,
                withdrawalAccount = copy(paytoUri = uri.toString()),
            )
        } else null
    }
}

@Serializable
sealed class AccountRestriction {
    @Serializable
    @SerialName("deny")
    data object DenyAllAccount: AccountRestriction()

    @Serializable
    @SerialName("regex")
    data class RegexAccount(
        // Regular expression that the payto://-URI of the
        // partner account must follow.  The regular expression
        // should follow posix-egrep, but without support for character
        // classes, GNU extensions, back-references or intervals. See
        // https://www.gnu.org/software/findutils/manual/html_node/find_html/posix_002degrep-regular-expression-syntax.html
        // for a description of the posix-egrep syntax. Applications
        // may support regexes with additional features, but exchanges
        // must not use such regexes.
        @SerialName("payto_regex")
        val paytoRegex: String,

        // Hint for a human to understand the restriction
        // (that is hopefully easier to comprehend than the regex itself).
        @SerialName("human_hint")
        val humanHint: String,

        // Map from IETF BCP 47 language tags to localized
        // human hints.
        @SerialName("human_hint_i18n")
        val humanHintI18n: Map<String, String>? = null,
    ): AccountRestriction()
}

@Serializable
@SerialName("payment")
class TransactionPayment(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val info: TransactionInfo,
    val contractTerms: ContractTerms? = null,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val posConfirmation: String? = null,
) : Transaction() {
    override val icon = R.drawable.transaction_payment
    override val detailPageNav = R.id.action_nav_transactions_detail_payment

    @Transient
    override val amountType = AmountType.Negative
    override fun getTitle(context: Context) = info.merchant.name
    override val generalTitleRes = R.string.payment_title
}

@Serializable
class TransactionInfo(
    val orderId: String,
    val merchant: Merchant,
    val summary: String,
    @SerialName("summary_i18n")
    val summaryI18n: Map<String, String>? = null,
    val products: List<ContractProduct> = emptyList(),
    val fulfillmentUrl: String? = null,
    /**
     * Message shown to the user after the payment is complete.
     */
    val fulfillmentMessage: String? = null,
    /**
     * Map from IETF BCP 47 language tags to localized fulfillment messages
     */
    val fulfillmentMessage_i18n: Map<String, String>? = null,
)

@Serializable
@SerialName("refund")
class TransactionRefund(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val refundedTransactionId: String,
    val paymentInfo: RefundPaymentInfo? = null,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
) : Transaction() {
    override val icon = R.drawable.transaction_refund
    override val detailPageNav = R.id.action_nav_transactions_detail_refund

    @Transient
    override val amountType = AmountType.Positive
    override fun getTitle(context: Context) = paymentInfo?.merchant?.name ?: context.getString(R.string.transaction_refund)

    override val generalTitleRes = R.string.refund_title
}

@Serializable
@SerialName("refresh")
class TransactionRefresh(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
) : Transaction() {
    override val icon = R.drawable.transaction_refresh
    override val detailPageNav = R.id.action_nav_transactions_detail_refresh

    @Transient
    override val amountType = AmountType.Negative
    override fun getTitle(context: Context): String {
        return context.getString(R.string.transaction_refresh)
    }

    override val generalTitleRes = R.string.transaction_refresh
}

@Serializable
@SerialName("deposit")
class TransactionDeposit(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val kycUrl: String? = null,
    val kycAuthTransferInfo: KycAuthTransferInfo? = null,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val targetPaytoUri: String,
    val depositGroupId: String,
) : Transaction() {
    override val icon = R.drawable.transaction_deposit
    override val detailPageNav = R.id.action_nav_transactions_detail_deposit

    @Transient
    override val amountType = AmountType.Negative
    override fun getTitle(context: Context): String {
        val uri = Uri.parse(targetPaytoUri)
        return uri.getQueryParameter("receiver-name")?.let { receiverName ->
            context.getString(R.string.transaction_deposit_to, receiverName)
        } ?: context.getString(R.string.transaction_deposit)
    }

    override val generalTitleRes = R.string.transaction_deposit
}

@Serializable
data class KycAuthTransferInfo(
    val debitPaytoUri: String,
    val accountPub: String,
    val creditPaytoUris: List<String>,
)

@Serializable
data class PeerInfoShort(
    val expiration: Timestamp? = null,
    val summary: String? = null,
)

/**
 * Debit because we paid someone's invoice.
 */
@Serializable
@SerialName("peer-pull-debit")
class TransactionPeerPullDebit(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val exchangeBaseUrl: String,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val info: PeerInfoShort,
) : Transaction() {
    override val icon = R.drawable.transaction_p2p_outgoing
    override val detailPageNav = R.id.nav_transactions_detail_peer

    @Transient
    override val amountType = AmountType.Negative
    override fun getTitle(context: Context): String {
        return if (txState.major == Done) {
            context.getString(R.string.transaction_peer_pull_debit)
        } else {
            context.getString(R.string.transaction_peer_pull_debit_pending)
        }
    }

    override val generalTitleRes = R.string.transaction_peer_pull_debit
}

/**
 * Credit because someone paid for an invoice we created.
 */
@Serializable
@SerialName("peer-pull-credit")
class TransactionPeerPullCredit(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val kycUrl: String? = null,
    val exchangeBaseUrl: String,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val info: PeerInfoShort,
    val talerUri: String,
    // val completed: Boolean, maybe
) : Transaction() {
    override val icon = R.drawable.transaction_p2p_incoming
    override val detailPageNav = R.id.nav_transactions_detail_peer

    override val amountType get() = AmountType.Positive
    override fun getTitle(context: Context): String {
        return context.getString(R.string.transaction_peer_pull_credit)
    }

    override val generalTitleRes = R.string.transaction_peer_pull_credit
}

/**
 * Debit because we sent money to someone.
 */
@Serializable
@SerialName("peer-push-debit")
class TransactionPeerPushDebit(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val exchangeBaseUrl: String,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val info: PeerInfoShort,
    val talerUri: String? = null,
    // val completed: Boolean, definitely
) : Transaction() {
    override val icon = R.drawable.transaction_p2p_outgoing
    override val detailPageNav = R.id.nav_transactions_detail_peer

    @Transient
    override val amountType = AmountType.Negative
    override fun getTitle(context: Context): String {
        return if (txState.major == Done) {
            context.getString(R.string.transaction_peer_push_debit)
        } else {
            context.getString(R.string.transaction_peer_push_debit_pending)
        }
    }

    override val generalTitleRes = R.string.payment_title
}

/**
 * We received money via a peer payment.
 */
@Serializable
@SerialName("peer-push-credit")
class TransactionPeerPushCredit(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    val kycUrl: String? = null,
    val exchangeBaseUrl: String,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val info: PeerInfoShort,
) : Transaction() {
    override val icon = R.drawable.transaction_p2p_incoming
    override val detailPageNav = R.id.nav_transactions_detail_peer

    @Transient
    override val amountType = AmountType.Positive
    override fun getTitle(context: Context): String {
        return if (txState.major == Done) {
            context.getString(R.string.transaction_peer_push_credit)
        } else {
            context.getString(R.string.transaction_peer_push_credit_pending)
        }
    }

    override val generalTitleRes = R.string.transaction_peer_push_credit
}

/**
 * A transaction to indicate financial loss due to denominations
 * that became unusable for deposits.
 */
@Serializable
@SerialName("denom-loss")
class TransactionDenomLoss(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val txState: TransactionState,
    override val txActions: List<TransactionAction>,
    override val error: TalerErrorInfo? = null,
    override val amountRaw: Amount,
    override val amountEffective: Amount,
    override val scopes: List<ScopeInfo>,
    val lossEventType: LossEventType,
): Transaction() {
    override val icon: Int = R.drawable.transaction_loss
    override val detailPageNav = R.id.nav_transactions_detail_loss

    @Transient
    override val amountType: AmountType = AmountType.Negative

    override fun getTitle(context: Context): String {
        return context.getString(R.string.transaction_denom_loss)
    }

    override val generalTitleRes: Int = R.string.transaction_denom_loss
}

@Serializable
enum class LossEventType {
    @SerialName("denom-expired")
    DenomExpired,

    @SerialName("denom-vanished")
    DenomVanished,

    @SerialName("denom-unoffered")
    DenomUnoffered
}

/**
 * This represents a transaction that we can not parse for some reason.
 */
class DummyTransaction(
    override val transactionId: String,
    override val timestamp: Timestamp,
    override val error: TalerErrorInfo,
) : Transaction() {
    override val txState: TransactionState = TransactionState(None)
    override val txActions: List<TransactionAction> = emptyList()
    override val amountRaw: Amount = Amount.zero("TESTKUDOS")
    override val amountEffective: Amount = Amount.zero("TESTKUDOS")
    override val icon: Int = R.drawable.transaction_dummy
    override val detailPageNav: Int = R.id.nav_transactions_detail_dummy
    override val amountType: AmountType = AmountType.Neutral
    override val generalTitleRes: Int = R.string.transaction_dummy_title
    override val scopes: List<ScopeInfo> = listOf(ScopeInfo.Exchange(
        currency = "TESTKUDOS",
        url = "exchange.test.taler.net",
    ))
    override fun getTitle(context: Context): String {
        return context.getString(R.string.transaction_dummy_title)
    }
}
