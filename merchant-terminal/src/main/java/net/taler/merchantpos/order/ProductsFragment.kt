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
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import net.taler.lib.android.base64Bitmap
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.config.ConfigProduct
import net.taler.merchantpos.databinding.FragmentProductsBinding
import net.taler.merchantpos.order.ProductAdapter.ProductViewHolder

interface ProductSelectionListener {
    fun onProductSelected(product: ConfigProduct)
}

class ProductsFragment : Fragment(), ProductSelectionListener {

    private val viewModel: MainViewModel by activityViewModels()
    private val orderManager by lazy { viewModel.orderManager }
    private val adapter = ProductAdapter(this)

    private lateinit var ui: FragmentProductsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ui = FragmentProductsBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.productsList.apply {
            adapter = this@ProductsFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        orderManager.products.observe(viewLifecycleOwner, { products ->
            if (products == null) {
                adapter.setItems(emptyList())
            } else {
                adapter.setItems(products)
            }
            ui.progressBar.visibility = INVISIBLE
        })
    }

    override fun onProductSelected(product: ConfigProduct) {
        orderManager.addProduct(orderManager.currentOrderId.value!!, product)
        viewModel.configManager.refreshInventory()
    }

}

private class ProductAdapter(
    private val listener: ProductSelectionListener
) : Adapter<ProductViewHolder>() {
    init {
        setHasStableIds(true)
    }

    private val itemCallback = object : ItemCallback<ConfigProduct>() {
        override fun areItemsTheSame(oldItem: ConfigProduct, newItem: ConfigProduct): Boolean {
            return oldItem.stableKey == newItem.stableKey
        }

        override fun areContentsTheSame(oldItem: ConfigProduct, newItem: ConfigProduct): Boolean {
            return oldItem.displayName == newItem.displayName &&
                oldItem.displayDescription == newItem.displayDescription &&
                oldItem.displayPrice == newItem.displayPrice &&
                oldItem.image == newItem.image &&
                oldItem.availableToSell == newItem.availableToSell &&
                oldItem.remainingStock == newItem.remainingStock
        }
    }
    private val differ = AsyncListDiffer(this, itemCallback)

    override fun getItemCount() = differ.currentList.size

    override fun getItemId(position: Int): Long {
        return differ.currentList[position].stableKey.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(differ.currentList[position])
    }

    fun setItems(items: List<ConfigProduct>) {
        differ.submitList(items.toList())
    }

    inner class ProductViewHolder(private val v: View) : ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.name)
        private val description: TextView = v.findViewById(R.id.description)
        private val price: TextView = v.findViewById(R.id.price)
        private val image: ImageView = v.findViewById(R.id.image)
        private val unavailable: TextView = v.findViewById(R.id.unavailableLabel)
        private val card: MaterialCardView = v as MaterialCardView

        fun bind(product: ConfigProduct) {
            name.text = product.displayName
            val productDescription = product.displayDescription
            if (productDescription == null) {
                description.visibility = GONE
            } else {
                description.visibility = VISIBLE
                description.text = productDescription
            }
            price.text = product.displayPrice

            // base64 encoded image
            val bitmap = product.image?.base64Bitmap
            if (bitmap == null) {
                image.visibility = GONE
            } else {
                image.visibility = VISIBLE
                image.setImageBitmap(bitmap)
            }

            unavailable.visibility = if (product.availableToSell) GONE else VISIBLE
            unavailable.text = when {
                product.availableToSell -> ""
                product.remainingStock == 0 -> v.context.getString(R.string.product_out_of_stock)
                else -> v.context.getString(R.string.product_unavailable)
            }
            card.isEnabled = product.availableToSell
            v.isEnabled = product.availableToSell
            v.alpha = if (product.availableToSell) 1f else 0.5f
            v.setOnClickListener {
                if (product.availableToSell) listener.onProductSelected(product)
            }
        }
    }

}
