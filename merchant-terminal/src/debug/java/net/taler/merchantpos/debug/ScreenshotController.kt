package net.taler.merchantpos.debug

import android.content.Context
import android.content.Intent
import android.util.Base64
import net.taler.common.Amount
import net.taler.merchantlib.MerchantConfig
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.config.InitialOrderScreen
import net.taler.merchantpos.config.PosConfig
import net.taler.merchantpos.order.Order

object ScreenshotController {
    private const val EXTRA_SCENARIO = "taler_pos_screenshot_scenario"
    private const val FIXTURE_ASSET_DIR = "screenshot-products"
    private const val CURRENCY = "CHF"

    @Volatile
    private var activeScenario: Scenario? = null

    val isActive: Boolean
        get() = activeScenario != null

    fun prepareScenario(intent: Intent, model: MainViewModel): PosDestination? {
        val scenario = intent.getStringExtra(EXTRA_SCENARIO)
            ?.let(Scenario::fromValue)
            ?: run {
                activeScenario = null
                return null
            }
        activeScenario = scenario
        applyScenario(model, scenario)
        return scenario.destination
    }

    private fun applyScenario(model: MainViewModel, scenario: Scenario) {
        val fixture = buildFixture(model.getApplication())
        model.configManager.debugApplyFixture(
            posConfig = fixture.posConfig,
            merchantConfig = fixture.merchantConfig,
            currency = CURRENCY,
            initialOrderScreen = InitialOrderScreen.Inventory,
        )
        model.orderManager.debugSeedCurrentOrder(
            listOf("coffee", "croissant", "sandwich", "coffee"),
        )
        val currentOrderId = model.orderManager.currentOrderId.value ?: 0
        val currentOrder = model.orderManager.getOrder(currentOrderId).order.value
            ?: Order(
                id = currentOrderId,
                currency = CURRENCY,
                currencySpec = null,
                availableCategories = emptyMap(),
            )

        when (scenario) {
            Scenario.AmountEntry -> Unit
            Scenario.Order -> Unit
            Scenario.Payment -> {
                model.paymentManager.debugSetPayment(
                    net.taler.merchantpos.payment.Payment(
                        order = currentOrder,
                        summary = currentOrder.summary,
                        currency = CURRENCY,
                        orderId = "2026-ORD-1042",
                        talerPayUri = "taler://pay/example.invalid/2026-ORD-1042/ZXCVBNM123456",
                    ),
                )
            }
            Scenario.PaymentSuccess -> Unit
        }
    }

    private fun buildFixture(context: Context): FixtureData {
        val categories = listOf(
            Category(id = 1, name = "Drinks"),
            Category(id = 2, name = "Bakery"),
            Category(id = 3, name = "Lunch"),
        )
        val products = listOf(
            ConfigProduct(
                id = "coffee",
                productId = "coffee",
                productName = "House Coffee",
                description = "House Coffee",
                price = Amount.fromString(CURRENCY, "3.80"),
                categories = listOf(1),
                image = assetDataUri(context, "coffee.png"),
            ),
            ConfigProduct(
                id = "tea",
                productId = "tea",
                productName = "Herbal Tea",
                description = "Herbal Tea",
                price = Amount.fromString(CURRENCY, "3.40"),
                categories = listOf(1),
                image = assetDataUri(context, "tea.png"),
            ),
            ConfigProduct(
                id = "croissant",
                productId = "croissant",
                productName = "Butter Croissant",
                description = "Butter Croissant",
                price = Amount.fromString(CURRENCY, "2.90"),
                categories = listOf(2),
                image = assetDataUri(context, "croissant.png"),
            ),
            ConfigProduct(
                id = "sandwich",
                productId = "sandwich",
                productName = "Veggie Sandwich",
                description = "Veggie Sandwich",
                price = Amount.fromString(CURRENCY, "8.50"),
                categories = listOf(3),
                image = assetDataUri(context, "sandwich.png"),
            ),
        )
        return FixtureData(
            posConfig = PosConfig(categories = categories, products = products),
            merchantConfig = MerchantConfig(
                baseUrl = "https://merchant.example.invalid/instances/demo",
                apiKey = "",
            ),
        )
    }

    private fun assetDataUri(context: Context, fileName: String): String {
        val bytes = context.assets.open("$FIXTURE_ASSET_DIR/$fileName").use { it.readBytes() }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/png;base64,$encoded"
    }

    private data class FixtureData(
        val posConfig: PosConfig,
        val merchantConfig: MerchantConfig,
    )

    private enum class Scenario(
        val value: String,
        val destination: PosDestination,
    ) {
        AmountEntry("amount-entry", PosDestination.AmountEntry),
        Order("order", PosDestination.Order),
        Payment("payment", PosDestination.ProcessPayment),
        PaymentSuccess("payment-success", PosDestination.PaymentSuccess),
        ;

        companion object {
            fun fromValue(value: String): Scenario? = entries.firstOrNull { it.value == value }
        }
    }
}
