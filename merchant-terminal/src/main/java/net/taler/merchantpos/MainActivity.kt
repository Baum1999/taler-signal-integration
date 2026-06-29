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
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.taler.lib.android.TalerNfcService
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.config.Config
import net.taler.merchantpos.config.ConfigFetcherFragment
import net.taler.merchantpos.config.ConfigFragment
import net.taler.merchantpos.config.GeneralSettingsFragment
import net.taler.merchantpos.history.HistoryFragment
import net.taler.merchantpos.order.OrderFragment
import net.taler.merchantpos.amount.AmountEntryFragment
import net.taler.merchantpos.payment.PaymentSuccessFragment
import net.taler.merchantpos.payment.ProcessPaymentFragment
import net.taler.merchantpos.refund.RefundFragment
import net.taler.merchantpos.refund.RefundUriFragment
import net.taler.merchantpos.order.RestartState
import net.taler.merchantpos.order.RestartState.DISABLED
import net.taler.merchantpos.order.RestartState.UNDO

class MainActivity : AppCompatActivity() {

    private val model: MainViewModel by viewModels()

    private var navController: NavHostController? = null
    private var reallyExit = false

    companion object {
        const val TAG = "taler-pos"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TalerNfcService.startService(this)

        model.paymentManager.payment.observe(this) { payment ->
            payment?.talerPayUri?.let {
                TalerNfcService.setUri(this, it)
            } ?: run {
                TalerNfcService.clearNdefPayload(this)
            }
        }

        model.configManager.sessionExpired.observe(this) {
            showPosError(R.string.session_expired_toast)
            navigateTo(PosDestination.Config, clearBackStack = true)
        }

        setContent {
            PosTheme {
                MerchantTerminalApp(
                    viewModel = model,
                    startDestination = determineStartDestination(),
                    onNavControllerReady = { navController = it },
                    onExitRequested = ::handleExitRequest,
                )
            }
        }

        handleSetupIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!model.configManager.config.isValid()) {
            navigateTo(PosDestination.Config, clearBackStack = true)
        } else if (model.configManager.merchantConfig == null || model.configManager.currency == null) {
            navigateTo(PosDestination.ConfigFetcher)
        } else {
            model.configManager.refreshConfigInBackground()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSetupIntent(intent)
    }

    fun navigateTo(destination: PosDestination, clearBackStack: Boolean = false) {
        val controller = navController ?: return
        controller.navigate(destination.route) {
            launchSingleTop = true
            if (clearBackStack) {
                popUpTo(controller.graph.id) {
                    inclusive = true
                }
            }
        }
    }

    fun navigateBack() {
        val controller = navController ?: return
        if (!controller.popBackStack()) {
            finish()
        }
    }

    fun navigateToInitialOrderScreen() {
        navigateTo(model.configManager.initialDestination(), clearBackStack = true)
    }

    fun handleSetupIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != "taler-pos") return

        val host = data.host ?: return
        val pathSegments = data.pathSegments
        val pathStyleInstance = pathSegments
            .takeIf { it.size >= 2 && it[0].equals("instances", ignoreCase = true) }
            ?.get(1)
            ?.takeIf(String::isNotBlank)
        val rawFragment = data.fragment?.removePrefix("/")?.trim().orEmpty()
        val params = rawFragment
            .takeIf { '=' in it }
            ?.split('&')
            ?.associate { part ->
                part.split('=', limit = 2).let { it[0] to Uri.decode(it.getOrElse(1) { "" }) }
            }

        val instance = pathStyleInstance ?: params?.get("username") ?: return
        val token = if (pathStyleInstance != null && params == null) {
            Uri.decode(rawFragment).takeIf(String::isNotBlank)
        } else {
            params?.get("password")
        } ?: return

        val merchantUrl = Uri.Builder()
            .scheme("https")
            .encodedAuthority(host)
            .appendPath("instances")
            .appendPath(instance)
            .build()
            .toString()

        val newConfig = Config.New(
            merchantUrl = merchantUrl,
            accessToken = token,
            savePassword = true,
        )

        Log.d("MainActivity", "Config URL: $merchantUrl")
        model.configManager.config = newConfig
        navigateTo(PosDestination.ConfigFetcher)
    }

    private fun determineStartDestination(): PosDestination {
        return when {
            !model.configManager.config.isValid() -> PosDestination.Config
            model.configManager.merchantConfig == null || model.configManager.currency == null ->
                PosDestination.ConfigFetcher
            else -> model.configManager.initialDestination()
        }
    }

    private fun handleExitRequest(currentRoute: String?) {
        if (currentRoute == PosDestination.Order.route || currentRoute == PosDestination.AmountEntry.route) {
            if (reallyExit) {
                super.onBackPressedDispatcher.onBackPressed()
            } else {
                reallyExit = true
                Toast.makeText(this, R.string.toast_back_to_exit, LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ reallyExit = false }, 3000)
            }
        } else if (currentRoute == PosDestination.Settings.route || currentRoute == PosDestination.Config.route) {
            if (!model.configManager.config.isValid()) {
                startActivity(Intent(ACTION_MAIN).apply {
                    addCategory(CATEGORY_HOME)
                    flags = FLAG_ACTIVITY_NEW_TASK
                })
            } else {
                navigateBack()
            }
        } else {
            navigateBack()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantTerminalApp(
    viewModel: MainViewModel,
    startDestination: PosDestination,
    onNavControllerReady: (NavHostController) -> Unit,
    onExitRequested: (String?) -> Unit,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentOrderId by viewModel.orderManager.currentOrderId.observeAsState()
    val currentOrderLive = remember(currentOrderId) { currentOrderId?.let { viewModel.orderManager.getOrder(it) } }
    val currentOrderState = currentOrderLive?.order?.observeAsState()
    val currentOrder = currentOrderState?.value
    val restartState by currentOrderLive?.restartState?.observeAsState(DISABLED) ?: remember { androidx.compose.runtime.mutableStateOf(DISABLED) }
    val clearOrderEnabled =
        restartState != DISABLED || currentOrder?.products?.isNotEmpty() == true
    val hasPreviousOrder = currentOrderId?.let { viewModel.orderManager.hasPreviousOrder(it) } ?: false
    val hasNextOrder by currentOrderId?.let { viewModel.orderManager.hasNextOrder(it).observeAsState(false) }
        ?: remember { androidx.compose.runtime.mutableStateOf(false) }
    val preferredInitialDestination by viewModel.configManager.initialOrderScreenLiveData.observeAsState(
        viewModel.configManager.initialOrderScreen
    )
    val context = LocalContext.current
    val hasValidConfig = viewModel.configManager.config.isValid()
    val lockMerchantSettingsNavigation =
        !hasValidConfig &&
            (currentRoute == PosDestination.Settings.route || currentRoute == PosDestination.Config.route)
    val screenTitle = when (currentRoute) {
        PosDestination.Order.route -> currentOrder?.let {
            stringResource(R.string.order_label_title, it.title)
        } ?: stringResource(R.string.menu_order)
        else -> stringResource(currentRoute.titleResId())
    }
    val reloadingMessage = stringResource(R.string.toast_reloading)

    val drawerItems = listOf(
        PosDestination.AmountEntry,
        PosDestination.Order,
        PosDestination.History,
        PosDestination.Settings,
    ).let { items ->
        val preferredItem = when (preferredInitialDestination) {
            net.taler.merchantpos.config.InitialOrderScreen.AmountEntry -> PosDestination.AmountEntry
            net.taler.merchantpos.config.InitialOrderScreen.Inventory -> PosDestination.Order
            null -> viewModel.configManager.initialDestination()
        }
        if (preferredItem in items) {
            listOf(preferredItem) + items.filterNot { it == preferredItem }
        } else {
            items
        }
    }

    LaunchedEffect(navController) {
        onNavControllerReady(navController)
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            onExitRequested(currentRoute)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !lockMerchantSettingsNavigation,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_talerpos_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    drawerItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    painter = painterResource(item.drawerIconResId()),
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelResId()),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                            },
                            selected = currentRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            badge = null,
                            colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                if (currentRoute == PosDestination.Order.route) {
                    Surface(shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            if (!lockMerchantSettingsNavigation) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = null)
                                }
                            } else {
                                Spacer(modifier = Modifier.size(48.dp))
                            }
                            Text(
                                text = screenTitle,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { currentOrderLive?.restartOrUndo() },
                                    enabled = clearOrderEnabled,
                                    colors = topBarOrderButtonColors(),
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Text(
                                        if (restartState == UNDO) {
                                            stringResource(R.string.order_undo)
                                        } else {
                                            stringResource(R.string.order_restart)
                                        },
                                    )
                                }
                                Button(
                                    onClick = { if (hasPreviousOrder) viewModel.orderManager.previousOrder() },
                                    enabled = hasPreviousOrder,
                                    colors = topBarOrderButtonColors(),
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Text(stringResource(R.string.order_previous))
                                }
                                Button(
                                    onClick = { if (hasNextOrder) viewModel.orderManager.nextOrder() },
                                    enabled = hasNextOrder,
                                    colors = topBarOrderButtonColors(),
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Text(stringResource(R.string.order_next))
                                }
                                Button(
                                    onClick = {
                                        viewModel.configManager.reloadConfig()
                                        Toast.makeText(
                                            context,
                                            reloadingMessage,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                    colors = topBarOrderButtonColors(),
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Text(stringResource(R.string.menu_reload))
                                }
                            }
                        }
                    }
                } else if (currentRoute == PosDestination.History.route) {
                    Surface(shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            if (!lockMerchantSettingsNavigation) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = null)
                                }
                            } else {
                                Spacer(modifier = Modifier.size(48.dp))
                            }
                            Text(
                                text = screenTitle,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Button(
                                onClick = { viewModel.historyManager.fetchHistory() },
                                colors = topBarOrderButtonColors(),
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) {
                                Text(stringResource(R.string.history_refresh))
                            }
                        }
                    }
                } else {
                    Surface(shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            if (!lockMerchantSettingsNavigation) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = null)
                                }
                            } else {
                                Spacer(modifier = Modifier.size(48.dp))
                            }
                            Text(
                                text = screenTitle,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(PosDestination.AmountEntry.route) {
                    FragmentScreenHost("amount-entry") { AmountEntryFragment() }
                }
                composable(PosDestination.Order.route) {
                    FragmentScreenHost("order") { OrderFragment() }
                }
                composable(PosDestination.History.route) {
                    FragmentScreenHost("history") { HistoryFragment() }
                }
                composable(PosDestination.Settings.route) {
                    FragmentScreenHost("settings") { GeneralSettingsFragment() }
                }
                composable(PosDestination.Config.route) {
                    FragmentScreenHost("config") { ConfigFragment() }
                }
                composable(PosDestination.ConfigFetcher.route) {
                    FragmentScreenHost("config-fetcher") { ConfigFetcherFragment() }
                }
                composable(PosDestination.ProcessPayment.route) {
                    FragmentScreenHost("process-payment") { ProcessPaymentFragment() }
                }
                composable(PosDestination.PaymentSuccess.route) {
                    FragmentScreenHost("payment-success") { PaymentSuccessFragment() }
                }
                composable(PosDestination.Refund.route) {
                    FragmentScreenHost("refund") { RefundFragment() }
                }
                composable(PosDestination.RefundUri.route) {
                    FragmentScreenHost("refund-uri") { RefundUriFragment() }
                }
            }
        }
    }
}

@Composable
private fun topBarOrderButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun FragmentScreenHost(
    routeTag: String,
    createFragment: () -> Fragment,
) {
    val activity = LocalActivity.current as? MainActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    val containerId = remember(routeTag) { View.generateViewId() }
    val fragmentTag = remember(routeTag, containerId) { "$routeTag-$containerId" }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                id = containerId
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        },
    )

    DisposableEffect(fragmentTag) {
        if (fragmentManager.findFragmentByTag(fragmentTag) == null) {
            fragmentManager.commitNow {
                replace(containerId, createFragment(), fragmentTag)
            }
        }
        onDispose {
            fragmentManager.findFragmentByTag(fragmentTag)?.let { fragment ->
                if (fragment.isAdded && !fragmentManager.isStateSaved) {
                    fragmentManager.commitNow {
                        remove(fragment)
                    }
                }
            }
        }
    }
}

private fun PosDestination.labelResId(): Int = when (this) {
    PosDestination.AmountEntry -> R.string.menu_amount_entry
    PosDestination.Order -> R.string.menu_order
    PosDestination.History -> R.string.menu_history
    PosDestination.Settings -> R.string.menu_settings
    else -> R.string.app_name_short
}

private fun PosDestination.drawerIconResId(): Int = when (this) {
    PosDestination.AmountEntry -> R.drawable.ic_dialpad
    PosDestination.Order -> R.drawable.ic_move_money_24dp
    PosDestination.History -> R.drawable.ic_history_black_24dp
    PosDestination.Settings -> R.drawable.ic_menu_manage
    else -> R.drawable.ic_move_money_24dp
}

private fun String?.titleResId(): Int = when (this) {
    PosDestination.AmountEntry.route -> R.string.menu_amount_entry
    PosDestination.Order.route -> R.string.menu_order
    PosDestination.History.route -> R.string.menu_history
    PosDestination.Settings.route -> R.string.menu_settings
    PosDestination.Config.route -> R.string.config_label
    PosDestination.ConfigFetcher.route -> R.string.config_fetching_label
    PosDestination.ProcessPayment.route -> R.string.payment_process_label
    PosDestination.PaymentSuccess.route -> R.string.payment_received
    PosDestination.Refund.route, PosDestination.RefundUri.route -> R.string.history_refund
    else -> R.string.app_name_short
}
