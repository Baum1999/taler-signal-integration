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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.flow.MutableStateFlow
import net.taler.common.showError
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.balances.BalanceState
import net.taler.wallet.compose.LoadingScreen
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.collectAsStateLifecycleAware
import net.taler.wallet.showError

class DonauStatementFragment: Fragment() {
    private val model: MainViewModel by activityViewModels()

    private lateinit var host: String
    private val mStatements = MutableStateFlow<List<DonauStatement>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = ComposeView(requireContext()).apply {
        host = arguments?.getString("host")
            ?: error("no host provided")

        val supportActionBar = (requireActivity() as? AppCompatActivity)
            ?.supportActionBar

        setContent {
            TalerSurface {
                val statements by mStatements.collectAsStateLifecycleAware()
                if (statements.isEmpty()) {
                    LoadingScreen()
                } else {
                    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
                    DonauStatementComposable(statements, selectedIndex) { index ->
                        selectedIndex = index
                    }

                    LaunchedEffect(selectedIndex) {
                        supportActionBar?.title =
                            getString(
                                R.string.donau_statement_title_year,
                                statements[selectedIndex].year,
                            )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.balanceManager.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BalanceState.Error -> {
                    if (model.devMode.value == true) {
                        showError(state.error)
                    } else {
                        showError(state.error.userFacingMsg)
                    }
                }

                is BalanceState.Success -> {
                    state.statements.filter {
                        it.host == host
                    }.let {
                        mStatements.value = it
                    }
                }

                else -> {}
            }
        }
    }
}