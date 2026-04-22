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

package net.taler.wallet.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalScaffold(
    model: MainViewModel?,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(
        WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
    ),
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable ((PaddingValues) -> Unit),
) {
    val scrollBehavior = TopAppBarDefaults
        .pinnedScrollBehavior(rememberTopAppBarState())
    val localFocusManager = LocalFocusManager.current

    val devMode = model?.devMode?.observeAsState(false)
    val online = model?.networkManager?.networkStatus?.observeAsState(true)

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (title != null) {
                    TopAppBar(
                        title = title,
                        navigationIcon = navigationIcon ?: {
                            if (onNavigateBack != null) {
                                IconButton(onClick = {
                                    localFocusManager.clearFocus()
                                    onNavigateBack()
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Default.ArrowBack,
                                        contentDescription = stringResource(R.string.button_back),
                                    )
                                }
                            }
                        },
                        actions = {
                            Row {
                                actions()
                                if (devMode != null && devMode.value) {
                                    IconButton(onClick = {
                                        model.showObservabilityLog()
                                    }) {
                                        Icon(
                                            Icons.Default.BugReport,
                                            contentDescription = stringResource(R.string.observability_title),
                                        )
                                    }
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }

                if (online != null && !online.value) Text(
                    text = stringResource(R.string.offline_banner),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        contentWindowInsets = contentWindowInsets,
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        content(innerPadding)
    }
}