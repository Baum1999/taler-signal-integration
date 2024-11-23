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

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import net.taler.wallet.R
import net.taler.wallet.balances.ScopeInfo
import net.taler.wallet.exchanges.ExchangeAdapter.ExchangeItemViewHolder

interface ExchangeClickListener {
    fun onExchangeSelected(item: ExchangeItem)
    fun onManualWithdraw(item: ExchangeItem)
    fun onPeerReceive(item: ExchangeItem)
    fun onExchangeReload(item: ExchangeItem)
    fun onExchangeDelete(item: ExchangeItem)
    fun onExchangeTosAccept(item: ExchangeItem)
    fun onExchangeTosForget(item: ExchangeItem)
    fun onExchangeTosView(item: ExchangeItem)
    fun onExchangeGlobalCurrencyAdd(item: ExchangeItem)
    fun onExchangeGlobalCurrencyDelete(item: ExchangeItem)
}

internal class ExchangeAdapter(
    private val selectOnly: Boolean,
    private val listener: ExchangeClickListener,
    private val devMode: Boolean,
) : Adapter<ExchangeItemViewHolder>() {

    private var items = emptyList<ExchangeItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExchangeItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_exchange, parent, false)
        return ExchangeItemViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ExchangeItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun update(newItems: List<ExchangeItem>) {
        val oldItems = this.items

        val diffCallback = ExchangeDiffCallback(oldItems, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        diffResult.dispatchUpdatesTo(this)

        items = newItems
    }

    internal inner class ExchangeItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val context = v.context
        private val urlView: TextView = v.findViewById(R.id.urlView)
        private val currencyView: TextView = v.findViewById(R.id.currencyView)
        private val overflowIcon: ImageButton = v.findViewById(R.id.overflowIcon)

        fun bind(item: ExchangeItem) {
            urlView.text = item.name
            // If currency is null, it's because we have no data from the exchange...
            currencyView.text = if (item.currency == null) {
                context.getString(R.string.exchange_not_contacted)
            } else {
                context.getString(R.string.exchange_list_currency, item.currency)
            }
            if (selectOnly) {
                itemView.setOnClickListener { listener.onExchangeSelected(item) }
                overflowIcon.visibility = GONE
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                // ...thus, we should prevent the user from interacting with it.
                overflowIcon.visibility = if (item.currency != null) VISIBLE else GONE
            }
            overflowIcon.setOnClickListener { openMenu(overflowIcon, item) }
        }

        private fun openMenu(anchor: View, item: ExchangeItem) = PopupMenu(context, anchor).apply {
            inflate(R.menu.exchange)
            if (item.tosStatus == ExchangeTosStatus.Accepted) {
                menu.findItem(R.id.action_view_tos).isVisible = true
                menu.findItem(R.id.action_accept_tos).isVisible = false
                menu.findItem(R.id.action_forget_tos).isVisible = devMode
            } else {
                menu.findItem(R.id.action_view_tos).isVisible = false
                menu.findItem(R.id.action_accept_tos).isVisible = true
                menu.findItem(R.id.action_forget_tos).isVisible = false
            }

            if (item.scopeInfo is ScopeInfo.Exchange) {
                menu.findItem(R.id.action_global_add).isVisible = devMode
                menu.findItem(R.id.action_global_delete).isVisible = false
            } else if (item.scopeInfo is ScopeInfo.Global) {
                menu.findItem(R.id.action_global_add).isVisible = false
                menu.findItem(R.id.action_global_delete).isVisible = devMode
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_manual_withdrawal -> {
                        listener.onManualWithdraw(item)
                        true
                    }
                    R.id.action_receive_peer -> {
                        listener.onPeerReceive(item)
                        true
                    }
                    R.id.action_reload -> {
                        listener.onExchangeReload(item)
                        true
                    }
                    R.id.action_view_tos -> {
                        listener.onExchangeTosView(item)
                        true
                    }
                    R.id.action_accept_tos -> {
                        listener.onExchangeTosAccept(item)
                        true
                    }
                    R.id.action_forget_tos -> {
                        listener.onExchangeTosForget(item)
                        true
                    }
                    R.id.action_global_add -> {
                        listener.onExchangeGlobalCurrencyAdd(item)
                        true
                    }
                    R.id.action_global_delete -> {
                        listener.onExchangeGlobalCurrencyDelete(item)
                        true
                    }
                    R.id.action_delete -> {
                        listener.onExchangeDelete(item)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }
}

internal class ExchangeDiffCallback(
    private val oldList: List<ExchangeItem>,
    private val newList: List<ExchangeItem>,
): DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]

        return old.exchangeBaseUrl == new.exchangeBaseUrl
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]

        return old == new
    }
}