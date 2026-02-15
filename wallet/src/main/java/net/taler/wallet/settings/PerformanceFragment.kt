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

package net.taler.wallet.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.serialization.json.Json
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.balances.SectionHeader
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ShareButton
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel

class PerformanceFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {
            TalerSurface {
                val stats by model.settingsManager.performanceTable.collectAsStateLifecycleAware()

                if (stats == null) {
                    EmptyComposable()
                    return@TalerSurface
                }

                stats?.let {
                    PerformanceTableComposable(it,
                        onReload = { model.settingsManager.loadPerformanceStats() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.settingsManager.loadPerformanceStats()
    }
}

@Composable
fun PerformanceTableComposable(
    stats: PerformanceTable,
    onReload: () -> Unit,
) {
    val json = remember { Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    } }

    LazyColumn {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ShareButton(json.encodeToString(stats))

                Button(onClick = onReload) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.reload))
                }
            }
        }

        if (stats.httpFetch.isNotEmpty()) {
            stickyHeader {
                SectionHeader { Text(stringResource(R.string.performance_stats_http_fetch)) }
            }

            itemsIndexed(stats.httpFetch) { i, stat ->
                PerformanceStatItem(i + 1, stat)
            }
        }

        if (stats.dbQuery.isNotEmpty()) {
            stickyHeader {
                SectionHeader { Text(stringResource(R.string.performance_stats_db_query)) }
            }

            itemsIndexed(stats.dbQuery) { i, stat ->
                PerformanceStatItem(i + 1, stat)
            }
        }


        if (stats.crypto.isNotEmpty()) {
            stickyHeader {
                SectionHeader { Text(stringResource(R.string.performance_stats_crypto)) }
            }

            itemsIndexed(stats.crypto) { i, stat ->
                PerformanceStatItem(i + 1, stat)
            }
        }

        if (stats.walletRequest.isNotEmpty()) {
            stickyHeader {
                SectionHeader { Text(stringResource(R.string.performance_stats_wallet_request)) }
            }

            itemsIndexed(stats.walletRequest) { i, stat ->
                PerformanceStatItem(i + 1, stat)
            }
        }

        if (stats.walletTask.isNotEmpty()) {
            stickyHeader {
                SectionHeader { Text(stringResource(R.string.performance_stats_wallet_task)) }
            }

            itemsIndexed(stats.walletTask) { i, stat ->
                PerformanceStatItem(i + 1, stat)
            }
        }

        item {
            BottomInsetsSpacer()
        }
    }
}

@Composable
fun PerformanceStatItem(
    ranking: Int,
    stat: PerformanceStat,
) {
    ListItem(
        leadingContent = {
            Text(
                text = stringResource(R.string.ranking, ranking),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
            )
        },

        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,

            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    when (stat) {
                        is PerformanceStat.HttpFetch -> Text(
                            text = stat.url,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        is PerformanceStat.DbQuery -> Text(stat.name)
                        is PerformanceStat.Crypto -> Text(stat.operation)
                        is PerformanceStat.WalletRequest -> Text(stat.operation)
                        is PerformanceStat.WalletTask -> Text(
                            text = stat.taskId,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Badge(Modifier.padding(start = 10.dp)) {
                    Text(stat.count.toString())
                }
            }
        },

        supportingContent = {
            when(stat) {
                is PerformanceStat.DbQuery -> Text(
                    text = stat.location,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                else -> {}
            }
        },

        trailingContent = {
            Text(stringResource(R.string.millisecond, stat.maxDurationMs))
        },
    )
}