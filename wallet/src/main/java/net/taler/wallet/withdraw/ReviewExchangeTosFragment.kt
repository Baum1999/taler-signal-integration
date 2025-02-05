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

package net.taler.wallet.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import net.taler.common.fadeIn
import net.taler.common.fadeOut
import net.taler.wallet.MainViewModel
import net.taler.wallet.R
import net.taler.wallet.databinding.FragmentReviewExchangeTosBinding
import java.text.ParseException
import java.util.Locale

class ReviewExchangeTosFragment : Fragment(), AdapterView.OnItemSelectedListener {

    private val model: MainViewModel by activityViewModels()
    private val exchangeManager by lazy { model.exchangeManager }

    private lateinit var ui: FragmentReviewExchangeTosBinding
    private val markwon by lazy { Markwon.builder(requireContext()).build() }
    private val adapter by lazy { TosAdapter(markwon) }

    private var tos: TosResponse? = null
    private var exchangeBaseUrl: String? = null
    private var langAdapter: ArrayAdapter<String>? = null
    private var selectedLang: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentReviewExchangeTosBinding.inflate(inflater, container, false)
        return ui.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets()

        exchangeBaseUrl = arguments?.getString("exchangeBaseUrl")
            ?: error("no exchangeBaseUrl passed")
        val readOnly = arguments?.getBoolean("readOnly") ?: false

        langAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item)
        langAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ui.langSpinner.adapter = langAdapter
        ui.langSpinner.onItemSelectedListener = this

        ui.buttonCard.visibility = if (readOnly) GONE else VISIBLE
        ui.acceptTosCheckBox.isChecked = false
        ui.acceptTosCheckBox.setOnCheckedChangeListener { _, _ ->
            tos?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (exchangeManager.acceptCurrentTos(
                        exchangeBaseUrl = exchangeBaseUrl!!,
                        currentEtag = it.currentEtag,
                    )) {
                        findNavController().navigateUp()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                renderTos(exchangeBaseUrl!!, selectedLang)
            }
        }
    }

    private suspend fun renderTos(
        exchangeBaseUrl: String,
        language: String? = null,
    ) {
        val lc = Locale.getDefault().language
        selectedLang = language ?: lc
        tos = exchangeManager.getExchangeTos(exchangeBaseUrl, selectedLang)

        // Setup language adapter
        val languages = tos?.tosAvailableLanguages ?: emptyList()
        langAdapter?.clear()
        langAdapter?.addAll(languages.map { lang ->
            Locale(lang).displayLanguage
        })
        langAdapter?.notifyDataSetChanged()

        // Setup language spinner
        if (languages.size > 1) {
            ui.langSpinner.visibility = VISIBLE
            val i = languages.indexOf(selectedLang)
            if (i >= 0) {
                ui.langSpinner.setSelection(i)
            }
        } else {
            ui.langSpinner.visibility = GONE
        }

        // FIXME: better null handling!
        tos?.let {
            val sections = try {
                parseTos(markwon, it.content)
            } catch (e: ParseException) {
                onTosError(e.message ?: "Unknown Error")
                return
            }

            adapter.setSections(sections)
            ui.tosList.adapter = adapter
            ui.tosList.fadeIn()

            ui.acceptTosCheckBox.fadeIn()
            ui.progressBar.fadeOut()
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(ui.tosList) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom,
            )
            windowInsets
        }

        val checkboxMarginLeft = ui.acceptTosCheckBox.marginLeft
        val checkboxMarginRight = ui.acceptTosCheckBox.marginRight
        val checkboxMarginBottom = ui.acceptTosCheckBox.marginBottom
        ViewCompat.setOnApplyWindowInsetsListener(ui.acceptTosCheckBox) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = checkboxMarginLeft + insets.left
                rightMargin = checkboxMarginRight + insets.right
                bottomMargin = checkboxMarginBottom + insets.bottom
            }
            windowInsets
        }
    }

    private fun onTosError(msg: String) {
        ui.tosList.fadeIn()
        ui.progressBar.fadeOut()
        ui.acceptTosCheckBox.fadeIn()
        // ui.buttonCard.fadeOut()
        ui.errorView.text = getString(R.string.exchange_tos_error, "\n\n$msg")
        ui.errorView.fadeIn()
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        tos?.tosAvailableLanguages?.get(position)?.let { lang ->
            viewLifecycleOwner.lifecycleScope.launch {
                renderTos(exchangeBaseUrl!!, lang)
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

}
