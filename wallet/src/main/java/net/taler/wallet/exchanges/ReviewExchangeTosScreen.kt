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

package net.taler.wallet.exchanges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import kotlinx.coroutines.launch
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.systemBarsPaddingBottom
import java.util.Locale

@Composable
fun ReviewExchangeTosScreen(
    model: MainViewModel,
    exchangeBaseUrl: String,
    readOnly: Boolean = false,
    onNavigateBack: () -> Unit,
) {
    val exchangeManager = model.exchangeManager
    val devMode by model.devMode.observeAsState(false)
    val scope = rememberCoroutineScope()
    var tos: TosResponse? by remember { mutableStateOf(null) }
    var error: TalerErrorInfo? by remember { mutableStateOf(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var selectedLang by remember { mutableStateOf(Locale.getDefault().language) }

    LaunchedEffect(selectedLang, retryTrigger) {
        tos = null
        error = null
        exchangeManager.getExchangeTos(exchangeBaseUrl, selectedLang)
            .onSuccess { tos = it }
            .onError { error = it }
    }

    TalerSurface {
        val currentTos = tos
        val currentError = error
        when {
            currentTos != null -> ReviewExchangeTosComposable(
                model = model,
                tos = currentTos,
                readOnly = readOnly,
                onSelectLang = { selectedLang = it },
                onAcceptTos = {
                    scope.launch {
                        exchangeManager.acceptCurrentTos(
                            exchangeBaseUrl = exchangeBaseUrl,
                            currentEtag = currentTos.currentEtag,
                        ).onSuccess {
                            onNavigateBack()
                        }.onError { error = it }
                    }
                },
                onNavigateBack = onNavigateBack,
            )
            currentError != null -> ReviewExchangeTosErrorComposable(
                model = model,
                error = currentError,
                devMode = devMode,
                onRetry = { retryTrigger++ },
                onNavigateBack = onNavigateBack,
            )

            else -> LoadingScreen()
        }
    }
}

@Composable
fun ReviewExchangeTosErrorComposable(
    error: TalerErrorInfo,
    devMode: Boolean,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    model: MainViewModel? = null,
) {
    GlobalScaffold(
        model = model,
        title = { Text(stringResource(R.string.nav_exchange_tos)) },
        onNavigateBack = onNavigateBack,
        bottomBar = {
            BottomButtonBox {
                Button(
                    modifier = Modifier.systemBarsPaddingBottom(),
                    onClick = onRetry,
                ) {
                    Text(stringResource(R.string.transactions_retry))
                }
            }
        },
    ) { innerPadding ->
        ErrorComposable(
            error = error,
            modifier = Modifier.padding(innerPadding),
            devMode = devMode,
            message = stringResource(R.string.exchange_tos_error, ""),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewExchangeTosComposable(
    tos: TosResponse,
    readOnly: Boolean,
    onSelectLang: (String) -> Unit,
    onAcceptTos: () -> Unit,
    onNavigateBack: () -> Unit,
    model: MainViewModel? = null,
) {
    if (tos.status == ExchangeTosStatus.MissingTos) {
        EmptyComposable(message = stringResource(R.string.exchange_tos_missing))
        return
    }

    var expanded by remember { mutableStateOf(false) }

    GlobalScaffold(
        model = model,
        title = { Text(stringResource(R.string.nav_exchange_tos)) },
        onNavigateBack = onNavigateBack,
        bottomBar = {
            if (!readOnly) BottomButtonBox {
                Button(
                    modifier = Modifier
                        .systemBarsPaddingBottom(),
                    onClick = onAcceptTos,
                ) {
                    Text(stringResource(R.string.exchange_tos_accept))
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(Modifier.padding(innerPadding)) {
            if (tos.tosAvailableLanguages.size > 1) item {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        label = { Text(stringResource(R.string.language)) },
                        value = tos.contentLanguage?.let {
                            Locale(it).displayLanguage
                        } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy( // show text as if not disabled
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )

                    ExposedDropdownMenu (
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        tos.tosAvailableLanguages.forEach {
                            DropdownMenuItem(
                                { Text("${Locale(it).displayLanguage}") },
                                onClick = {
                                    onSelectLang(it)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Markdown(
                    content = tos.content.trimIndent(),
                    modifier = Modifier.padding(16.dp),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.headlineLarge,
                        h2 = MaterialTheme.typography.headlineMedium,
                        h3 = MaterialTheme.typography.headlineSmall,
                        h4 = MaterialTheme.typography.titleLarge,
                        h5 = MaterialTheme.typography.titleMedium,
                        h6 = MaterialTheme.typography.titleSmall,
                        text = MaterialTheme.typography.bodyMedium,
                        paragraph = MaterialTheme.typography.bodyMedium,
                    ),
                    padding = markdownPadding(
                        block = 5.dp,
                    ),
                    error = { modifier ->
                        ErrorComposable(
                            TalerErrorInfo.makeCustomError(
                                stringResource(R.string.exchange_tos_error, "")),
                            modifier = modifier,
                            devMode = false,
                        )
                    },
                )
            }
        }
    }
}

@Preview
@Composable
fun ReviewExchangeTosComposablePreview() {
    TalerSurface {
        val tos = TosResponse(
            status = ExchangeTosStatus.Proposed,
            content = "# Terms of service\nThis is a terms of service, obviously.\n## H2\n### H3\n#### H4\n##### H5\n###### H6",
            currentEtag = "1.2.0",
            contentLanguage = "en",
            tosAvailableLanguages = listOf("en", "en_US"),
        )

        ReviewExchangeTosComposable( tos, false, {}, {}, {}, null)
    }
}