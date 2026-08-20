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

package net.taler.wallet.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.ExperimentalSerializationApi
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun WalletInitErrorScreen(
    model: MainViewModel,
    error: TalerErrorInfo,
    onExportLogs: () -> Unit,
    onExportDb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devMode by model.devMode.observeAsState(false)
    GlobalScaffold(
        model = model,
        modifier = modifier,
        title = { Text(stringResource(R.string.wallet_init_error_title)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ErrorComposable(
                error = error,
                devMode = devMode,
                message = stringResource(R.string.wallet_init_error_message),
                scrollable = false,
            )

            HorizontalDivider(Modifier.size(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportLogs,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_bug_report),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.wallet_export_logs))
            }

            Spacer(Modifier.size(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportDb,
            ) {
                Icon(
                    painterResource(R.drawable.ic_database),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.wallet_export_database))
            }

            BottomInsetsSpacer()
        }
    }
}
