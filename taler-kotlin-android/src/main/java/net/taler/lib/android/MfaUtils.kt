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

import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.taler.common.Challenge
import net.taler.common.R
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
            val labels = challenges.map { c ->
                "${c.tanChannel}: ${c.tanInfo}"
            }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mfa_choose_title)
                .setItems(labels) { _, which ->
                    cont.resume(challenges[which])
                }
                .setOnCancelListener { cont.resume(null) }
                .show()
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
            val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.mfaCodeInputLayout)
            val input = dialogView.findViewById<TextInputEditText>(R.id.mfaCodeInput)
            messageView.text = message
            inputLayout.isErrorEnabled = false
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mfa_challenge_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    cont.resume(input?.text?.toString()?.trim().orEmpty())
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    cont.resume(null)
                }
                .setOnCancelListener { cont.resume(null) }
                .show()
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
