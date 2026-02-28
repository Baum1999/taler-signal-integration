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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.compose.Material3MenuGroup
import net.taler.wallet.compose.Material3MenuItemData

@Composable
fun EmptyBalancesComposable(
    innerPadding: PaddingValues,
    networkStatus: Boolean,
    onWithdrawMoneyClicked: () -> Unit,
    onGetDemoMoneyClicked: () -> Unit,
) {
    LazyColumn (
        modifier = Modifier
            .padding(innerPadding)
            .windowInsetsPadding(WindowInsets.systemBars.only(
                WindowInsetsSides.Top
            ))
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
    ) {
        item { Spacer(Modifier.height(20.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    modifier = Modifier
                        .height(45.dp)
                        .padding(end = 5.dp),
                    painter = painterResource(R.drawable.ic_taler_full),
                    contentDescription = null)

                Text(
                    stringResource(R.string.wallet),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        item {
            Material3MenuGroup(buildList {
                add(Material3MenuItemData(
                    title = { Text(stringResource(R.string.balances_empty_withdraw_chf_title)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.balances_empty_withdraw_chf_message))
                            Spacer(Modifier.height(6.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onWithdrawMoneyClicked,
                                enabled = networkStatus,
                            ) {
                                Text(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    text = stringResource(R.string.balances_empty_withdraw_chf_button),
                                )
                            }
                        }
                    },
                ))
            })
        }

        item { Spacer(Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(buildList {
                add(
                    Material3MenuItemData(
                        title = {},
                        description = {
                            Column {
                                Text(stringResource(R.string.balances_empty_withdraw_kudos_message))
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = onGetDemoMoneyClicked,
                                    enabled = networkStatus,
                                ) {
                                    Text(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        text = stringResource(R.string.balances_empty_withdraw_kudos_button),
                                    )
                                }
                            }
                        },
                    )
                )
            })
        }
    }
}