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

package net.taler.merchantpos

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val POS_ERROR_DIALOG_TAG = "POS_ERROR_DIALOG"

class PosErrorDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val mainText = args.getString(ARG_MAIN_TEXT).orEmpty()
        val detailText = args.getString(ARG_DETAIL_TEXT).orEmpty()
        val titleText = if (detailText.isBlank()) {
            getString(R.string.app_name_short)
        } else {
            mainText
        }
        val messageText = if (detailText.isBlank()) {
            mainText
        } else {
            detailText
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleText)
            .setMessage(messageText)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    companion object {
        private const val ARG_MAIN_TEXT = "main_text"
        private const val ARG_DETAIL_TEXT = "detail_text"

        fun newInstance(mainText: String, detailText: String = "") = PosErrorDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MAIN_TEXT, mainText)
                putString(ARG_DETAIL_TEXT, detailText)
            }
        }
    }
}

fun FragmentActivity.showPosError(mainText: String, detailText: String = "") {
    (supportFragmentManager.findFragmentByTag(POS_ERROR_DIALOG_TAG) as? DialogFragment)?.dismissAllowingStateLoss()
    PosErrorDialogFragment.newInstance(mainText, detailText)
        .show(supportFragmentManager, POS_ERROR_DIALOG_TAG)
}

fun FragmentActivity.showPosError(@StringRes mainId: Int, detailText: String = "") {
    showPosError(getString(mainId), detailText)
}

fun Fragment.showPosError(mainText: String, detailText: String = "") {
    requireActivity().showPosError(mainText, detailText)
}

fun Fragment.showPosError(@StringRes mainId: Int, detailText: String = "") {
    requireActivity().showPosError(mainId, detailText)
}
