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

package net.taler.merchantpos.order

import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.lib.android.base64Bitmap
import net.taler.merchantpos.MainActivity
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.config.Category
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.showPosError
import kotlinx.coroutines.delay
import net.taler.merchantpos.order.RestartState.DISABLED
import androidx.compose.foundation.shape.RoundedCornerShape

class OrderFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val paymentManager by lazy { viewModel.paymentManager }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OrderRoute(
                viewModel = viewModel,
                onNavigate = { destination ->
                    (requireActivity() as MainActivity).navigateTo(destination)
                },
                onShowMessage = { message ->
                    requireActivity().showPosError(message)
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.configManager.config.isValid()) {
            (requireActivity() as MainActivity).navigateTo(PosDestination.Config)
        } else if (viewModel.configManager.currency == null) {
            (requireActivity() as MainActivity).navigateTo(PosDestination.ConfigFetcher)
        }
    }
}

@Composable
private fun OrderRoute(
    viewModel: MainViewModel,
    onNavigate: (PosDestination) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val orderManager = remember { viewModel.orderManager }
    val paymentManager = remember { viewModel.paymentManager }
    val currentOrderId by orderManager.currentOrderId.observeAsState()
    val categories by orderManager.categories.observeAsState(emptyList())
    val products by orderManager.products.observeAsState(emptyList())
    val currency = viewModel.configManager.currency
    val currencySpec = viewModel.configManager.currencySpec

    val orderId = currentOrderId ?: return
    val liveOrder = remember(orderId) { orderManager.getOrder(orderId) }
    val order by liveOrder.order.observeAsState()
    val orderTotal by liveOrder.orderTotal.observeAsState(
        Amount.zero(currency ?: "").withSpec(currencySpec),
    )
    val restartState by liveOrder.restartState.observeAsState(DISABLED)
    val modifyAllowed by liveOrder.modifyOrderAllowed.observeAsState(false)
    val increaseAllowed by liveOrder.increaseOrderAllowed.observeAsState(false)
    val hasNextOrder by orderManager.hasNextOrder(orderId).observeAsState(false)
    var selectedProductKey by rememberSaveable(orderId) { mutableStateOf(liveOrder.selectedProductKey) }
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(categories.firstOrNull { it.selected }?.id)
    }
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    val reloadingText = stringResource(R.string.toast_reloading)

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            viewModel.configManager.refreshInventory()
        }
    }

    LaunchedEffect(order?.products, liveOrder.lastAddedProduct?.id) {
        val productsInOrder = order?.products.orEmpty()
        val selected = selectedProductKey?.let { key -> productsInOrder.find { it.id == key } }
        val nextSelection = liveOrder.lastAddedProduct?.takeIf { added ->
            productsInOrder.any { it.id == added.id }
        } ?: selected ?: productsInOrder.lastOrNull()
        selectedProductKey = nextSelection?.id
        liveOrder.selectOrderLine(nextSelection)
    }

    LaunchedEffect(categories.map { it.id }) {
        if (selectedCategoryId == null) {
            selectedCategoryId = categories.firstOrNull { it.selected }?.id
        } else if (categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = categories.firstOrNull { it.selected }?.id
        }
    }

    if (showCustomDialog && currency != null) {
        CustomProductDialog(
            currency = currency,
            currencySpec = currencySpec,
            onDismiss = { showCustomDialog = false },
            onAdd = { description, amount ->
                val product = ConfigProduct(
                    description = description,
                    price = amount,
                    categories = listOf(Int.MIN_VALUE),
                )
                orderManager.addProduct(orderId, product)
                showCustomDialog = false
            },
        )
    }

    PosTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            val isTabletLayout = LocalConfiguration.current.smallestScreenWidthDp >= 720
            if (isTabletLayout) {
                TabletOrderScreen(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    products = products,
                    order = order,
                    increaseAllowed = increaseAllowed,
                    modifyAllowed = modifyAllowed,
                    orderTotal = orderTotal.toString(),
                    selectedProductKey = selectedProductKey,
                    onCategorySelected = { category ->
                        selectedCategoryId = category.id
                        orderManager.setCurrentCategory(category)
                    },
                    onProductSelected = { product ->
                        orderManager.addProduct(orderId, product)
                    },
                    onSelectProduct = {
                        selectedProductKey = it?.id
                        liveOrder.selectOrderLine(it)
                    },
                    onIncrease = { liveOrder.increaseSelectedOrderLine() },
                    onDecrease = { liveOrder.decreaseSelectedOrderLine() },
                    onAddCustom = { showCustomDialog = true },
                    onComplete = {
                        val currentOrder = order ?: return@TabletOrderScreen
                        paymentManager.createPayment(currentOrder)
                        onNavigate(PosDestination.ProcessPayment)
                    },
                )
            } else {
                PhoneOrderScreen(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    products = products,
                    order = order,
                    increaseAllowed = increaseAllowed,
                    modifyAllowed = modifyAllowed,
                    orderTotal = orderTotal.toString(),
                    selectedProductKey = selectedProductKey,
                    onCategorySelected = { category ->
                        selectedCategoryId = category.id
                        orderManager.setCurrentCategory(category)
                    },
                    onProductSelected = { product ->
                        orderManager.addProduct(orderId, product)
                    },
                    onSelectProduct = {
                        selectedProductKey = it?.id
                        liveOrder.selectOrderLine(it)
                    },
                    onIncrease = { liveOrder.increaseSelectedOrderLine() },
                    onDecrease = { liveOrder.decreaseSelectedOrderLine() },
                    onAddCustom = { showCustomDialog = true },
                    onComplete = {
                        val currentOrder = order ?: return@PhoneOrderScreen
                        paymentManager.createPayment(currentOrder)
                        onNavigate(PosDestination.ProcessPayment)
                    },
                )
            }
        }
    }
}

@Composable
private fun TabletOrderScreen(
    categories: List<Category>,
    selectedCategoryId: Int?,
    products: List<ConfigProduct>,
    order: Order?,
    increaseAllowed: Boolean,
    modifyAllowed: Boolean,
    orderTotal: String,
    selectedProductKey: String?,
    onCategorySelected: (Category) -> Unit,
    onProductSelected: (ConfigProduct) -> Unit,
    onSelectProduct: (ConfigProduct?) -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddCustom: () -> Unit,
    onComplete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        CategoriesPane(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            modifier = Modifier.weight(0.25f),
            compact = false,
            onCategorySelected = onCategorySelected,
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        ProductsPane(
            products = products,
            modifier = Modifier.weight(0.50f),
            compact = false,
            onProductSelected = onProductSelected,
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        OrderColumnPane(
            order = order,
            increaseAllowed = increaseAllowed,
            modifyAllowed = modifyAllowed,
            orderTotal = orderTotal,
            orderIsEmpty = order?.total?.isZero() != false,
            selectedProductKey = selectedProductKey,
            modifier = Modifier.weight(0.25f),
            compact = false,
            onSelectProduct = onSelectProduct,
            onIncrease = onIncrease,
            onDecrease = onDecrease,
            onAddCustom = onAddCustom,
            onComplete = onComplete,
        )
    }
}

@Composable
private fun PhoneOrderScreen(
    categories: List<Category>,
    selectedCategoryId: Int?,
    products: List<ConfigProduct>,
    order: Order?,
    increaseAllowed: Boolean,
    modifyAllowed: Boolean,
    orderTotal: String,
    selectedProductKey: String?,
    onCategorySelected: (Category) -> Unit,
    onProductSelected: (ConfigProduct) -> Unit,
    onSelectProduct: (ConfigProduct?) -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddCustom: () -> Unit,
    onComplete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        CategoriesPane(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            modifier = Modifier.weight(0.22f),
            compact = true,
            onCategorySelected = onCategorySelected,
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        ProductsPane(
            products = products,
            modifier = Modifier.weight(0.43f),
            compact = true,
            onProductSelected = onProductSelected,
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        OrderColumnPane(
            order = order,
            increaseAllowed = increaseAllowed,
            modifyAllowed = modifyAllowed,
            orderTotal = orderTotal,
            orderIsEmpty = order?.total?.isZero() != false,
            selectedProductKey = selectedProductKey,
            modifier = Modifier.weight(0.35f),
            compact = true,
            onSelectProduct = onSelectProduct,
            onIncrease = onIncrease,
            onDecrease = onDecrease,
            onAddCustom = onAddCustom,
            onComplete = onComplete,
        )
    }
}

@Composable
private fun CategoriesPane(
    categories: List<Category>,
    selectedCategoryId: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onCategorySelected: (Category) -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (compact) 4.dp else 8.dp,
                    top = if (compact) 8.dp else 12.dp,
                    end = if (compact) 4.dp else 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryButton(
                    text = category.localizedName,
                    selected = category.id == selectedCategoryId,
                    compact = compact,
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryButton(
    text: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 8.dp else 16.dp,
                    vertical = if (compact) 8.dp else 12.dp,
                ),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProductsPane(
    products: List<ConfigProduct>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onProductSelected: (ConfigProduct) -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (compact) 4.dp else 8.dp,
                    top = if (compact) 8.dp else 12.dp,
                    end = if (compact) 4.dp else 8.dp,
                ),
        ) {
            val spacing = if (compact) 6.dp else 8.dp
            val minTileWidth = if (compact) 96.dp else 150.dp
            val columns = maxOf(1, ((maxWidth + spacing) / (minTileWidth + spacing)).toInt())
            val productRows = remember(products, columns) { products.chunked(columns) }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                productRows.forEach { rowProducts ->
                    val rowHasImage = rowProducts.any { !it.image.isNullOrBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowProducts.forEach { product ->
                            ProductCard(
                                product = product,
                                rowHasImage = rowHasImage,
                                compact = compact,
                                onClick = { onProductSelected(product) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        repeat(columns - rowProducts.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ConfigProduct,
    rowHasImage: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardContainerColor = if (product.availableToSell) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val cardBorderColor = if (product.availableToSell) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clickable(enabled = product.availableToSell, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor,
        ),
        border = BorderStroke(1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        ) {
            val productBitmap = product.image?.base64Bitmap
            val imageSize = if (compact) 40.dp else 64.dp
            if (productBitmap != null) {
                Image(
                    bitmap = productBitmap.asImageBitmap(),
                    contentDescription = product.displayName,
                    modifier = Modifier
                        .size(imageSize)
                        .align(Alignment.CenterHorizontally),
                )
            } else if (rowHasImage) {
                Spacer(
                    modifier = Modifier
                        .height(imageSize)
                        .fillMaxWidth(),
                )
            }
            Text(
                text = product.displayName,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
            product.displayDescription?.let {
                Text(
                    text = it,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = product.displayPrice,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!product.availableToSell) {
                Text(
                    text = when {
                        product.currencyMismatch -> stringResource(R.string.product_wrong_currency)
                        product.remainingStock == 0 -> stringResource(R.string.product_out_of_stock)
                        else -> stringResource(R.string.product_unavailable)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OrderPane(
    order: Order?,
    selectedProductKey: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onSelectProduct: (ConfigProduct?) -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(order?.products.orEmpty(), key = { it.id }) { product ->
                    val selected = selectedProductKey == product.id
                    OrderRow(
                        product = product,
                        selected = selected,
                        compact = compact,
                        onClick = { onSelectProduct(product) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun OrderRow(
    product: ConfigProduct,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val rowPadding = if (compact) 6.dp else 8.dp
    val imageSize = if (compact) 24.dp else 32.dp
    val countWidth = if (compact) 18.dp else 24.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .padding(rowPadding),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = product.quantity.toString(),
            modifier = Modifier.width(countWidth),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        )
        product.image?.base64Bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = product.displayName,
                modifier = Modifier.size(imageSize),
            )
        } ?: Spacer(Modifier.width(imageSize))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                product.displayName,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
            product.displayDescription?.let {
                Text(it, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            product.totalPrice.toString(),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OrderActionBar(
    modifyAllowed: Boolean,
    increaseAllowed: Boolean,
    orderTotal: String,
    orderIsEmpty: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddCustom: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Button(onClick = onIncrease, enabled = increaseAllowed, colors = orderControlButtonColors()) {
                Text("+1", style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onDecrease, enabled = modifyAllowed, colors = orderControlButtonColors()) {
                Text("-1", style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAddCustom, colors = orderControlButtonColors()) {
                Icon(
                    painter = painterResource(R.drawable.ic_dialpad),
                    contentDescription = stringResource(R.string.order_custom),
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                )
            }
        }
        Button(
            onClick = onComplete,
            enabled = !orderIsEmpty,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 72.dp else 96.dp),
            colors = completeButtonColors(),
        ) {
            Text(
                if (orderIsEmpty) {
                    stringResource(R.string.order_complete)
                } else {
                    stringResource(R.string.order_complete_with_amount, orderTotal)
                },
                style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OrderColumnPane(
    order: Order?,
    increaseAllowed: Boolean,
    modifyAllowed: Boolean,
    orderTotal: String,
    orderIsEmpty: Boolean,
    selectedProductKey: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onSelectProduct: (ConfigProduct?) -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddCustom: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OrderPane(
            order = order,
            selectedProductKey = selectedProductKey,
            modifier = Modifier.weight(1f),
            compact = compact,
            onSelectProduct = onSelectProduct,
        )
        OrderActionBar(
            modifyAllowed = modifyAllowed,
            increaseAllowed = increaseAllowed,
            orderTotal = orderTotal,
            orderIsEmpty = orderIsEmpty,
            modifier = Modifier.padding(start = 12.dp),
            compact = compact,
            onIncrease = onIncrease,
            onDecrease = onDecrease,
            onAddCustom = onAddCustom,
            onComplete = onComplete,
        )
    }
}

@Composable
private fun orderControlButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun completeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun CustomProductDialog(
    currency: String,
    currencySpec: net.taler.common.CurrencySpecification?,
    onDismiss: () -> Unit,
    onAdd: (String, Amount) -> Unit,
) {
    val defaultDescription = stringResource(R.string.order_custom_product_default)
    val invalidAmountText = stringResource(R.string.refund_error_invalid_amount)
    var description by rememberSaveable { mutableStateOf(defaultDescription) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.order_custom_product)) },
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text(currency) },
                    supportingText = errorText?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = try {
                    Amount.fromString(currency, amountText).withSpec(currencySpec)
                } catch (_: AmountParserException) {
                    errorText = invalidAmountText
                    return@Button
                }
                onAdd(description, amount)
            }) {
                Text(stringResource(R.string.order_custom_add_button))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.refund_abort))
            }
        },
    )
}
