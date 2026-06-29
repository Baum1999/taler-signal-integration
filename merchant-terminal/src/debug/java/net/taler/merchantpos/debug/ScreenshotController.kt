package net.taler.merchantpos.debug

import android.content.Context
import android.util.Base64
import net.taler.common.Amount
import net.taler.common.Tax
import net.taler.merchantlib.MerchantConfig
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.config.PosConfig

object ScreenshotController {

    const val isActive: Boolean = false

    private const val FIXTURE_ASSET_DIR = "screenshot-products"
    private const val CURRENCY = "CHF"

    fun buildFixture(context: Context): FixtureData {
        val categories = listOf(
            Category(id = 1, name = "Drinks"),
            Category(id = 2, name = "Bakery"),
            Category(id = 3, name = "Lunch"),
            Category(id = 4, name = "Snacks"),
            Category(id = 5, name = "Specials"),
        )
        val products = listOf(
            ConfigProduct(
                id = "coffee",
                productId = "coffee",
                productName = "House Coffee",
                description = "Freshly brewed fair-trade blend",
                price = Amount.fromString(CURRENCY, "3.80"),
                categories = listOf(1),
                image = assetDataUri(context, "coffee.png"),
                taxes = setOf(vat("0.29")),
                totalStock = 12,
                totalSold = 4,
            ),
            ConfigProduct(
                id = "tea",
                productId = "tea",
                productName = "Herbal Tea",
                description = "Mint and lemon verbena infusion",
                price = Amount.fromString(CURRENCY, "3.40"),
                categories = listOf(1),
                image = assetDataUri(context, "tea.png"),
                taxes = setOf(vat("0.26")),
                totalStock = 18,
                totalSold = 6,
            ),
            ConfigProduct(
                id = "juice",
                productId = "juice",
                productName = "Apple Juice",
                description = "Pressed Swiss apples, 30 cl",
                price = Amount.fromString(CURRENCY, "4.20"),
                categories = listOf(1),
                image = assetDataUri(context, "juice.png"),
                taxes = setOf(vat("0.32")),
                totalStock = 9,
                totalSold = 1,
            ),
            ConfigProduct(
                id = "espresso",
                productId = "espresso",
                productName = "Espresso",
                description = "Single origin, short pull",
                price = Amount.fromString(CURRENCY, "3.20"),
                categories = listOf(1),
                image = assetDataUri(context, "espresso.png"),
                taxes = setOf(vat("0.24")),
                totalStock = 20,
                totalSold = 8,
            ),
            ConfigProduct(
                id = "lemonade",
                productId = "lemonade",
                productName = "Lemonade",
                description = "Sparkling lemon and ginger",
                price = Amount.fromString(CURRENCY, "4.40"),
                categories = listOf(1, 5),
                image = assetDataUri(context, "lemonade.png"),
                taxes = setOf(vat("0.33")),
                totalStock = 14,
                totalSold = 4,
            ),
            ConfigProduct(
                id = "croissant",
                productId = "croissant",
                productName = "Butter Croissant",
                description = "All-butter pastry baked this morning",
                price = Amount.fromString(CURRENCY, "2.90"),
                categories = listOf(2),
                image = assetDataUri(context, "croissant.png"),
                taxes = setOf(vat("0.22")),
                totalStock = 16,
                totalSold = 7,
            ),
            ConfigProduct(
                id = "muffin",
                productId = "muffin",
                productName = "Blueberry Muffin",
                description = "With lemon crumble topping",
                price = Amount.fromString(CURRENCY, "4.60"),
                categories = listOf(2, 4),
                image = assetDataUri(context, "muffin.png"),
                taxes = setOf(vat("0.35")),
                totalStock = 10,
                totalSold = 3,
            ),
            ConfigProduct(
                id = "bagel",
                productId = "bagel",
                productName = "Sesame Bagel",
                description = "Toasted with cream cheese",
                price = Amount.fromString(CURRENCY, "5.20"),
                categories = listOf(2),
                image = assetDataUri(context, "bagel.png"),
                taxes = setOf(vat("0.39")),
                totalStock = 9,
                totalSold = 2,
            ),
            ConfigProduct(
                id = "sandwich",
                productId = "sandwich",
                productName = "Veggie Sandwich",
                description = "Grilled vegetables, hummus, seeded roll",
                price = Amount.fromString(CURRENCY, "8.50"),
                categories = listOf(3),
                image = assetDataUri(context, "sandwich.png"),
                taxes = setOf(vat("0.64")),
                totalStock = 8,
                totalSold = 2,
            ),
            ConfigProduct(
                id = "wrap",
                productId = "wrap",
                productName = "Chicken Wrap",
                description = "Herbs, salad, yogurt dressing",
                price = Amount.fromString(CURRENCY, "8.90"),
                categories = listOf(3),
                image = assetDataUri(context, "wrap.png"),
                taxes = setOf(vat("0.67")),
                totalStock = 7,
                totalSold = 3,
            ),
            ConfigProduct(
                id = "salad",
                productId = "salad",
                productName = "Market Salad",
                description = "Greens, grains, roasted seeds",
                price = Amount.fromString(CURRENCY, "9.80"),
                categories = listOf(3, 5),
                image = assetDataUri(context, "salad.png"),
                taxes = setOf(vat("0.74")),
                totalStock = 6,
                totalSold = 1,
            ),
            ConfigProduct(
                id = "soup",
                productId = "soup",
                productName = "Pumpkin Soup",
                description = "Seasonal special, served warm",
                price = Amount.fromString(CURRENCY, "7.20"),
                categories = listOf(3, 5),
                image = assetDataUri(context, "soup.png"),
                taxes = setOf(vat("0.54")),
                totalStock = 5,
                totalSold = 5,
            ),
            ConfigProduct(
                id = "quiche",
                productId = "quiche",
                productName = "Vegetable Quiche",
                description = "Daily special with garden vegetables",
                price = Amount.fromString(CURRENCY, "7.90"),
                categories = listOf(3, 5),
                image = assetDataUri(context, "quiche.png"),
                taxes = setOf(vat("0.60")),
                totalStock = 6,
                totalSold = 2,
            ),
            ConfigProduct(
                id = "granola",
                productId = "granola",
                productName = "Granola Cup",
                description = "Yogurt, berries, toasted oats",
                price = Amount.fromString(CURRENCY, "5.70"),
                categories = listOf(4, 5),
                image = assetDataUri(context, "granola.png"),
                taxes = setOf(vat("0.43")),
                totalStock = 7,
                totalSold = 2,
            ),
            ConfigProduct(
                id = "chips",
                productId = "chips",
                productName = "Lentil Chips",
                description = "Sea salt snack bag",
                price = Amount.fromString(CURRENCY, "3.60"),
                categories = listOf(4),
                image = assetDataUri(context, "chips.png"),
                taxes = setOf(vat("0.27")),
                totalStock = 15,
                totalSold = 5,
            ),
            ConfigProduct(
                id = "fruit",
                productId = "fruit",
                productName = "Fruit Cup",
                description = "Seasonal sliced fruit",
                price = Amount.fromString(CURRENCY, "4.90"),
                categories = listOf(4, 5),
                image = assetDataUri(context, "fruit.png"),
                taxes = setOf(vat("0.37")),
                totalStock = 8,
                totalSold = 2,
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

    private fun vat(amount: String) = Tax(
        name = "VAT",
        tax = Amount.fromString(CURRENCY, amount),
    )

    data class FixtureData(
        val posConfig: PosConfig,
        val merchantConfig: MerchantConfig,
    )
}
