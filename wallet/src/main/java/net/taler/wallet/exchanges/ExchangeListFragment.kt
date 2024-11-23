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

package net.taler.wallet.exchanges

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import net.taler.common.Amount
import net.taler.common.EventObserver
import net.taler.common.fadeIn
import net.taler.common.fadeOut
import net.taler.common.showError
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.databinding.FragmentExchangeListBinding
import net.taler.wallet.showError

open class ExchangeListFragment : Fragment(), ExchangeClickListener {

    protected val model: MainViewModel by activityViewModels()
    private val exchangeManager by lazy { model.exchangeManager }
    private val transactionManager by lazy { model.transactionManager }
    private val balanceManager by lazy { model.balanceManager }

    protected lateinit var ui: FragmentExchangeListBinding
    protected open val isSelectOnly = false
    private val exchangeAdapter by lazy { ExchangeAdapter(isSelectOnly, this, model.devMode.value == true) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        ui = FragmentExchangeListBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupInsets()

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (model.devMode.value == true) {
                    menuInflater.inflate(R.menu.exchange_list, menu)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_add_dev_exchanges) {
                    exchangeManager.addDevExchanges()
                }
                return true
            }
        }, viewLifecycleOwner, RESUMED)

        ui.list.apply {
            adapter = exchangeAdapter
            addItemDecoration(DividerItemDecoration(context, VERTICAL))
        }
        ui.addExchangeFab.setOnClickListener {
            AddExchangeDialogFragment().show(parentFragmentManager, "ADD_EXCHANGE")
        }

        // TODO: refactor and unify progress bar handling
        // exchangeManager.progress.observe(viewLifecycleOwner) { show ->
        //     if (show) ui.progressBar.fadeIn() else ui.progressBar.fadeOut()
        // }

        exchangeManager.exchanges.observe(viewLifecycleOwner) { exchanges ->
            onExchangeUpdate(exchanges)
        }

        exchangeManager.addError.observe(viewLifecycleOwner, EventObserver { error ->
            onAddExchangeFailed()
            if (model.devMode.value == true) {
                showError(error)
            }
        })

        exchangeManager.listError.observe(viewLifecycleOwner, EventObserver { error ->
            onListExchangeFailed()
            if (model.devMode.value == true) {
                showError(error)
            }
        })

        exchangeManager.deleteError.observe(viewLifecycleOwner, EventObserver { error ->
            if (model.devMode.value == true) {
                showError(error)
            } else {
                showError(error.userFacingMsg)
            }
        })

        exchangeManager.reloadError.observe(viewLifecycleOwner, EventObserver { error ->
            if (model.devMode.value == true) {
                showError(error)
            } else {
                showError(error.userFacingMsg)
            }
        })
    }

    protected open fun onExchangeUpdate(exchanges: List<ExchangeItem>) {
        exchangeAdapter.update(exchanges)
        if (exchanges.isEmpty()) {
            ui.emptyState.fadeIn()
            ui.list.fadeOut()
        } else {
            ui.emptyState.fadeOut()
            ui.list.fadeIn()
        }
    }

    private fun onAddExchangeFailed() {
        Toast.makeText(requireContext(), R.string.exchange_add_error, LENGTH_LONG).show()
    }

    private fun onListExchangeFailed() {
        Toast.makeText(requireContext(), R.string.exchange_list_error, LENGTH_LONG).show()
    }

    override fun onExchangeSelected(item: ExchangeItem) {
        throw AssertionError("must not get triggered here")
    }

    override fun onManualWithdraw(item: ExchangeItem) {
        model.withdrawManager.resetWithdrawal()
        val args = bundleOf(
            "editableCurrency" to false,
            "exchangeBaseUrl" to item.exchangeBaseUrl,
            "amount" to item.currency?.let { Amount.zero(it).toJSONString() },
        )
        findNavController().navigate(R.id.promptWithdraw, args)
    }

    override fun onPeerReceive(item: ExchangeItem) {
        transactionManager.selectScope(item.scopeInfo)
        findNavController().navigate(R.id.nav_peer_pull)
    }

    override fun onExchangeReload(item: ExchangeItem) {
        exchangeManager.reload(item.exchangeBaseUrl)
    }

    override fun onExchangeDelete(item: ExchangeItem) {
        val optionsArray = arrayOf(getString(R.string.exchange_delete_force))
        val checkedArray = BooleanArray(1) { false }

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Material3)
            .setTitle(R.string.exchange_delete)
            .setMultiChoiceItems(optionsArray, checkedArray) { _, which, isChecked ->
                checkedArray[which] = isChecked
            }
            .setNegativeButton(R.string.transactions_delete) { _, _ ->
                exchangeManager.delete(item.exchangeBaseUrl, checkedArray[0])
            }
            .setPositiveButton(R.string.cancel) { _, _ -> }
            .show()
    }

    override fun onExchangeTosView(item: ExchangeItem) {
        val bundle = bundleOf(
            "exchangeBaseUrl" to item.exchangeBaseUrl,
            "readOnly" to true,
        )
        findNavController().navigate(R.id.action_global_reviewExchangeTos, bundle)
    }

    override fun onExchangeTosAccept(item: ExchangeItem) {
        val bundle = bundleOf("exchangeBaseUrl" to item.exchangeBaseUrl)
        findNavController().navigate(R.id.action_global_reviewExchangeTos, bundle)
    }

    override fun onExchangeTosForget(item: ExchangeItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            exchangeManager.getExchangeTos(item.exchangeBaseUrl)?.let { tos ->
                exchangeManager.forgetCurrentTos(item.exchangeBaseUrl, tos.currentEtag)
            }
        }
    }

    override fun onExchangeGlobalCurrencyAdd(item: ExchangeItem) {
        item.currency?.let {
            balanceManager.addGlobalCurrencyExchange(
                currency = item.currency,
                exchange = item,
                onSuccess = {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.exchange_global_add_success),
                        Snackbar.LENGTH_LONG
                    ).show()
                },
                onError = { error ->
                    showError(error)
                },
            )
        }
    }

    override fun onExchangeGlobalCurrencyDelete(item: ExchangeItem) {
        item.currency?.let {
            balanceManager.removeGlobalCurrencyExchange(
                currency = item.currency,
                exchange = item,
                onSuccess = {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.exchange_global_delete_success),
                        Snackbar.LENGTH_LONG
                    ).show()
                },
                onError = { error ->
                    showError(error)
                },
            )
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(ui.list) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right,
            )
            WindowInsetsCompat.CONSUMED
        }

        val fabMarginBottom = ui.addExchangeFab.marginBottom
        val fabMarginLeft = ui.addExchangeFab.marginLeft
        val fabMarginRight = ui.addExchangeFab.marginRight
        ViewCompat.setOnApplyWindowInsetsListener(ui.addExchangeFab) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = fabMarginBottom + insets.bottom
                leftMargin = fabMarginLeft + insets.left
                rightMargin = fabMarginRight + insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }
}
