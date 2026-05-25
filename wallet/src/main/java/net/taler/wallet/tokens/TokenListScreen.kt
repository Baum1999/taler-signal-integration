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

package net.taler.wallet.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.taler.common.Merchant
import net.taler.common.RelativeTime
import net.taler.common.TalerUtils
import net.taler.common.Timestamp
import net.taler.lib.android.base64Bitmap
import net.taler.lib.android.toAbsoluteTime
import net.taler.wallet.NavigateCallback
import net.taler.wallet.R
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.MerchantAvatar
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.cardPaddings
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.ui.theme.TalerTheme

enum class TokenViewMode {
    Discounts,
    Passes,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenListScreen(
    model: MainViewModel,
    viewMode: TokenViewMode,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val pages = listOf(TokenFilter.Valid, TokenFilter.Expired)
    val pagerState = rememberPagerState { pages.size }

    val scope = rememberCoroutineScope()
    val devMode by model.devMode.observeAsState(false)
    val discounts by model.tokenManager.discounts.collectAsStateLifecycleAware(UiState.Loading)
    val passes by model.tokenManager.passes.collectAsStateLifecycleAware(UiState.Loading)

    GlobalScaffold(
        model = model,
        modifier = Modifier.fillMaxSize(),
        onNavigateBack = onNavigateBack,
        title = {
            Text(when (viewMode) {
                TokenViewMode.Discounts -> stringResource(R.string.discounts_title)
                TokenViewMode.Passes -> stringResource(R.string.passes_title)
            })
        },
        tabs = {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                pages.forEachIndexed { index, filter ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.scrollToPage(index) } },
                        text = {
                            Text(
                                when (filter) {
                                    TokenFilter.Valid -> stringResource(R.string.tokens_filter_available)
                                    TokenFilter.Expired -> stringResource(R.string.tokens_filter_expired)
                                }
                            )
                        },
                    )
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val filter = pages[page]
            when (viewMode) {
                TokenViewMode.Discounts -> DiscountList(discounts, filter, devMode)
                TokenViewMode.Passes -> PassList(passes, filter, devMode)
            }
        }
    }
}

@Composable
fun DiscountList(uiState: UiState<List<DiscountListDetail>>, filter: TokenFilter, devMode: Boolean) {
    TokenListContent(uiState, devMode) { discounts ->
        val filtered = discounts.filterDiscounts(filter)
        if (filtered.isEmpty()) {
            EmptyComposable(
                message = when (filter) {
                    is TokenFilter.Valid -> stringResource(R.string.passes_empty_valid)
                    is TokenFilter.Expired -> stringResource(R.string.passes_empty_expired)
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { discount ->
                DiscountCard(discount)
            }
        }
    }
}

@Composable
fun PassList(uiState: UiState<List<SubscriptionListDetail>>, filter: TokenFilter, devMode: Boolean) {
    TokenListContent(uiState, devMode) { passes ->
        val filtered = passes.filterPasses(filter)
        if (filtered.isEmpty()) {
            EmptyComposable(
                message = when (filter) {
                    is TokenFilter.Valid -> stringResource(R.string.passes_empty_valid)
                    is TokenFilter.Expired -> stringResource(R.string.passes_empty_expired)
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { pass ->
                PassCard(pass)
            }
        }
    }
}

@Composable
fun <T> TokenListContent(
    uiState: UiState<T>,
    devMode: Boolean,
    content: @Composable (T) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is UiState.Error -> ErrorComposable(error = uiState.error, devMode = devMode)
            is UiState.Success -> content(uiState.data)
        }
    }
}

@Composable
fun DiscountCard(pass: DiscountListDetail) {
    val isActive = remember(pass) { pass.isActive }
    OutlinedCard(Modifier.cardPaddings()) {
        Column {
            ListItem(
                // leadingContent = { MerchantAvatar(pass.merchantInfo, size = 40.dp) },
                headlineContent = {
                    Text(pass.name, style = MaterialTheme.typography.headlineMedium
                        .copy(fontWeight = FontWeight.Medium))
                },
                supportingContent = {
                    Column {
                        Text(
                            TalerUtils.getLocalizedString(
                                pass.descriptionI18n,
                                pass.description,
                            ),
                            style= MaterialTheme.typography.labelLarge,
                        )

                        Text(
                            stringResource(
                                R.string.pass_issuer,
                                pass.merchantInfo?.name
                                    ?: cleanExchange(pass.merchantBaseUrl),
                            ),
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                trailingContent = {
                    Badge(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.discount_quantity, pass.tokensAvailable),
                            modifier = Modifier.padding(3.5.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            )

            TokenValidityFooter(isActive,
                pass.validityStart,
                pass.validityEnd)
        }
    }
}

@Composable
fun PassCard(pass: SubscriptionListDetail) {
    val isActive = remember(pass) { pass.isActive }
    OutlinedCard(Modifier.cardPaddings()) {
        Column {
            ListItem(
                // leadingContent = { MerchantAvatar(pass.merchantInfo, size = 40.dp) },
                headlineContent = {
                    Text(pass.name, style = MaterialTheme.typography.headlineMedium
                        .copy(fontWeight = FontWeight.Medium))
                },
                supportingContent = {
                    Column {
                        Text(
                            TalerUtils.getLocalizedString(
                                pass.descriptionI18n,
                                pass.description,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Text(
                            stringResource(
                                R.string.pass_issuer,
                                pass.merchantInfo?.name
                                    ?: cleanExchange(pass.merchantBaseUrl),
                            ),
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                trailingContent = {
                    if (isActive) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                text = stringResource(R.string.pass_active),
                                modifier = Modifier.padding(3.5.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            )

            TokenValidityFooter(isActive,
                pass.validityStart,
                pass.validityEnd)
        }
    }
}

@Composable
fun TokenValidityFooter(
    isActive: Boolean,
    validityStart: Timestamp,
    validityEnd: Timestamp,
) {
    val context = LocalContext.current

    HorizontalDivider()

    Box(modifier = Modifier
        .background(if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        })
        .fillMaxWidth()
        .padding(vertical = 12.dp)
        .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            Text(
                text = stringResource(
                    R.string.token_valid_until,
                    validityEnd.ms.toAbsoluteTime(context),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.token_valid_from,
                    validityStart.ms.toAbsoluteTime(context),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val oneWeek = RelativeTime.fromMillis(86400000 * 7)

@Preview
@Composable
fun DiscountListPreview() {
    val now = Timestamp.now()
    TalerSurface {
        TalerTheme {
            DiscountList(
                uiState = UiState.Success(
                    data = listOf(
                        DiscountListDetail(
                            tokenFamilyHash = "",
                            tokenIssuePubHash = "",
                            merchantBaseUrl = "https://backend.demo.taler.net/",
                            merchantInfo = Merchant(name = "Test Merchant"),
                            name = "20% off",
                            description = "valid for fruits",
                            descriptionI18n = mapOf(),
                            validityStart = now - oneWeek,
                            validityEnd = now + oneWeek,
                            tokensAvailable = 3,
                        ),
                        DiscountListDetail(
                            tokenFamilyHash = "",
                            tokenIssuePubHash = "",
                            merchantBaseUrl = "https://backend.demo.taler.net/",
                            name = "Name",
                            description = "Description",
                            descriptionI18n = mapOf(),
                            validityStart = now + oneWeek,
                            validityEnd = now + oneWeek + oneWeek,
                            tokensAvailable = 2,
                        ),
                    ),
                ),
                filter = TokenFilter.Valid,
                devMode = true,
            )
        }
    }
}

@Preview
@Composable
fun PassListPreview() {
    val now = Timestamp.now()
    TalerSurface {
        TalerTheme {
            PassList(
                uiState = UiState.Success(
                    data = listOf(
                        SubscriptionListDetail(
                            tokenFamilyHash = "",
                            tokenIssuePubHash = "",
                            merchantBaseUrl = "https://backend.demo.taler.net/",
                            merchantInfo = Merchant(name = "Test Merchant"),
                            name = "Premium pass",
                            description = "Provides you access to this mega description",
                            descriptionI18n = mapOf(),
                            validityStart = now - oneWeek,
                            validityEnd = now + oneWeek,
                        ),

                        SubscriptionListDetail(
                            tokenFamilyHash = "",
                            tokenIssuePubHash = "",
                            merchantBaseUrl = "https://backend.demo.taler.net/",
                            name = "Name",
                            description = "Description",
                            descriptionI18n = mapOf(),
                            validityStart = now + oneWeek,
                            validityEnd = now + oneWeek + oneWeek,
                        ),
                    ),
                ),
                filter = TokenFilter.Valid,
                devMode = true,
            )
        }
    }
}