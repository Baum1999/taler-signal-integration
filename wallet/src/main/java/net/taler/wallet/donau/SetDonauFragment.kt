/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import net.taler.common.showError
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.compose.ErrorComposable
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.donau.GetDonauStatus.Error
import net.taler.wallet.donau.GetDonauStatus.Loading
import net.taler.wallet.donau.GetDonauStatus.None
import net.taler.wallet.donau.GetDonauStatus.Success
import net.taler.wallet.showError

class SetDonauFragment: Fragment() {
    val model: MainViewModel by activityViewModels()
    val donauManager by lazy { model.donauManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        val saveShouldExit = arguments?.getBoolean("saveShouldExit") == true
        val donauBaseUrl = arguments?.getString("donauBaseUrl")

        setContent {
            TalerSurface {
                val donauStatus by donauManager.donauStatus.collectAsState()
                val devMode by model.devMode.observeAsState()
                when(val status = donauStatus) {
                    Loading, None -> LoadingScreen()
                    is Success -> SetDonauComposable(
                        initialUrl = donauBaseUrl,
                        donauInfo = status.donauInfo,
                        onSetDonauInfo = { info ->
                            donauManager.setDonau(info,
                                {
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.donau_ready),
                                        Toast.LENGTH_LONG).show()
                                    if (saveShouldExit) {
                                        findNavController().popBackStack()
                                    }
                                },
                                { error ->
                                    if(model.devMode.value == true) {
                                        showError(error)
                                    } else {
                                        showError(error.userFacingMsg)
                                    }
                                }
                            )
                        },
                    )
                    is Error -> ErrorComposable(
                        error = status.error,
                        devMode = devMode == true,
                    )
                }

                LaunchedEffect(Unit) {
                    donauManager.getDonau()
                }
            }
        }
    }
}

@Composable
fun SetDonauComposable(
    initialUrl: String? = null,
    donauInfo: DonauInfo?,
    onSetDonauInfo: (info: DonauInfo) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var donauBaseUrl by remember { mutableStateOf(initialUrl ?: donauInfo?.donauBaseUrl ?: "") }
    var taxPayerId by remember { mutableStateOf(donauInfo?.taxPayerId ?: "") }

    Column(Modifier.fillMaxSize()) {
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
        SetDonauComposable("https://donau.test.taler.net/", null) {}
    }
}