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

package net.taler.wallet.link

import android.app.Activity
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.taler.wallet.R

/**
 * Einmaliger Consent-Dialog fuer eine Fremd-App, die sich mit dieser Wallet
 * verbinden will. Wird von der Fremd-App per explizitem Intent + `startActivityForResult`
 * gestartet (nur so ist [getCallingPackage] verlaesslich gesetzt). Kein
 * Intent-Filter - diese Activity ist nicht ueber `taler://`-Links erreichbar.
 *
 * Consent ersetzt die Cert-Allowlist-Pruefung nicht, sondern kommt danach:
 * ist der Aufrufer nicht in der Allowlist, wird sofort abgebrochen.
 */
class ConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callerPackage = callingPackage
        val certSha256 = callerPackage?.let {
            CallerVerification.signingCertSha256(packageManager, it)
        }
        val expected = callerPackage?.let { AllowedCallers.expectedSha256(it) }

        if (callerPackage == null || certSha256 == null || expected == null ||
            !certSha256.equals(expected, ignoreCase = true)
        ) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.taler_link_consent_title)
            .setMessage(getString(R.string.taler_link_consent_message, callerPackage))
            .setPositiveButton(R.string.taler_link_consent_allow) { _, _ ->
                ConsentStore(applicationContext).grant(callerPackage, certSha256)
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton(R.string.taler_link_consent_deny) { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setOnCancelListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }
}
