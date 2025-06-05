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

package net.taler.wallet.payment

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import net.taler.common.ContractProduct
import net.taler.common.base64Bitmap
import net.taler.wallet.R
import net.taler.wallet.payment.ProductAdapter.ProductViewHolder

internal interface ProductImageClickListener {
    fun onImageClick(image: Bitmap)
}

internal class ProductAdapter(private val listener: ProductImageClickListener) :
    RecyclerView.Adapter<ProductViewHolder>() {

    private var items = emptyList<ContractProduct>()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.list_item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun update(newItems: List<ContractProduct>) {
        val oldItems = this.items

        val diffCallback = ProductDiffCallback(oldItems, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        diffResult.dispatchUpdatesTo(this)

        items = newItems
    }

    internal inner class ProductViewHolder(v: View) : ViewHolder(v) {
        private val context: Context = v.context

        private val quantity: TextView = v.findViewById(R.id.quantity)
        private val image: ImageView = v.findViewById(R.id.image)
        private val name: TextView = v.findViewById(R.id.name)
        private val taxes: TextView = v.findViewById(R.id.taxes)
        private val price: TextView = v.findViewById(R.id.price)

        fun bind(product: ContractProduct) {
            quantity.text = product.quantity.toString()

            // base64 encoded image
            val bitmap = product.image?.base64Bitmap
            if (bitmap == null) {
                image.visibility = GONE
            } else {
                image.visibility = VISIBLE
                image.setImageBitmap(bitmap)
                image.setOnClickListener {
                    listener.onImageClick(bitmap)
                }
            }

            name.text = product.description

            if (product.totalPrice != null) {
                price.visibility = VISIBLE
                price.text = product.totalPrice.toString()
            } else {
                price.visibility = GONE
            }
    
            if (product.taxes != null && product.taxes!!.isNotEmpty()) {
                taxes.visibility = VISIBLE
                taxes.text = product.taxes!!.filter {
                    !it.tax.isZero()
                }.joinToString(separator = "\n") {
                    context.getString(R.string.payment_tax, it.name, it.tax)
                }
            } else {
                taxes.visibility = GONE
            }
        }
    }
}

internal class ProductDiffCallback(
    private val oldList: List<ContractProduct>,
    private val newList: List<ContractProduct>,
): DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]

        return old.productId == new.productId
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]

        return old == new
    }
}