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

package net.taler.wallet.balances

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.taler.common.Amount
import net.taler.common.Timestamp
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.balances.ScopeInfo.Auditor
import net.taler.wallet.balances.ScopeInfo.Exchange
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.ActionButton
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.Material3MenuGroup
import net.taler.wallet.compose.Material3MenuItemData
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.cardPaddings
import net.taler.wallet.donau.DonauSummaryItem
import net.taler.wallet.transactions.DummyTransaction
import net.taler.wallet.transactions.Transaction
import net.taler.wallet.transactions.TransactionRow
import net.taler.wallet.transactions.TransactionsResult
import net.taler.wallet.ui.theme.errorContainerLight
import net.taler.wallet.ui.theme.onErrorContainerLight
import net.taler.wallet.ui.theme.onSuccessContainerLight
import net.taler.wallet.ui.theme.onSurfaceVariantLight
import net.taler.wallet.ui.theme.successContainerLight
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Whether the pager shows a balances list page (page 0) in front of the
 * individual balances. With zero balances the list is still shown (it also
 * carries statements and token sections); with a single balance the pager
 * starts directly on that balance.
 */
internal fun showBalancesList(count: Int): Boolean = count != 1

/**
 * Maps a selected [ScopeInfo] to the pager page that displays it.
 * Page 0 is the balances list when [showBalancesList] is true, otherwise it
 * is the first (and only) balance. A scope that is not part of [balances]
 * falls back to page 0.
 */
internal fun scopeToPage(
    balances: List<BalanceItem>,
    scope: ScopeInfo?,
    showBalancesList: Boolean,
): Int {
    if (scope == null) return 0
    val index = balances.indexOfFirst { it.scopeInfo == scope }
    if (index < 0) return 0
    return if (showBalancesList) index + 1 else index
}

/**
 * Maps a pager [page] to the balance it displays. Returns null for the
 * balances list page (page 0 when [showBalancesList] is true) and for pages
 * out of range.
 */
internal fun pageToBalance(
    balances: List<BalanceItem>,
    page: Int,
    showBalancesList: Boolean,
): BalanceItem? = if (showBalancesList && page == 0) {
    null
} else {
    balances.getOrNull(if (showBalancesList) page - 1 else page)
}

@Composable
fun balanceContainerColor() = if (isSystemInDarkTheme()) {
    MaterialTheme.colorScheme.primary
} else {
    MaterialTheme.colorScheme.primaryContainer
}

@Composable
fun balanceContentColor() = if (isSystemInDarkTheme()) {
    MaterialTheme.colorScheme.onPrimary
} else {
    MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
fun BalancesPagerComposable(
    innerPadding: PaddingValues,
    bottomFabSkip: Dp,
    state: BalanceState,
    selectedBalance: ScopeInfo?,
    recentTxs: Map<ScopeInfo, StateFlow<TransactionsResult>?>,
    devMode: Boolean,
    networkStatus: Boolean,
    onBalanceSelected: (BalanceItem?) -> Unit,
    onWithdrawMoney: () -> Unit,
    onGetDemoMoney: () -> Unit,
    onShopping: (BalanceItem) -> Unit,
    onSend: () -> Unit,
    onRequest: () -> Unit,
    onWithdraw: () -> Unit,
    onDeposit: () -> Unit,
    onTransactionClicked: (tx: Transaction) -> Unit,
    onShowAllTransactions: (ScopeInfo) -> Unit,
    onShowPendingIncoming: (ScopeInfo) -> Unit,
    onShowPendingOutgoing: (ScopeInfo) -> Unit,
    onShowDiscounts: (ScopeInfo?) -> Unit,
    onShowPasses: (ScopeInfo?) -> Unit,
) {
    if (state.showWelcome()) {
        return EmptyBalancesComposable(
            innerPadding = innerPadding,
            networkStatus = networkStatus,
            onWithdrawMoney,
            onGetDemoMoney,
        )
    }

    when (state) {
        is BalanceState.Loading -> LoadingScreen()
        is BalanceState.Error -> ErrorComposable(
            state.error,
            devMode = devMode,
            modifier = Modifier.fillMaxSize()
        )

        is BalanceState.Success -> {
            val balances = state.balances
            val hasListPage = showBalancesList(balances.size)
            val pageCount = balances.size + if (hasListPage) 1 else 0

            // The selection (viewMode.selectedScope) is the single source of
            // truth for the pager: the page it maps to is recomputed from the
            // current balances on every composition, so balances changes
            // (new exchange, removed scope) always land on the right page.
            val selectedPage = scopeToPage(balances, selectedBalance, hasListPage)
            val pagerState = rememberPagerState(
                initialPage = selectedPage.coerceIn(0, pageCount - 1),
                pageCount = { pageCount },
            )

            LaunchedEffect(selectedPage) {
                pagerState.animateScrollToPage(selectedPage.coerceIn(0, pageCount - 1))
            }

            LaunchedEffect(pagerState, state) {
                snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
                    .collect { (page, inProgress) ->
                        if (!inProgress) onBalanceSelected(pageToBalance(balances, page, hasListPage))
                    }
            }

            Column(Modifier.padding(innerPadding)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val balance = pageToBalance(balances, page, hasListPage)
                    if (balance == null) {
                        BalancesListComposable(
                            innerPadding = PaddingValues(0.dp),
                            bottomFabSkip = bottomFabSkip,
                            state = state,
                            devMode = devMode,
                            networkStatus = networkStatus,
                            onWithdrawMoneyClicked = onWithdrawMoney,
                            onGetDemoMoneyClicked = onGetDemoMoney,
                            onBalanceClicked = onBalanceSelected,
                            onStatementClicked = { onBalanceSelected(null) }
                        )
                    } else {
                        val scope = balance.scopeInfo
                        val recentTx = recentTxs[scope]?.collectAsState(TransactionsResult.None)
                        BalancesPage(
                            pagerState = pagerState,
                            page = page,
                            showPaginator = pageCount > 1,
                            bottomFabSkip = bottomFabSkip,
                            balance = balance,
                            devMode = devMode,
                            disableActions = !networkStatus,
                            recentTxs = recentTx?.value,
                            onShoppingClicked = { onShopping(balance) },
                            onTransactionClicked = onTransactionClicked,
                            onSend = onSend,
                            onRequest = onRequest,
                            onWithdraw = onWithdraw,
                            onDeposit = onDeposit,
                            onSelectBalance = { target ->
                                onBalanceSelected(pageToBalance(balances, target, hasListPage))
                            },
                            onShowAllTransactions = { onShowAllTransactions(scope) },
                            onShowPendingIncoming = { onShowPendingIncoming(scope) },
                            onShowPendingOutgoing = { onShowPendingOutgoing(scope) },
                            onShowStatements = { onBalanceSelected(null) },
                            onShowDiscounts = { onShowDiscounts(scope) },
                            onShowPasses = { onShowPasses(scope) },
                        )
                    }
                }
            }
        }

        else -> {}
    }
}

@Composable
fun BalancesPage(
    balance: BalanceItem,
    pagerState: PagerState,
    page: Int,
    showPaginator: Boolean = true,
    bottomFabSkip: Dp,
    devMode: Boolean,
    disableActions: Boolean,
    recentTxs: TransactionsResult?,
    onShoppingClicked: () -> Unit,
    onSend: () -> Unit,
    onRequest: () -> Unit,
    onWithdraw: () -> Unit,
    onDeposit: () -> Unit,
    onSelectBalance: (Int) -> Unit,
    onTransactionClicked: (tx: Transaction) -> Unit,
    onShowAllTransactions: () -> Unit,
    onShowPendingIncoming: () -> Unit,
    onShowPendingOutgoing: () -> Unit,
    onShowStatements: () -> Unit,
    onShowDiscounts: () -> Unit,
    onShowPasses: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        val spacing = 12.dp
        BalanceHeaderCard(
            balance,
            onShowPendingIncoming,
            onShowPendingOutgoing
        )
        if (showPaginator) {
            PageIndicator(pagerState = pagerState, page = page, onSelectBalance = onSelectBalance)
        }
        Spacer(Modifier.height(spacing))
        if (balance.shoppingUrls.isNotEmpty()) {
            BalanceShoppingCard(balance, onShoppingClicked)
            Spacer(Modifier.height(spacing))
        }
        BalanceActionRow(
            balance = balance,
            disableActions = disableActions,
            onSend = onSend,
            onRequest = onRequest,
            onWithdraw = onWithdraw,
            onDeposit = onDeposit,
        )
        Spacer(Modifier.height(spacing))
        BalanceRecentTransactions(
            balance = balance,
            devMode = devMode,
            recentTxs = recentTxs,
            onTransactionClicked = onTransactionClicked,
            onShowAllTransactions = onShowAllTransactions,
        )
        Spacer(Modifier.height(spacing * 2))
        BalanceAssetsRow(
            onShowStatements = onShowStatements,
            onShowDiscounts = onShowDiscounts,
            onShowPasses = onShowPasses,
        )
        Spacer(Modifier.height(bottomFabSkip))
        BottomInsetsSpacer()
    }
}

@Composable
fun PageIndicator(
    pagerState: PagerState,
    page: Int,
    modifier: Modifier = Modifier,
    onSelectBalance: (Int) -> Unit,
) {
    // Continuous viewport position in page units: 0 = balances list,
    // 1..n = individual balances. It also animates while paging.
    val pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction

    BoxWithConstraints(modifier) {
        val pageWidth = constraints.maxWidth
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                // Counter-translate so the row stays centered while pages slide.
                .offset {
                    IntOffset(
                        x = (-pagerState.getOffsetDistanceInPages(page) * pageWidth).roundToInt(),
                        y = 0,
                    )
                }
                // Fade out as this page leaves the viewport, cross-fading with
                // the adjacent page's indicator (and out at the balances list).
                .graphicsLayer { alpha = (1f - abs(page - pagerPosition) * 1.25f).coerceIn(0f, 1f) },
            horizontalArrangement = Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val inactive = MaterialTheme.colorScheme.outlineVariant
            val active = MaterialTheme.colorScheme.outline
            repeat(pagerState.pageCount) { iteration ->
                // Ease the highlight between adjacent dots while paging.
                val weight = (1f - abs(iteration - pagerPosition)).coerceIn(0f, 1f)
                val color = lerp(inactive, active, weight)
                val size = 12.dp
                if (iteration > 0) Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(size)
                        .clickable { onSelectBalance(iteration) }
                ) else {
                    OutlinedButton(
                        modifier = Modifier
                            .padding(end = 6.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = { onSelectBalance(0) },
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(size * 2f),
                            imageVector = Icons.AutoMirrored.Default.List,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = stringResource(R.string.balances_title),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceHeaderCard(
    balance: BalanceItem,
    showPendingIncoming: () -> Unit,
    showPendingOutgoing: () -> Unit,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    ElevatedCard(Modifier
        .cardPaddings()
        .fillMaxWidth()
        .dropShadow(CardDefaults.elevatedShape, block = {
            radius = 5f
            offset = Offset(0f, 6f)
            alpha = 0.16f
            color = onBackground
        }),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp,
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = balanceContainerColor(),
            contentColor = balanceContentColor(),
        ),
    ) {
        Column {
            Box(Modifier
                .align(Alignment.End)
                .padding(16.dp)
                .padding(bottom = 10.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(
                    fontStyle = FontStyle.Italic,
                    color = onSurfaceVariantLight,
                )) {
                    when (val info = balance.scopeInfo) {
                        is Exchange -> Text(cleanExchange(info.url))
                        is Auditor -> Text(cleanExchange(info.url))
                        else -> {}
                    }
                }
            }

            Text(balance.available.toString(),
                modifier = Modifier
                    .padding(16.dp)
                    .defaultMinSize(minHeight = 60.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = balanceContentColor(),
                ),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = stringResource(R.string.balances_pending_label),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelLarge,
                )
                AssistChip(
                    modifier = Modifier
                        .padding(start = 16.dp),
                    label = { Text(balance.pendingIncoming.toString(showSymbol = false)) },
                    leadingIcon = { Icon(Icons.Default.Add,
                        contentDescription = stringResource(R.string.balances_pending_incoming)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = successContainerLight,
                        labelColor = onSuccessContainerLight,
                        leadingIconContentColor = onSuccessContainerLight,
                    ),
                    shape = CircleShape,
                    onClick = showPendingIncoming,
                )

                AssistChip(
                    modifier = Modifier
                        .padding(start = 16.dp),
                    label = { Text(balance.pendingOutgoing.toString(showSymbol = false)) },
                    leadingIcon = { Icon(Icons.Default.Remove,
                        contentDescription = stringResource(R.string.balances_pending_outgoing)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = errorContainerLight,
                        labelColor = onErrorContainerLight,
                        leadingIconContentColor = onErrorContainerLight,
                    ),
                    shape = CircleShape,
                    onClick = showPendingOutgoing,
                )
            }
        }
    }
}

@Composable
fun BalanceShoppingCard(
    balance: BalanceItem,
    onClick: () -> Unit,
) {
    Box(Modifier
        .padding(horizontal = 12.dp)
        .padding(bottom = 9.dp)
    ) {
        Material3MenuGroup(items = buildList {
            add(
                Material3MenuItemData(
                    title = { Text(stringResource(
                        R.string.exchange_shopping_label,
                        balance.currency,
                    )) },
                    icon = { Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                    ) },
                    onClick = onClick,
                    cardColors = CardDefaults.cardColors(
                        containerColor = balanceContainerColor(),
                        contentColor = balanceContentColor(),
                    )
                )
            )
        })
    }
}

@Composable
fun BalanceRecentTransactions(
    balance: BalanceItem,
    devMode: Boolean,
    recentTxs: TransactionsResult?,
    onTransactionClicked: (tx: Transaction) -> Unit,
    onShowAllTransactions: () -> Unit,
) {
    val placeholderHeight = 320.dp // FIXME: calculate this dynamically
    OutlinedCard(Modifier.cardPaddings()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.balances_recent_transactions),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)

                Badge(
                    modifier = Modifier.clickable { onShowAllTransactions() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.all))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            when (val res = recentTxs) {
                is TransactionsResult.Success -> {
                    if (res.transactions.isEmpty()) {
                        EmptyComposable(
                            message = stringResource(R.string.transactions_empty),
                            modifier = Modifier.height(placeholderHeight),
                        )
                    } else res.transactions.forEach { tx ->
                        TransactionRow(
                            tx = tx,
                            spec = balance.available.spec,
                            isSelected = false,
                            selectionMode = false,
                            onTransactionClick = { onTransactionClicked(tx) },
                            onTransactionSelect = {},
                        )
                    }
                }

                is TransactionsResult.Error -> ErrorComposable(
                    modifier = Modifier
                        .defaultMinSize(minHeight = placeholderHeight),
                    error = res.error,
                    devMode = devMode,
                )

                null, is TransactionsResult.None -> LoadingScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(placeholderHeight),
                )
            }
        }
    }
}

@Composable
fun BalanceActionRow(
    balance: BalanceItem,
    disableActions: Boolean,
    onSend: () -> Unit,
    onRequest: () -> Unit,
    onWithdraw: () -> Unit,
    onDeposit: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ActionButton(
            icon = { Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = null) },
            label = { Text(stringResource(R.string.transactions_send_funds)) },
            shape = CircleShape,
            enabled = !disableActions && !balance.disablePeerPayments,
            modifier = Modifier.weight(1f),
            onClick = onSend,
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )

        ActionButton(
            icon = { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = null) },
            label = { Text(stringResource(R.string.transactions_receive_funds)) },
            shape = CircleShape,
            enabled = !disableActions && !balance.disablePeerPayments,
            modifier = Modifier.weight(1f),
            onClick = onRequest,
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )

        ActionButton(
            icon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
            label = { Text(stringResource(R.string.withdraw_button_label)) },
            shape = CircleShape,
            modifier = Modifier.weight(1f),
            onClick = onWithdraw,
            enabled = !disableActions,
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )

        ActionButton(
            icon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
            label = { Text(stringResource(R.string.send_deposit_button_label)) },
            shape = CircleShape,
            modifier = Modifier.weight(1f),
            onClick = onDeposit,
            enabled = !disableActions,
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )
    }
}

@Composable
fun BalanceAssetsRow(
    onShowStatements: () -> Unit,
    onShowDiscounts: () -> Unit,
    onShowPasses: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ActionButton(
            icon = { Icon(painterResource(R.drawable.ic_discounts), contentDescription = null) },
            label = { Text(stringResource(R.string.discounts_title)) },
            onClick = onShowDiscounts,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )

        ActionButton(
            icon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
            label = { Text(stringResource(R.string.passes_title)) },
            onClick = onShowPasses,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )

        ActionButton(
            icon = { Icon(painterResource(R.drawable.ic_donau), contentDescription = null) },
            label = { Text(stringResource(R.string.balances_section_statements)) },
            onClick = onShowStatements,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            contentColor = balanceContentColor(),
            containerColor = balanceContainerColor(),
        )
    }
}

@Preview
@Composable
fun BalancesPagerComposablePreview() {
    val balances = listOf(
        BalanceItem(
            scopeInfo = ScopeInfo.Global("CHF"),
            available = Amount.fromJSONString("CHF:10.20"),
            pendingIncoming = Amount.fromJSONString("CHF:1.20"),
            pendingOutgoing = Amount.fromJSONString("CHF:0.40"),
            disablePeerPayments = false,
        ),
        BalanceItem(
            scopeInfo = Exchange("KUDOS", "https://exchange.demo.taler.net"),
            available = Amount.fromJSONString("KUDOS:1407.37"),
            pendingIncoming = Amount.fromJSONString("KUDOS:0"),
            pendingOutgoing = Amount.fromJSONString("KUDOS:2.15"),
            disablePeerPayments = false,
        ),
        BalanceItem(
            scopeInfo = Auditor("MXN", "https://auditor.taler.banxico.org.mx"),
            available = Amount.fromJSONString("MXN:5.50"),
            pendingIncoming = Amount.fromJSONString("MXN:1.40"),
            pendingOutgoing = Amount.fromJSONString("MXN:0"),
            disablePeerPayments = false,
        ),
    )

    val recentTxs: Map<ScopeInfo, StateFlow<TransactionsResult>> = mapOf(
        Exchange("KUDOS", "https://exchange.demo.taler.net") to MutableStateFlow<TransactionsResult>(TransactionsResult.Success(
            listOf(
                DummyTransaction("txn:1234", Timestamp.now(), TalerErrorInfo.makeCustomError("no error")),
                DummyTransaction("txn:2345", Timestamp.now(), TalerErrorInfo.makeCustomError("no error")),
                DummyTransaction("txn:3456", Timestamp.now(), TalerErrorInfo.makeCustomError("no error")),
                DummyTransaction("txn:4567", Timestamp.now(), TalerErrorInfo.makeCustomError("no error")),
            )))
    )

    val donauSummary = listOf(
        DonauSummaryItem(
            donauBaseUrl = "https://donau.test.taler.net/",
            legalDomain = "Gnuland",
            year = 2025,
            amountReceiptsSubmitted = Amount.fromJSONString("KUDOS:10"),
            amountReceiptsAvailable = Amount.fromJSONString("KUDOS:10"),
        )
    )

    TalerSurface {
        BalancesPagerComposable(
            innerPadding = PaddingValues(0.dp),
            bottomFabSkip = 0.dp,
            selectedBalance = Exchange("KUDOS", "https://exchange.demo.taler.net"),
            state = BalanceState.Success(balances, donauSummary),
            recentTxs = recentTxs,
            devMode = false,
            networkStatus = true,
            onBalanceSelected = {},
            onWithdrawMoney = {},
            onGetDemoMoney = {},
            onShopping = {},
            onSend = {},
            onRequest = {},
            onWithdraw = {},
            onDeposit = {},
            onTransactionClicked = {},
            onShowAllTransactions = {},
            onShowPendingIncoming = {},
            onShowPendingOutgoing = {},
            onShowDiscounts = {},
            onShowPasses = {},
        )
    }
}