package net.taler.merchantpos.debug

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.taler.common.Amount
import net.taler.common.Challenge
import net.taler.common.TanChannel
import net.taler.common.Timestamp
import net.taler.merchantlib.OrderHistoryEntry
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.config.InitialOrderScreen
import net.taler.merchantpos.payment.Payment
import net.taler.merchantpos.refund.RefundResult

class ScreenshotActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SCREENSHOT_READY"
        private const val EXTRA_SCENARIO = "screenshot_scenario"
        private const val CURRENCY = "CHF"
    }

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,
        )

        val scenario = intent.getStringExtra(EXTRA_SCENARIO) ?: return

        val fixture = ScreenshotController.buildFixture(this)
        model.configManager.debugApplyFixture(
            posConfig = fixture.posConfig,
            merchantConfig = fixture.merchantConfig,
            currency = CURRENCY,
            initialOrderScreen = InitialOrderScreen.Inventory,
        )

        setContent {
            PosTheme(darkTheme = false) {
                when (scenario) {
                    "login" -> WithSettingsTopBar { LoginScreen() }
                    "mfa-select" -> Box(modifier = Modifier.fillMaxSize()) {
                        WithSettingsTopBar { LoginScreen() }
                        MfaOverlay { MfaSelectContent() }
                    }
                    "mfa-code" -> Box(modifier = Modifier.fillMaxSize()) {
                        WithSettingsTopBar { LoginScreen() }
                        MfaOverlay { MfaCodeContent() }
                    }
                    "amount-entry" -> WithTopBar(stringResource(R.string.menu_amount_entry)) {
                        AmountEntryScreenshot()
                    }
                    "order" -> WithOrderTopBar {
                        OrderScreenshot(showCustomDialog = false)
                    }
                    "order-custom" -> WithOrderTopBar {
                        OrderScreenshot(showCustomDialog = true)
                    }
                    "order-custom-added" -> WithOrderTopBar {
                        OrderScreenshot(showCustomDialog = false)
                    }
                    "payment" -> WithTopBar(stringResource(R.string.payment_process_label)) {
                        PaymentScreenshot()
                    }
                    "payment-success" -> WithTopBar(stringResource(R.string.payment_received)) {
                        PaymentSuccessScreenshot()
                    }
                    "history" -> WithHistoryTopBar {
                        HistoryScreenshot()
                    }
                    "refund" -> WithTopBar(stringResource(R.string.history_refund)) {
                        RefundScreenshot()
                    }
                    "refund-qr" -> WithTopBar(stringResource(R.string.history_refund)) {
                        RefundQrScreenshot()
                    }
                    "navigation" -> NavigationDrawerScreenshot()
                }
            }
        }

        when (scenario) {
            "order", "order-custom", "order-custom-added", "navigation" -> seedOrder(scenario)
            "payment" -> seedPayment()
            "history" -> seedHistory()
            "refund", "refund-qr" -> seedRefund(scenario)
        }

        signalReady(scenario)
    }

    private fun signalReady(scenario: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            Log.i(TAG, scenario)
        }, 300)
    }

    private fun seedOrder(scenario: String) {
        model.orderManager.debugSeedCurrentOrder(
            listOf(
                "lemonade", "lemonade", "lemonade", "lemonade", "lemonade",
                "wrap", "wrap",
                "chips",
                "salad", "salad",
            ),
        )
        if (scenario == "order-custom-added") {
            val orderId = model.orderManager.currentOrderId.value ?: 0
            val customProduct = net.taler.merchantpos.config.ConfigProduct(
                description = "Tip",
                price = Amount.fromString(CURRENCY, "2.73"),
                categories = listOf(Int.MIN_VALUE),
            )
            model.orderManager.addProduct(orderId, customProduct)
        }
    }

    private fun seedPayment() {
        val currentOrderId = model.orderManager.currentOrderId.value ?: 0
        val paymentProduct = net.taler.merchantpos.config.ConfigProduct(
            description = "Order total",
            price = Amount.fromString(CURRENCY, "123.45"),
            categories = listOf(Int.MIN_VALUE),
        )
        model.orderManager.addProduct(currentOrderId, paymentProduct)
        val currentOrder = model.orderManager.getOrder(currentOrderId).order.value
            ?: net.taler.merchantpos.order.Order(
                id = currentOrderId,
                currency = CURRENCY,
                currencySpec = null,
                availableCategories = emptyMap(),
            )
        model.paymentManager.debugSetPayment(
            Payment(
                order = currentOrder,
                summary = currentOrder.summary,
                currency = CURRENCY,
                orderId = "2026-ORD-1042",
                talerPayUri = "taler://pay/example.invalid/2026-ORD-1042/ZXCVBNM123456",
            ),
        )
    }

    private fun seedHistory() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        model.historyManager.debugSetHistory(
            listOf(
                OrderHistoryEntry(
                    orderId = "2026-ORD-1048",
                    rowId = 48,
                    timestamp = Timestamp.fromMillis(now - 1 * hour),
                    amount = Amount.fromString(CURRENCY, "22.00"),
                    summary = "5x Lemonade, 2x Chicken Wrap",
                    paid = false,
                    refundable = false,
                ),
                OrderHistoryEntry(
                    orderId = "2026-ORD-1047",
                    rowId = 47,
                    timestamp = Timestamp.fromMillis(now - 2 * hour),
                    amount = Amount.fromString(CURRENCY, "18.30"),
                    summary = "2x House Coffee, Veggie Sandwich",
                    paid = true,
                    refundable = true,
                ),
                OrderHistoryEntry(
                    orderId = "2026-ORD-1045",
                    rowId = 45,
                    timestamp = Timestamp.fromMillis(now - 5 * hour),
                    amount = Amount.fromString(CURRENCY, "9.80"),
                    summary = "Market Salad",
                    paid = true,
                    refundable = true,
                    refunded = true,
                    refundAmount = Amount.fromString(CURRENCY, "9.80"),
                ),
                OrderHistoryEntry(
                    orderId = "2026-ORD-1044",
                    rowId = 44,
                    timestamp = Timestamp.fromMillis(now - 7 * hour),
                    amount = Amount.fromString(CURRENCY, "14.60"),
                    summary = "Blueberry Muffin, Granola Cup, Espresso",
                    paid = true,
                    refundable = true,
                    refunded = true,
                    refundAmount = Amount.fromString(CURRENCY, "4.60"),
                ),
                OrderHistoryEntry(
                    orderId = "2026-ORD-1042",
                    rowId = 42,
                    timestamp = Timestamp.fromMillis(now - 26 * hour),
                    amount = Amount.fromString(CURRENCY, "123.45"),
                    summary = "Catering order",
                    paid = true,
                    refundable = true,
                ),
                OrderHistoryEntry(
                    orderId = "2026-ORD-1040",
                    rowId = 40,
                    timestamp = Timestamp.fromMillis(now - 50 * hour),
                    amount = Amount.fromString(CURRENCY, "7.20"),
                    summary = "Pumpkin Soup",
                    paid = true,
                    refundable = false,
                ),
            ),
        )
    }

    private fun seedRefund(scenario: String) {
        val refundItem = OrderHistoryEntry(
            orderId = "2026-ORD-1047",
            rowId = 47,
            timestamp = Timestamp.now(),
            amount = Amount.fromString(CURRENCY, "18.30"),
            summary = "2x House Coffee, Veggie Sandwich",
            paid = true,
            refundable = true,
        )
        if (scenario == "refund") {
            model.refundManager.startRefund(refundItem)
        } else {
            model.refundManager.debugSetRefundResult(
                RefundResult.Success(
                    refundUri = "taler://refund/example.invalid/2026-ORD-1047/REFUND-ABC123",
                    item = refundItem,
                    amount = Amount.fromString(CURRENCY, "18.30"),
                    reason = "Wrong order",
                ),
            )
        }
    }

    // ── Top bar scaffolds ───────────────────────────────────────────────

    @Composable
    private fun WithTopBar(title: String, content: @Composable () -> Unit) {
        Scaffold(
            topBar = {
                Surface(shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content()
            }
        }
    }

    @Composable
    private fun WithOrderTopBar(content: @Composable () -> Unit) {
        Scaffold(
            topBar = {
                Surface(shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                        Text(
                            text = stringResource(R.string.menu_order),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TopBarButton(stringResource(R.string.order_restart))
                            TopBarButton(stringResource(R.string.order_previous))
                            TopBarButton(stringResource(R.string.order_next))
                            TopBarButton(stringResource(R.string.menu_reload))
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content()
            }
        }
    }

    @Composable
    private fun WithHistoryTopBar(content: @Composable () -> Unit) {
        Scaffold(
            topBar = {
                Surface(shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                        Text(
                            text = stringResource(R.string.menu_history),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TopBarButton(stringResource(R.string.history_refresh))
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content()
            }
        }
    }

    @Composable
    private fun WithSettingsTopBar(content: @Composable () -> Unit) {
        Scaffold(
            topBar = {
                Surface(shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.config_label),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content()
            }
        }
    }

    @Composable
    private fun TopBarButton(text: String, enabled: Boolean = true) {
        Button(
            onClick = {},
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Text(text)
        }
    }

    // ── Screenshot composables ──────────────────────────────────────────

    @Composable
    private fun LoginScreen() {
        net.taler.merchantpos.config.ConfigScreenContent(
            merchantUrl = "backend.demo.taler.net",
            username = "sandbox",
            token = "sandbox",
            passwordVisible = true,
        )
    }

    @Composable
    private fun MfaSelectContent() {
        Text(
            text = stringResource(net.taler.common.R.string.mfa_choose_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fixtureChallenges.forEach { challenge ->
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${challenge.tanChannel}: ${challenge.tanInfo}")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = {}) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }

    @Composable
    private fun MfaCodeContent() {
        Text(
            text = stringResource(net.taler.common.R.string.mfa_challenge_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(
                net.taler.common.R.string.mfa_challenge_message,
                fixtureChallenges.first().tanChannel.name,
                fixtureChallenges.first().tanInfo,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MfaCodeDisplay(code = "77912082")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = {}) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(onClick = {}) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }

    @Composable
    private fun AmountEntryScreenshot() {
        net.taler.merchantpos.amount.AmountEntryScreenContent(
            amountText = "123.45",
            selectedCurrency = CURRENCY,
            currencyOptions = listOf(CURRENCY),
            chargeEnabled = true,
        )
    }

    @Composable
    private fun OrderScreenshot(showCustomDialog: Boolean) {
        net.taler.merchantpos.order.OrderScreenContent(
            viewModel = model,
            showCustomDialog = showCustomDialog,
            customDescription = if (showCustomDialog) "Tip" else null,
            customAmount = if (showCustomDialog) "2.73" else null,
        )
    }

    @Composable
    private fun PaymentScreenshot() {
        val payment = model.paymentManager.payment.value ?: return
        net.taler.merchantpos.payment.PaymentScreenContent(payment = payment)
    }

    @Composable
    private fun PaymentSuccessScreenshot() {
        net.taler.merchantpos.payment.PaymentSuccessScreenContent()
    }

    @Composable
    private fun HistoryScreenshot() {
        net.taler.merchantpos.history.HistoryScreenContent(
            items = (model.historyManager.items.value
                as? net.taler.merchantpos.history.HistoryResult.Success)?.items.orEmpty(),
        )
    }

    @Composable
    private fun RefundScreenshot() {
        val item = model.refundManager.toBeRefunded ?: return
        net.taler.merchantpos.refund.RefundScreenContent(
            item = item,
            currencySpec = model.configManager.currencySpec,
            initialReason = "Wrong order",
        )
    }

    @Composable
    private fun RefundQrScreenshot() {
        val result = model.refundManager.refundResult.value as? RefundResult.Success ?: return
        net.taler.merchantpos.refund.RefundQrScreenContent(result = result)
    }

    @Composable
    private fun NavigationDrawerScreenshot() {
        Box(modifier = Modifier.fillMaxSize()) {
            WithOrderTopBar {
                OrderScreenshot(showCustomDialog = false)
            }
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
            )
            // Drawer sheet
            androidx.compose.material3.ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_talerpos_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    drawerItems.forEach { (destination, iconResId, labelResId) ->
                        androidx.compose.material3.NavigationDrawerItem(
                            icon = {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(iconResId),
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(labelResId),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            selected = destination == "order",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(),
                        )
                    }
                }
            }
        }
    }

    private data class DrawerItem(
        val route: String,
        val iconResId: Int,
        val labelResId: Int,
    )

    private val drawerItems = listOf(
        DrawerItem("order", R.drawable.ic_move_money_24dp, R.string.menu_order),
        DrawerItem("amount-entry", R.drawable.ic_dialpad, R.string.menu_amount_entry),
        DrawerItem("history", R.drawable.ic_history_black_24dp, R.string.menu_history),
        DrawerItem("settings", R.drawable.ic_menu_manage, R.string.menu_settings),
    )

    // ── MFA visual helpers ──────────────────────────────────────────────

    @Composable
    private fun MfaOverlay(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun MfaCodeDisplay(code: String) {
        val interactionSource = remember { MutableInteractionSource() }
        BasicTextField(
            value = code,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            readOnly = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            interactionSource = interactionSource,
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(8) { index ->
                        if (index == 4) {
                            Text(
                                text = "-",
                                modifier = Modifier.padding(horizontal = 3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        MfaDigitBox(digit = code.getOrNull(index))
                    }
                }
            },
        )
    }

    @Composable
    private fun MfaDigitBox(digit: Char?) {
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .size(width = 30.dp, height = 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {}
            Text(
                text = digit?.toString().orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    private val fixtureChallenges = listOf(
        Challenge(
            challengeId = "CHALL-001",
            tanChannel = TanChannel.EMAIL,
            tanInfo = "s****x@demo.taler.net",
        ),
        Challenge(
            challengeId = "CHALL-002",
            tanChannel = TanChannel.SMS,
            tanInfo = "+41 ** *** **42",
        ),
    )
}
