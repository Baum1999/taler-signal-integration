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

package net.taler.wallet

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build.VERSION.SDK_INT
import android.util.TypedValue
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.annotation.RequiresApi
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.taler.common.Amount
import net.taler.common.AmountParserException
import net.taler.common.showError
import net.taler.common.startActivitySafe
import net.taler.wallet.backend.TalerErrorInfo

const val CURRENCY_BTC = "BITCOINBTC"

fun connectToWifi(context: Context, ssid: String) {
    if (SDK_INT >= 29) {
        connectToWifi29(context, ssid)
    } else {
        connectToWifiDeprecated(context, ssid)
    }
}

@RequiresApi(29)
private fun connectToWifi29(context: Context, ssid: String) {
    val wifiManager = context.getSystemService(WifiManager::class.java)
    if (wifiManager?.isWifiEnabled == false) {
        // we are not allowed to enable the WiFi anymore, so show at least a hint about it
        Toast.makeText(context, R.string.wifi_disabled_error, LENGTH_LONG).show()
    }

    val specifier = WifiNetworkSpecifier.Builder()
        .setSsid(ssid)
        .build()
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .setNetworkSpecifier(specifier)
        .build()
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    connectivityManager?.requestNetwork(request, NetworkCallback())
}

@Suppress("DEPRECATION")
private fun connectToWifiDeprecated(context: Context, ssid: String) {
    context.getSystemService<WifiManager>()?.apply {
        if (!isWifiEnabled) {
            val enabledResult = setWifiEnabled(true)
            while (enabledResult && !isWifiEnabled) Thread.sleep(25)
        }
        val wifiConfig = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
        addNetwork(wifiConfig).let { netId ->
            if (netId == -1) {
                val str = context.getString(R.string.wifi_connect_error, ssid)
                Toast.makeText(context, str, LENGTH_LONG).show()
            } else {
                disconnect()
                enableNetwork(netId, true)
                reconnect()
            }
        }
    }
}

fun cleanExchange(exchange: String) = exchange.let {
    if (it.startsWith("https://", ignoreCase = true)) it.substring(8) else it
}.trimEnd('/')

fun getAmount(currency: String, text: String): Amount? {
    return try {
        Amount.fromString(currency, text)
    } catch (e: AmountParserException) {
        null
    }
}

fun Context.getAttrColor(attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}

fun launchInAppBrowser(context: Context, url: String) {
    val builder = CustomTabsIntent.Builder()
    val intent = builder.build().intent
    intent.data = Uri.parse(url)
    context.startActivitySafe(intent)
}

fun Fragment.showError(error: TalerErrorInfo) {
    @Suppress("OPT_IN_USAGE")
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    val message = json.encodeToString(error)
    showError(message)
}

fun FragmentActivity.showError(error: TalerErrorInfo) {
    @Suppress("OPT_IN_USAGE")
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    val message = json.encodeToString(error)
    showError(message)
}

fun Context.getThemeColor(attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

@Composable
fun <T> T.useDebounce(
    delayMillis: Long = 300L,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    onChange: suspend (T) -> Unit
): T{
    val state by rememberUpdatedState(this)

    DisposableEffect(state){
        val job = coroutineScope.launch {
            delay(delayMillis)
            onChange(state)
        }
        onDispose {
            job.cancel()
        }
    }

    return state
}

@Composable
fun BottomInsetsSpacer() = Spacer(
    Modifier.windowInsetsBottomHeight(
        WindowInsets.systemBars,
    ),
)

@Composable
fun Modifier.systemBarsPaddingBottom() =
    windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))

@Composable
fun Modifier.systemBarsPaddingHorizontal() =
    windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))

@Composable
fun Modifier.systemBarsPaddingAllExceptTop() =
    windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))