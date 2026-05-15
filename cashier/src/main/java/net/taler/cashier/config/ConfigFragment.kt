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

package net.taler.cashier.config

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.taler.cashier.MainViewModel
import net.taler.cashier.R
import net.taler.cashier.databinding.FragmentConfigBinding
import net.taler.common.exhaustive
import net.taler.common.showError
import kotlin.coroutines.resume

private const val URL_BANK_TEST = "https://bank.demo.taler.net"
private const val URL_BANK_TEST_REGISTER = "https://bank.demo.taler.net/webui/#/register"

class ConfigFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val configManager by lazy { viewModel.configManager }

    private lateinit var ui: FragmentConfigBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentConfigBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            if (configManager.config.bankUrl.isBlank()) {
                ui.urlView.editText!!.setText(URL_BANK_TEST)
            } else {
                ui.urlView.editText!!.setText(configManager.config.bankUrl)
            }
            ui.usernameView.editText!!.setText(configManager.config.username)
            ui.passwordView.editText!!.setText(configManager.config.password)
        } else {
            ui.urlView.editText!!.setText(savedInstanceState.getCharSequence("urlView"))
            ui.usernameView.editText!!.setText(savedInstanceState.getCharSequence("usernameView"))
            ui.passwordView.editText!!.setText(savedInstanceState.getCharSequence("passwordView"))
        }
        ui.saveButton.setOnClickListener {
            val config = Config(
                bankUrl = ui.urlView.editText!!.text.toString().trim(),
                username = ui.usernameView.editText!!.text.toString().trim(),
                password = ui.passwordView.editText!!.text.toString().trim()
            )
            if (checkConfig(config)) {
                // show progress
                ui.saveButton.visibility = INVISIBLE
                ui.progressBar.visibility = VISIBLE
                // kick off check and observe result
                configManager.checkAndSaveConfig(config)
                configManager.configResult.observe(viewLifecycleOwner, onConfigResult)
                // hide keyboard
                val inputMethodManager =
                    getSystemService(requireContext(), InputMethodManager::class.java)!!
                inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        ui.demoView.text = HtmlCompat.fromHtml(
            getString(R.string.config_demo_hint, URL_BANK_TEST_REGISTER), FROM_HTML_MODE_LEGACY
        )
        ui.demoView.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onStart() {
        super.onStart()
        // focus on password if it is the only missing value (like after locking)
        if (ui.urlView.editText!!.text.isNotBlank()
            && ui.usernameView.editText!!.text.isNotBlank()
            && ui.passwordView.editText!!.text.isBlank()
        ) {
            ui.passwordView.editText!!.requestFocus()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // for some reason automatic restore isn't working at the moment!?
        outState.putCharSequence("urlView", ui.urlView.editText?.text?.trim())
        outState.putCharSequence("usernameView", ui.usernameView.editText?.text?.trim())
        outState.putCharSequence("passwordView", ui.passwordView.editText?.text?.trim())
    }

    private fun checkConfig(config: Config): Boolean {
        if (!config.bankUrl.startsWith("https://") &&
            !config.bankUrl.startsWith("http://")
        ) {
            ui.urlView.error = getString(R.string.config_bank_url_error)
            ui.urlView.requestFocus()
            return false
        }
        if (config.username.isBlank()) {
            ui.usernameView.error = getString(R.string.config_username_error)
            ui.usernameView.requestFocus()
            return false
        }
        ui.urlView.isErrorEnabled = false
        return true
    }

    private val onConfigResult = Observer<ConfigResult?> { result ->
        if (result == null) return@Observer
        when (result) {
            is ConfigResult.Success -> {
                val action = ConfigFragmentDirections.actionConfigFragmentToBalanceFragment()
                findNavController().navigate(action)
            }
            ConfigResult.Offline -> {
                Snackbar.make(requireView(), R.string.config_error_offline, LENGTH_LONG).show()
            }
            is ConfigResult.Error -> {
                if (result.authError) {
                    Snackbar.make(requireView(), R.string.config_error_auth, LENGTH_LONG).show()
                } else {
                    requireActivity().showError(getString(R.string.config_error), result.msg)
                }
            }
            is ConfigResult.TanRequired -> {
                lifecycleScope.launch {
                    val solvedIds = handleChallengeResponse(
                        ui.urlView.editText!!.text.toString().trim(),
                        ui.usernameView.editText!!.text.toString().trim(),
                        result.challenges,
                        result.combiAnd
                    )
                    if (solvedIds.isNotEmpty()) {
                        val config = Config(
                            bankUrl = ui.urlView.editText!!.text.toString().trim(),
                            username = ui.usernameView.editText!!.text.toString().trim(),
                            password = ui.passwordView.editText!!.text.toString().trim()
                        )
                        configManager.checkAndSaveConfig(config, solvedIds)
                    } else {
                        ui.saveButton.visibility = VISIBLE
                        ui.progressBar.visibility = INVISIBLE
                        configManager.configResult.removeObservers(viewLifecycleOwner)
                    }
                }
                return@Observer
            }
            ConfigResult.Unknown -> {
                requireActivity().showError(getString(R.string.config_error), "Unknown error")
            }
        }.exhaustive
        ui.saveButton.visibility = VISIBLE
        ui.progressBar.visibility = INVISIBLE
        configManager.configResult.removeObservers(viewLifecycleOwner)
    }

    private suspend fun handleChallengeResponse(
        baseUrl: String,
        username: String,
        challenges: List<Challenge>,
        combiAnd: Boolean,
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
            withContext(Dispatchers.IO) {
                configManager.requestChallenge(baseUrl, username, challenge.challengeId)
            }

            while (true) {
                val tan = promptForTan(challenge) ?: return emptyList()
                try {
                    withContext(Dispatchers.IO) {
                        configManager.confirmChallenge(
                            baseUrl,
                            username,
                            challenge.challengeId,
                            tan
                        )
                    }
                    solvedIds.add(challenge.challengeId)
                    break
                } catch (e: Exception) {
                    when (handleChallengeConfirmError(e)) {
                        ChallengeRetryDecision.Retry -> continue
                        ChallengeRetryDecision.Resend -> {
                            withContext(Dispatchers.IO) {
                                configManager.requestChallenge(
                                    baseUrl,
                                    username,
                                    challenge.challengeId
                                )
                            }
                            continue
                        }
                        ChallengeRetryDecision.Abort -> return emptyList()
                    }
                }
            }
        }
        return solvedIds
    }

    private suspend fun selectChallenge(challenges: List<Challenge>): Challenge? =
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

    private suspend fun promptForTan(challenge: Challenge): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val message = getString(
                    R.string.mfa_challenge_message,
                    challenge.tanChannel.name,
                    challenge.tanInfo
                )
                val dialogView = layoutInflater.inflate(
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

    private suspend fun handleChallengeConfirmError(e: Exception): ChallengeRetryDecision =
        withContext(Dispatchers.Main) {
            if (e is io.ktor.client.plugins.ClientRequestException) {
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

    private enum class ChallengeRetryDecision {
        Retry,
        Resend,
        Abort
    }

}
