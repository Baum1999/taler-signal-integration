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

package net.taler.lib.android

import android.content.res.ColorStateList
import android.content.DialogInterface
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.content.res.use
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.taler.common.Challenge
import net.taler.common.R
import android.graphics.drawable.GradientDrawable
import kotlin.coroutines.resume

enum class ChallengeRetryDecision {
    Retry,
    Resend,
    Abort
}

class ChallengeCancelledException : Exception()

suspend fun Fragment.handleChallengeResponse(
    challenges: List<Challenge>,
    combiAnd: Boolean,
    onRequestChallenge: suspend (challengeId: String) -> Unit,
    onConfirmChallenge: suspend (challengeId: String, tan: String) -> Unit,
): List<String> {
    if (challenges.isEmpty()) return emptyList()
    val challengesToSolve = if (combiAnd) {
        challenges
    } else {
        val selected = selectChallenge(challenges) ?: return emptyList()
        listOf(selected)
    }

    val solvedIds = mutableListOf<String>()
    for (challenge in challengesToSolve) {
        onRequestChallenge(challenge.challengeId)

        while (true) {
            val tan = promptForTan(challenge) ?: return emptyList()
            try {
                onConfirmChallenge(challenge.challengeId, tan)
                solvedIds.add(challenge.challengeId)
                break
            } catch (e: Exception) {
                when (handleChallengeConfirmError(e)) {
                    ChallengeRetryDecision.Retry -> continue
                    ChallengeRetryDecision.Resend -> {
                        onRequestChallenge(challenge.challengeId)
                        continue
                    }
                    ChallengeRetryDecision.Abort -> return emptyList()
                }
            }
        }
    }
    return solvedIds
}

suspend fun Fragment.selectChallenge(challenges: List<Challenge>): Challenge? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val buttonContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val density = resources.displayMetrics.density
                val horizontalPadding = (24 * density).toInt()
                val verticalPadding = (8 * density).toInt()
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0)
            }
            var dialog: androidx.appcompat.app.AlertDialog? = null
            challenges.forEach { challenge ->
                val button = MaterialButton(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = dp(8)
                    }
                    text = "${challenge.tanChannel}: ${challenge.tanInfo}"
                    backgroundTintList = colorStateListFromAttr(androidx.appcompat.R.attr.colorPrimary)
                    setTextColor(colorFromAttr(com.google.android.material.R.attr.colorOnPrimary))
                    isAllCaps = false
                    cornerRadius = dp(10)
                    setOnClickListener {
                        if (cont.isActive) cont.resume(challenge)
                        dialog?.dismiss()
                    }
                }
                buttonContainer.addView(button)
            }
            dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mfa_choose_title)
                .setView(buttonContainer)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    if (cont.isActive) cont.resume(null)
                }
                .setOnCancelListener {
                    if (cont.isActive) cont.resume(null)
                }
                .show()
            styleMfaDialogActions(dialog)
        }
    }

suspend fun Fragment.promptForTan(challenge: Challenge): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val message = getString(
                R.string.mfa_challenge_message,
                challenge.tanChannel.name,
                challenge.tanInfo
            )
            val dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_mfa_challenge,
                null,
                false
            )
            val messageView = dialogView.findViewById<TextView>(R.id.mfaMessageView)
            val errorView = dialogView.findViewById<TextView>(R.id.mfaCodeErrorView)
            val inputs = listOf(
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit1),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit2),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit3),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit4),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit5),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit6),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit7),
                dialogView.findViewById<EditText>(R.id.mfaCodeDigit8),
            )
            messageView.text = message
            errorView.visibility = GONE
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mfa_challenge_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    cont.resume(null)
                }
                .setOnCancelListener { cont.resume(null) }
                .show()
            fun submitCode(): Boolean {
                val code = collectMfaCode(inputs)
                if (code == null) {
                    errorView.text = getString(R.string.mfa_challenge_code_incomplete)
                    errorView.visibility = VISIBLE
                    return false
                }
                errorView.visibility = GONE
                cont.resume(code)
                dialog.dismiss()
                return true
            }
            setupMfaCodeInputs(inputs, errorView, ::submitCode)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                submitCode()
            }
            styleMfaDialogActions(dialog)
            inputs.firstOrNull()?.requestFocus()
        }
    }

suspend fun Fragment.handleChallengeConfirmError(e: Exception): ChallengeRetryDecision =
    withContext(Dispatchers.Main) {
        if (e is ClientRequestException) {
            when (e.response.status.value) {
                409 -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.mfa_challenge_invalid,
                        Toast.LENGTH_LONG
                    ).show()
                    return@withContext ChallengeRetryDecision.Retry
                }
                429 -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.mfa_challenge_retry,
                        Toast.LENGTH_LONG
                    ).show()
                    return@withContext ChallengeRetryDecision.Resend
                }
            }
        }
        Toast.makeText(
            requireContext(),
            R.string.mfa_challenge_failed,
            Toast.LENGTH_LONG
        ).show()
        ChallengeRetryDecision.Abort
    }

private fun setupMfaCodeInputs(
    inputs: List<EditText>,
    errorView: TextView,
    onSubmit: () -> Boolean,
) {
    var updatingInputs = false
    inputs.forEachIndexed { index, input ->
        input.filters = input.filters
            .filterNot { it is InputFilter.LengthFilter }
            .toTypedArray()
        input.imeOptions = if (index == inputs.lastIndex) {
            EditorInfo.IME_ACTION_DONE
        } else {
            EditorInfo.IME_ACTION_NEXT
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (updatingInputs) return
                errorView.visibility = GONE
                val value = s?.toString().orEmpty()
                if (value.length > 1) {
                    val pastedCode = normalizeMfaCode(value)
                    updatingInputs = true
                    try {
                        if (pastedCode == null) {
                            input.text?.clear()
                        } else {
                            inputs.forEachIndexed { digitIndex, digitInput ->
                                digitInput.setText(pastedCode[digitIndex].toString())
                            }
                        }
                    } finally {
                        updatingInputs = false
                    }
                    if (pastedCode != null) {
                        inputs.last().apply {
                            requestFocus()
                            setSelection(text?.length ?: 0)
                        }
                    }
                } else if (value.length == 1 && index < inputs.lastIndex) {
                    inputs[index + 1].requestFocus()
                }
            }
        })
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL &&
                event.action == KeyEvent.ACTION_DOWN &&
                input.text.isNullOrEmpty() &&
                index > 0
            ) {
                inputs[index - 1].apply {
                    requestFocus()
                    text?.clear()
                }
                true
            } else {
                false
            }
        }
        input.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP
            when {
                actionId == EditorInfo.IME_ACTION_NEXT && index < inputs.lastIndex -> {
                    inputs[index + 1].requestFocus()
                    true
                }
                actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    isEnter -> onSubmit()
                else -> false
            }
        }
    }
}

internal fun normalizeMfaCode(value: String): String? {
    val digits = value.filter(Char::isDigit)
    return digits.takeIf { it.length == 8 }
}

private fun collectMfaCode(inputs: List<EditText>): String? {
    val digits = inputs.map { it.text?.toString().orEmpty() }
    if (digits.any { it.length != 1 }) return null
    return digits.take(4).joinToString("") + "-" + digits.drop(4).joinToString("")
}

private fun Fragment.styleMfaDialogActions(dialog: androidx.appcompat.app.AlertDialog) {
    styleDialogActionButton(
        button = dialog.getButton(DialogInterface.BUTTON_POSITIVE),
        backgroundAttr = androidx.appcompat.R.attr.colorPrimary,
        textAttr = com.google.android.material.R.attr.colorOnPrimary,
    )
    styleDialogActionButton(
        button = dialog.getButton(DialogInterface.BUTTON_NEGATIVE),
        backgroundAttr = com.google.android.material.R.attr.colorPrimaryContainer,
        textAttr = com.google.android.material.R.attr.colorOnPrimaryContainer,
    )
}

private fun Fragment.styleDialogActionButton(
    button: Button?,
    backgroundAttr: Int,
    textAttr: Int,
) {
    button ?: return
    val backgroundColor = colorFromAttr(backgroundAttr)
    button.isAllCaps = false
    button.minHeight = dp(40)
    button.minWidth = dp(96)
    button.setPadding(dp(16), 0, dp(16), 0)
    button.setTextColor(colorFromAttr(textAttr))
    button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
    button.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(backgroundColor)
    }
}

private fun Fragment.colorFromAttr(attr: Int): Int {
    return requireContext().obtainStyledAttributes(intArrayOf(attr)).use {
        it.getColor(0, 0)
    }
}

private fun Fragment.colorStateListFromAttr(attr: Int): ColorStateList {
    return ColorStateList.valueOf(colorFromAttr(attr))
}

private fun Fragment.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
