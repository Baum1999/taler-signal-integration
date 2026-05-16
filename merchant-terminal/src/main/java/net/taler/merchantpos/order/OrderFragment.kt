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

package net.taler.merchantpos.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.transition.TransitionManager.beginDelayedTransition
import net.taler.lib.android.navigate
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.databinding.FragmentOrderBinding
import net.taler.merchantpos.order.OrderFragmentDirections.Companion.actionGlobalConfigFetcher
import net.taler.merchantpos.order.OrderFragmentDirections.Companion.actionOrderToMerchantSettings
import net.taler.merchantpos.order.OrderFragmentDirections.Companion.actionOrderToProcessPayment
import net.taler.merchantpos.order.RestartState.ENABLED
import net.taler.merchantpos.order.RestartState.UNDO

class OrderFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val paymentManager by lazy { viewModel.paymentManager }

    private lateinit var ui: FragmentOrderBinding
    private var billLabel: String = ""
    private var currentOrderId: Int? = null
    private var currentLiveOrder: LiveOrder? = null
    private var restartOrderItem: MenuItem? = null
    private var deleteOrderItem: MenuItem? = null
    private var previousOrderItem: MenuItem? = null
    private var nextOrderItem: MenuItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentOrderBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        billLabel = getString(R.string.order_complete)
        ui.completeButton.text = billLabel

        requireActivity().addMenuProvider(object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.order, menu)
                restartOrderItem = menu.findItem(R.id.orderRestart)
                deleteOrderItem = menu.findItem(R.id.orderDelete)
                previousOrderItem = menu.findItem(R.id.orderPrevious)
                nextOrderItem = menu.findItem(R.id.orderNext)
                updateOrderNavigationActions(currentOrderId)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when(menuItem.itemId) {
                    R.id.orderRestart -> {
                        currentLiveOrder?.restartOrUndo()
                        true
                    }
                    R.id.orderDelete -> {
                        orderManager.deleteCurrentOrder()
                        true
                    }

                    R.id.orderPrevious -> {
                        orderManager.previousOrder()
                        true
                    }

                    R.id.orderNext -> {
                        orderManager.nextOrder()
                        true
                    }

                    R.id.reload -> {
                        viewModel.configManager.reloadConfig()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_reloading),
                            Toast.LENGTH_LONG,
                        ).show()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        orderManager.currentOrderId.observe(viewLifecycleOwner) { orderId ->
            val liveOrder = orderManager.getOrder(orderId)
            onOrderSwitched(orderId, liveOrder)
            // add a new OrderStateFragment for each order
            // as switching its internals (like we do here) would be too messy
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment1, OrderStateFragment())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.configManager.config.isValid()) {
            navigate(actionOrderToMerchantSettings())
        } else if (viewModel.configManager.currency == null) {
            navigate(actionGlobalConfigFetcher())
        }
    }

    private fun onOrderSwitched(orderId: Int, liveOrder: LiveOrder) {
        currentOrderId = orderId
        currentLiveOrder = liveOrder
        updateOrderNavigationActions(orderId)
        // order title
        liveOrder.order.observe(viewLifecycleOwner) { order ->
            if (order == null) return@observe
            activity?.title = getString(R.string.order_label_title, order.title)
        }
        // restart action
        liveOrder.restartState.observe(viewLifecycleOwner) { state ->
            beginDelayedTransition(view as ViewGroup)
            if (state == UNDO) {
                restartOrderItem?.setTitle(R.string.order_undo)
                restartOrderItem?.isEnabled = true
                ui.completeButton.isEnabled = false
            } else {
                restartOrderItem?.setTitle(R.string.order_restart)
                restartOrderItem?.isEnabled = state == ENABLED
                ui.completeButton.isEnabled = state == ENABLED
            }
            deleteOrderItem?.isEnabled =
                state != RestartState.DISABLED ||
                    orderManager.hasPreviousOrder(orderId) ||
                    (orderManager.hasNextOrder(orderId).value == true)
        }
        liveOrder.orderTotal.observe(viewLifecycleOwner) { orderTotal ->
            ui.completeButton.text = if (orderTotal.isZero()) {
                billLabel
            } else {
                getString(R.string.order_complete_with_amount, orderTotal)
            }
        }
        // -1 and +1 buttons
        liveOrder.modifyOrderAllowed.observe(viewLifecycleOwner) { allowed ->
            ui.minusButton.isEnabled = allowed
        }
        liveOrder.increaseOrderAllowed.observe(viewLifecycleOwner) { allowed ->
            ui.plusButton.isEnabled = allowed
        }
        ui.minusButton.setOnClickListener { liveOrder.decreaseSelectedOrderLine() }
        ui.plusButton.setOnClickListener { liveOrder.increaseSelectedOrderLine() }
        ui.tipButton.setOnClickListener {
            CustomDialogFragment().show(childFragmentManager, CustomDialogFragment.TAG)
        }
        // previous and next order actions
        orderManager.hasNextOrder(orderId).observe(viewLifecycleOwner) { hasNextOrder ->
            if (currentOrderId == orderId) nextOrderItem?.isEnabled = hasNextOrder
        }
        // complete button
        ui.completeButton.setOnClickListener {
            val order = liveOrder.order.value ?: return@setOnClickListener
            paymentManager.createPayment(order)
            navigate(actionOrderToProcessPayment())
        }
    }

    private fun updateOrderNavigationActions(orderId: Int?) {
        previousOrderItem?.isEnabled = orderId?.let { orderManager.hasPreviousOrder(it) } ?: false
        nextOrderItem?.isEnabled = orderId?.let {
            orderManager.hasNextOrder(it).value ?: false
        } ?: false
    }

}
