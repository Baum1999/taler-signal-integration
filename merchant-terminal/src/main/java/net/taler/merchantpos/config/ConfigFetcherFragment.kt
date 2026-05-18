/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
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

package net.taler.merchantpos.config

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.MainActivity
import net.taler.merchantpos.PosDestination
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.showPosError

class ConfigFetcherFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val configManager by lazy { model.configManager }

    private var navigating = false

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ConfigFetcherScreen()
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        if (configManager.config.savePassword()) {
            configManager.fetchConfig(configManager.config, false)
        } else if (!navigating) {
            navigating = true
            (requireActivity() as MainActivity).navigateTo(PosDestination.Config, clearBackStack = true)
        }

        configManager.configUpdateResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                null -> return@observe
                is ConfigUpdateResult.Error -> {
                    requireActivity().showPosError(result.msg)
                }

                is ConfigUpdateResult.Success -> {
                    if (!navigating) {
                        navigating = true
                        (requireActivity() as MainActivity).navigateToInitialOrderScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigFetcherScreen() {
    PosTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.config_fetching),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
