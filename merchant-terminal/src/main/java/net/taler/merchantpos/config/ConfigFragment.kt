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

package net.taler.merchantpos.config

import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import net.taler.common.navigate
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.config.ConfigFragmentDirections.Companion.actionSettingsToOrder
import net.taler.merchantpos.databinding.FragmentMerchantConfigBinding

/**
 * Fragment that displays merchant settings.
 */
class ConfigFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val configManager by lazy { model.configManager }

    private lateinit var ui: FragmentMerchantConfigBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = FragmentMerchantConfigBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.configToggle.check(when (configManager.config) {
            is Config.Old -> R.id.oldConfigButton
            is Config.New -> R.id.newConfigButton
        })

        ui.oldConfigButton.setOnClickListener {
            showOldConfig()
        }

        ui.newConfigButton.setOnClickListener {
            showNewConfig()
        }

        /*
         * Old configuration (JSON)
         */

        ui.configUrlView.editText!!.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) checkForUrlCredentials()
        }

        ui.okOldButton.setOnClickListener {
            checkForUrlCredentials()
            val inputUrl = ui.configUrlView.editText!!.text
            val url = if (inputUrl.startsWith("http")) {
                inputUrl.toString()
            } else {
                "https://$inputUrl".also { ui.configUrlView.editText!!.setText(it) }
            }
            // ui.progressBarOld.visibility = VISIBLE
            ui.okOldButton.visibility = INVISIBLE
            val config = Config.Old(
                configUrl = url,
                username = ui.usernameView.editText!!.text.toString(),
                password = ui.passwordView.editText!!.text.toString(),
                savePassword = ui.savePasswordCheckBox.isChecked,
            )
            configManager.fetchConfig(config, true)
            configManager.configUpdateResult.observe(viewLifecycleOwner) { result ->
                if (onConfigUpdate(result)) {
                    configManager.configUpdateResult.removeObservers(viewLifecycleOwner)
                }
            }
        }

        ui.forgetPasswordButton.setOnClickListener {
            configManager.forgetPassword()
            ui.passwordView.editText!!.text = null
            ui.forgetPasswordButton.visibility = GONE
        }

        ui.configDocsView.movementMethod = LinkMovementMethod.getInstance()

        /*
         * New configuration (Merchant)
         */

        ui.okNewButton.setOnClickListener {
            val inputUrl = ui.merchantUrlView.editText!!.text
            val url = if (inputUrl.startsWith("http")) {
                inputUrl.toString()
            } else {
                "https://$inputUrl".also { ui.merchantUrlView.editText!!.setText(it) }
            }

            // ui.progressBarNew.visibility = VISIBLE
            ui.okNewButton.visibility = INVISIBLE
            val config = Config.New(
                merchantUrl = url,
                accessToken = ui.tokenView.editText!!.text.toString(),
                savePassword = ui.saveTokenCheckBox.isChecked,
            )
            configManager.fetchConfig(config, true)
            configManager.configUpdateResult.observe(viewLifecycleOwner) { result ->
                if (onConfigUpdate(result)) {
                    configManager.configUpdateResult.removeObservers(viewLifecycleOwner)
                }
            }
        }

        updateView(savedInstanceState == null)
    }

    override fun onStart() {
        super.onStart()
        // focus password if this is the only empty field
        if (ui.passwordView.editText!!.text.isBlank()
            && ui.configUrlView.editText!!.text.isNotBlank()
            && ui.usernameView.editText!!.text.isNotBlank()
        ) {
            ui.passwordView.requestFocus()
        }
    }

    private fun updateView(isInitialization: Boolean = false) {
        if (isInitialization) {
            ui.configUrlView.editText!!.setText(OLD_CONFIG_URL_DEMO)
            ui.usernameView.editText!!.setText(OLD_CONFIG_USERNAME_DEMO)
            ui.passwordView.editText!!.setText(OLD_CONFIG_PASSWORD_DEMO)

            ui.merchantUrlView.editText!!.setText(NEW_CONFIG_URL_DEMO)

            when (val config = configManager.config) {
                is Config.Old -> {
                    if (config.configUrl.isNotBlank()) {
                        ui.configUrlView.editText!!.setText(config.configUrl)
                    }

                    if (config.username.isNotBlank()) {
                        ui.usernameView.editText!!.setText(config.username)
                    }

                    ui.savePasswordCheckBox.isChecked = config.savePassword
                }

                is Config.New -> {
                    if (config.merchantUrl.isNotBlank()) {
                        ui.merchantUrlView.editText!!.setText(config.merchantUrl)
                    }

                    ui.saveTokenCheckBox.isChecked = config.savePassword
                }
            }
        }

        when (configManager.config) {
            is Config.Old -> {
                ui.configToggle.check(R.id.oldConfigButton)
                showOldConfig()
            }
            is Config.New -> {
                ui.configToggle.check(R.id.newConfigButton)
                showNewConfig()
            }
        }

    }

    private fun showOldConfig() {
        ui.oldConfigForm.visibility = VISIBLE
        ui.newConfigForm.visibility = GONE
    }

    private fun showNewConfig() {
        ui.oldConfigForm.visibility = GONE
        ui.newConfigForm.visibility = VISIBLE
    }

    private fun checkForUrlCredentials() {
        val text = ui.configUrlView.editText!!.text.toString()
        Uri.parse(text)?.userInfo?.let { userInfo ->
            if (userInfo.contains(':')) {
                val (user, pass) = userInfo.split(':')
                val strippedUrl = text.replace("${userInfo}@", "")
                ui.configUrlView.editText!!.setText(strippedUrl)
                ui.usernameView.editText!!.setText(user)
                ui.passwordView.editText!!.setText(pass)
            }
        }
    }

    /**
     * Processes updated config and returns true, if observer can be removed.
     */
    private fun onConfigUpdate(result: ConfigUpdateResult?) = when (result) {
        null -> false
        is ConfigUpdateResult.Error -> {
            onError(result.msg)
            true
        }
        is ConfigUpdateResult.Success -> {
            onConfigReceived(result.currency)
            true
        }
    }

    private fun onConfigReceived(currency: String) {
        onResultReceived()
        updateView()
        Snackbar.make(requireView(), getString(R.string.config_changed, currency), LENGTH_LONG).show()
        navigate(actionSettingsToOrder())
    }

    private fun onError(msg: String) {
        onResultReceived()
        Snackbar.make(requireView(), msg, LENGTH_LONG).show()
    }

    private fun onResultReceived() {
        ui.progressBarOld.visibility = INVISIBLE
        ui.okOldButton.visibility = VISIBLE
        ui.progressBarNew.visibility = INVISIBLE
        ui.okNewButton.visibility = VISIBLE
    }

}
