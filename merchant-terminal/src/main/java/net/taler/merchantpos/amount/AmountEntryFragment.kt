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

package net.taler.merchantpos.amount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.taler.common.Amount
import net.taler.common.navigate
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.amount.AmountEntryFragmentDirections.Companion.actionAmountEntryToProcessPayment
import net.taler.merchantpos.amount.AmountEntryFragmentDirections.Companion.actionGlobalConfigFetcher
import net.taler.merchantpos.amount.AmountEntryFragmentDirections.Companion.actionGlobalMerchantSettings
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.databinding.FragmentAmountEntryBinding
import net.taler.merchantpos.order.Order

private const val QUICK_AMOUNT_ORDER_ID = -1
private const val QUICK_AMOUNT_PRODUCT_ID = "quick_amount"

class AmountEntryFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val paymentManager by lazy { viewModel.paymentManager }

    private lateinit var ui: FragmentAmountEntryBinding

    private var selectedCurrency: String? = null
    private var amount: Amount? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentAmountEntryBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.configManager.config.isValid()) {
            navigate(actionGlobalMerchantSettings())
        } else if (viewModel.configManager.currency == null) {
            navigate(actionGlobalConfigFetcher())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val configuredCurrency = viewModel.configManager.currency
        if (configuredCurrency != null) {
            bindCurrency(listOf(configuredCurrency))
            setCurrency(configuredCurrency)
        }

        ui.key0.setOnClickListener { onDigitPressed('0') }
        ui.key1.setOnClickListener { onDigitPressed('1') }
        ui.key2.setOnClickListener { onDigitPressed('2') }
        ui.key3.setOnClickListener { onDigitPressed('3') }
        ui.key4.setOnClickListener { onDigitPressed('4') }
        ui.key5.setOnClickListener { onDigitPressed('5') }
        ui.key6.setOnClickListener { onDigitPressed('6') }
        ui.key7.setOnClickListener { onDigitPressed('7') }
        ui.key8.setOnClickListener { onDigitPressed('8') }
        ui.key9.setOnClickListener { onDigitPressed('9') }
        ui.keyClear.setOnClickListener { clearAmount() }
        ui.keyBackspace.setOnClickListener { onBackspacePressed() }

        ui.chargeButton.setOnClickListener { onChargePressed() }

        render()
    }

    private fun bindCurrency(currencies: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, currencies)
        ui.currencyView.setAdapter(adapter)
        ui.currencyView.setOnItemClickListener { _, _, position, _ ->
            val newCurrency = adapter.getItem(position) ?: return@setOnItemClickListener
            setCurrency(newCurrency)
        }
    }

    private fun setCurrency(currency: String) {
        selectedCurrency = currency
        val currentAmount = amount
        amount = when {
            currentAmount == null -> Amount.zero(currency)
            currentAmount.currency == currency -> currentAmount
            else -> currentAmount.withCurrency(currency)
        }
        ui.currencyView.setText(currency, false)
        render()
    }

    private fun onDigitPressed(digit: Char) {
        val currentAmount = amount ?: return
        amount = currentAmount.addInputDigit(digit) ?: currentAmount
        render()
    }

    private fun onBackspacePressed() {
        val currentAmount = amount ?: return
        amount = currentAmount.removeInputDigit() ?: currentAmount
        render()
    }

    private fun clearAmount() {
        val currency = selectedCurrency ?: return
        amount = Amount.zero(currency)
        render()
    }

    private fun onChargePressed() {
        val configuredCurrency = viewModel.configManager.currency ?: run {
            navigate(actionGlobalConfigFetcher())
            return
        }
        val enteredCurrency = selectedCurrency ?: configuredCurrency
        val enteredAmount = amount ?: Amount.zero(enteredCurrency)

        if (enteredAmount.isZero()) {
            Toast.makeText(requireContext(), R.string.amount_entry_error_zero, Toast.LENGTH_LONG)
                .show()
            return
        }
        if (enteredCurrency != configuredCurrency) {
            Toast.makeText(requireContext(), R.string.amount_entry_error_wrong_currency, Toast.LENGTH_LONG)
                .show()
            return
        }

        val order = Order(
            id = QUICK_AMOUNT_ORDER_ID,
            currency = configuredCurrency,
            availableCategories = emptyMap(),
        )
        val product = ConfigProduct(
            description = getString(R.string.amount_entry_product_description),
            productId = QUICK_AMOUNT_PRODUCT_ID,
            price = enteredAmount,
            categories = listOf(Int.MIN_VALUE),
        )
        order + product

        // Backend doesn't require products; omit them for this "quick amount" flow.
        paymentManager.createPayment(order, includeProducts = false)
        navigate(actionAmountEntryToProcessPayment())
    }

    private fun render() {
        val currentAmount = amount
        ui.amountView.text = currentAmount?.toString(showSymbol = false) ?: "0.00"
        val enabled = currentAmount != null && !currentAmount.isZero()
        ui.chargeButton.isEnabled = enabled
    }
}
