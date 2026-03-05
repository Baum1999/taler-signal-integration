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

package net.taler.merchantpos

import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_HOME
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat.START
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener
import net.taler.lib.android.TalerNfcService
import net.taler.merchantpos.config.Config
import net.taler.merchantpos.config.ConfigUpdateResult
import net.taler.merchantpos.databinding.ActivityMainBinding
import android.util.Log

class MainActivity : AppCompatActivity(), OnNavigationItemSelectedListener {

    private val model: MainViewModel by viewModels()

    private lateinit var ui: ActivityMainBinding
    private lateinit var nav: NavController

    private var reallyExit = false

    companion object {
        const val TAG = "taler-pos"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        TalerNfcService.startService(this)

        model.paymentManager.payment.observe(this) { payment ->
            payment?.talerPayUri?.let {
                TalerNfcService.setUri(this, it)
            } ?: run {
                TalerNfcService.clearNdefPayload(this)
            }
        }

        // new: if we ever see a 401, fire this and kick back to settings
        model.configManager.sessionExpired.observe(this) {
            Toast
                .makeText(this, R.string.session_expired_toast, Toast.LENGTH_LONG)
                .show()
            nav.navigate(R.id.action_global_merchantSettings)
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        nav = navHostFragment.navController

        ui.navView.setupWithNavController(nav)
        ui.navView.setNavigationItemSelectedListener(this)

        setSupportActionBar(ui.main.toolbar)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_order,
                R.id.nav_amountEntry,
                R.id.nav_history,
                R.id.nav_settings,
            ),
            ui.drawerLayout,
        )
        ui.main.toolbar.setupWithNavController(nav, appBarConfiguration)

        handleSetupIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!model.configManager.config.isValid()) {
            if (nav.currentDestination?.id != R.id.nav_settings)
                nav.navigate(R.id.action_global_merchantSettings)
        } else if (model.configManager.merchantConfig == null
                && nav.currentDestination?.id != R.id.configFetcher) {
            nav.navigate(R.id.action_global_configFetcher)
        }
    }

    override fun onResume() {
        super.onResume()
        TalerNfcService.setDefaultHandler(this)
    }

    override fun onPause() {
        super.onPause()
        TalerNfcService.unsetDefaultHandler(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        TalerNfcService.stopService(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_order   -> nav.navigate(R.id.action_global_order)
            R.id.nav_amountEntry -> nav.navigate(R.id.action_global_amountEntry)
            R.id.nav_history -> nav.navigate(R.id.action_global_merchantHistory)
            R.id.nav_settings-> nav.navigate(R.id.action_global_merchantSettings)
        }
        ui.drawerLayout.closeDrawer(START)
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSetupIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentDestination = nav.currentDestination?.id
        if (ui.drawerLayout.isDrawerOpen(START)) {
            ui.drawerLayout.closeDrawer(START)
        } else if ((currentDestination == R.id.nav_settings || currentDestination == R.id.nav_instanceSettings)
                && !model.configManager.config.isValid()) {
            // we are in settings and need a valid config to continue
            val intent = Intent(ACTION_MAIN).apply {
                addCategory(CATEGORY_HOME)
                flags = FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } else if (currentDestination == R.id.nav_order || currentDestination == R.id.nav_amountEntry) {
            if (reallyExit) super.onBackPressed()
            else {
                // this closes the app and causes orders to be lost, so let's confirm first
                reallyExit = true
                Toast.makeText(this, R.string.toast_back_to_exit, LENGTH_SHORT).show()
                Handler().postDelayed({ reallyExit = false }, 3000)
            }
        } else super.onBackPressed()
    }

    /**
     * Handle the setup intent from the URL scheme. E.g. scanned the QR code from the camera
     *
     * This is the URL format:
     * taler-pos://backend.demo.taler.net/#/username=<username>&password=<password>
     */
     fun handleSetupIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != "taler-pos") return

        val host = data.host ?: return

        val params = data.fragment
            ?.removePrefix("/")
            ?.split('&')
            ?.associate { part ->
                part.split('=', limit = 2).let { it[0] to Uri.decode(it.getOrElse(1) { "" }) }
            } ?: return

        val instance = params["username"] ?: return
        val token    = params["password"] ?: return

        // Build a regular Merchant-API URL:  https://<host>/instances/<instance>
        val merchantUrl = Uri.Builder()
            .scheme("https")
            .encodedAuthority(host)
            .appendPath("instances")
            .appendPath(instance)
            .build()
            .toString()

        // Re-use the existing “new config” class
        val newConfig = Config.New(
            merchantUrl  = merchantUrl,
            accessToken  = token,
            savePassword = true
        )

        Log.d("MainActivity", "Config URL: $merchantUrl")

        //add check that there was no config beforehand
        model.configManager.config = newConfig

        // Kick off the exact same pipeline the Settings screen would start
        model.configManager.fetchConfig(newConfig, /*save =*/ true)

        // Show the spinner immediately
        if (nav.currentDestination?.id != R.id.configFetcher) {
            nav.navigate(R.id.action_global_configFetcher)
        }

        // Observe for result
        model.configManager.configUpdateResult.observe(this) { result ->
            if (result is ConfigUpdateResult.Success) {
                Log.d("MainActivity", "Config loaded successfully")
                model.configManager.configUpdateResult.removeObservers(this)
            } else if (result is ConfigUpdateResult.Error) {
                Log.e("MainActivity", "Config failed: ${result.msg}")
                model.configManager.configUpdateResult.removeObservers(this)
                Toast.makeText(this, result.msg, Toast.LENGTH_LONG).show()
            }
        }
    }

}
