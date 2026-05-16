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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.donau.GetDonauStatus.Error
import net.taler.wallet.donau.GetDonauStatus.Loading
import net.taler.wallet.donau.GetDonauStatus.None
import net.taler.wallet.donau.GetDonauStatus.Success
import net.taler.wallet.main.MainViewModel

@Composable
fun SetDonauScreen(
    model: MainViewModel,
    donauBaseUrl: String?,
    onShowMessage: (String) -> Unit,
    onShowError: (TalerErrorInfo) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val donauManager = model.donauManager
    val donauStatus by donauManager.donauStatus.collectAsStateLifecycleAware()
    val devMode by model.devMode.observeAsState(false)

    LaunchedEffect(Unit) {
        donauManager.getDonau()
    }

    val readyMessage = stringResource(id = R.string.donau_ready)

    TalerSurface {
        GlobalScaffold(
            model = model,
            modifier = Modifier.fillMaxSize(),
            onNavigateBack = onNavigateBack,
            title = { Text(stringResource(R.string.donau_title)) },
        ) { paddingValues ->
            when (val status = donauStatus) {
                Loading, None -> LoadingScreen(Modifier.padding(paddingValues))
                is Success -> SetDonauComposable(
                    modifier = Modifier.padding(paddingValues),
                    donauInfo = status.donauInfo,
                    initialUrl = donauBaseUrl,
                    onSetDonauInfo = { info ->
                        donauManager.setDonau(
                            info,
                            {
                                onShowMessage(readyMessage)
                                onNavigateBack()
                            },
                            { error -> onShowError(error) }
                        )
                    },
                )

                is Error -> ErrorComposable(
                    error = status.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                    devMode = devMode,
                )
            }
        }
    }
}

@Composable
fun SetDonauComposable(
    donauInfo: DonauInfo?,
    onSetDonauInfo: (info: DonauInfo) -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var donauBaseUrl by remember { mutableStateOf(initialUrl ?: donauInfo?.donauBaseUrl ?: "") }
    var taxPayerId by remember { mutableStateOf(donauInfo?.taxPayerId ?: "") }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            modifier = Modifier.padding(
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ).fillMaxWidth(),
            value = donauBaseUrl,
            onValueChange = {
                donauBaseUrl = it
            },
            singleLine = true,
            isError = donauBaseUrl.isBlank(),
            label = { Text(stringResource(R.string.donau_url)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        )

        OutlinedTextField(
            modifier = Modifier.padding(
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ).fillMaxWidth(),
            value = taxPayerId,
            onValueChange = {
                taxPayerId = it
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Monospace),
            isError = taxPayerId.isBlank(),
            label = { Text(stringResource(R.string.donau_id)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Exit) }),
        )

        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .align(Alignment.End),
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onSetDonauInfo(DonauInfo(
                    donauBaseUrl = donauBaseUrl,
                    taxPayerId = taxPayerId,
                ))
            },
            enabled = donauBaseUrl.isNotBlank() &&
                    taxPayerId.isNotBlank()
        ) {
            Text(stringResource(R.string.save))
        }

        BottomInsetsSpacer()
    }
}

@Preview
@Composable
fun SetDonauComposablePreview() {
    TalerSurface {
        SetDonauComposable(
            donauInfo = null,
            initialUrl = "https://donau.test.taler.net/",
            onSetDonauInfo = {},
        )
    }
}
