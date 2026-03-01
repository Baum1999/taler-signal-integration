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

package net.taler.wallet.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import kotlinx.coroutines.launch
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.BottomButtonBox
import net.taler.wallet.compose.EmptyComposable
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.exchanges.ExchangeTosStatus
import net.taler.wallet.exchanges.TosResponse
import net.taler.wallet.main.MainViewModel
import net.taler.wallet.systemBarsPaddingBottom
import java.util.Locale

class ReviewExchangeTosFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val exchangeManager by lazy { model.exchangeManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {
            val exchangeBaseUrl = arguments
                ?.getString("exchangeBaseUrl")
                ?: error("no exchangeBaseUrl passed")
            val readOnly = arguments
                ?.getBoolean("readOnly")
                ?: false

            var tos: TosResponse? by remember { mutableStateOf(null) }
            var selectedLang by remember { mutableStateOf(Locale.getDefault().language) }

            LaunchedEffect(selectedLang) {
                tos = null
                tos = model.exchangeManager.getExchangeTos(exchangeBaseUrl, selectedLang)
            }

            TalerSurface {
                tos?.let { tos ->
                    ReviewExchangeTosComposable(tos,
                        readOnly = readOnly,
                        onSelectLang = { selectedLang = it },
                        onAcceptTos = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                if (exchangeManager.acceptCurrentTos(
                                        exchangeBaseUrl = exchangeBaseUrl,
                                        currentEtag = tos.currentEtag,
                                    )) {
                                    findNavController().navigateUp()
                                }
                            }
                        },
                    )
                } ?: LoadingScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewExchangeTosComposable(
    tos: TosResponse,
    readOnly: Boolean,
    onSelectLang: (String) -> Unit,
    onAcceptTos: () -> Unit,
) {
    if (tos.status == ExchangeTosStatus.MissingTos) {
        EmptyComposable(stringResource(R.string.exchange_tos_missing))
        return
    }

    var expanded by remember { mutableStateOf(false) }

    Scaffold(
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
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
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

        ReviewExchangeTosComposable(tos, false, {}, {})
    }
}