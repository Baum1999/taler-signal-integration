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

package net.taler.wallet.donau

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.taler.wallet.R
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.main.MainViewModel

@Composable
fun DonauStatementScreen(
    model: MainViewModel,
    host: String,
    onNavigateBack: () -> Unit,
) {
    val donauManager = model.donauManager
    val status by donauManager.donauStatementsStatus.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)

    LaunchedEffect(host) {
        donauManager.getDonauStatements(host)
    }

    TalerSurface {
        GlobalScaffold(
            model = model,
            onNavigateBack = onNavigateBack,
            title = { Text(stringResource(R.string.donau_statement_title)) },
        ) { paddingValues ->
            when (val s = status) {
                is GetDonauStatementsStatus.None,
                is GetDonauStatementsStatus.Loading -> LoadingScreen(Modifier.padding(paddingValues))

                is GetDonauStatementsStatus.Error -> ErrorComposable(
                    error = s.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                    devMode = devMode,
                )

                is GetDonauStatementsStatus.Success -> if (s.statements.isEmpty()) {
                    EmptyComposable(Modifier.padding(paddingValues))
                } else {
                    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
                    DonauStatementComposable(
                        modifier = Modifier.padding(paddingValues),
                        statements = s.statements,
                        selectedIndex = selectedIndex,
                    ) { index ->
                        selectedIndex = index
                    }
                }
            }
        }
    }
}
