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

package net.taler.wallet.main

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import net.taler.wallet.R
import net.taler.wallet.compose.DemandAttention
import net.taler.wallet.compose.GridMenu
import net.taler.wallet.compose.GridMenuItem
import net.taler.wallet.compose.Material3MenuGroup
import net.taler.wallet.compose.Material3MenuItemData
import net.taler.wallet.compose.TalerSurface
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TalerActionButton(
    demandAttention: Boolean,
    onShowSheet: () -> Unit,
    onScanQr: () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.actions)) } },
        state = tooltipState,
    ) {
        val offsetY = remember { Animatable(0f) }
        var cancelled by remember { mutableStateOf(false) }

        DemandAttention(demandAttention = demandAttention) {
            LargeFloatingActionButton(
                modifier = Modifier
                    .requiredSize(86.dp)
                    .padding(8.dp)
                    .offset { IntOffset(0, offsetY.value.roundToInt() / 6) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            runBlocking { offsetY.snapTo(offsetY.value + delta) }
                            if (delta > 0) {
                                cancelled = true
                            }
                        },
                        onDragStopped = {
                            offsetY.animateTo(0.0f)
                            if (!cancelled) {
                                onScanQr()
                            }
                            cancelled = false
                        },
                    ),
                shape = CircleShape,
                onClick = { onShowSheet() },
            ) {
                if (offsetY.value == 0.0f) {
                    Icon(
                        painterResource(R.drawable.ic_actions),
                        modifier = Modifier.size(38.dp),
                        contentDescription = stringResource(R.string.actions),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_scan_qr),
                        contentDescription = stringResource(R.string.actions),
                    )
                }
            }
        }
    }

    LaunchedEffect(demandAttention) {
        if (demandAttention) {
            tooltipState.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalerActionsModal(
    showSheet: Boolean,
    sheetState: SheetState,
    selectedCurrency: String? = null,
    showShopping: Boolean,
    disableActions: Boolean,
    disablePeer: Boolean,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onScanQr: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEnterUri: () -> Unit,
    onShoppingDiscovery: () -> Unit,
) {
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column {
                if (showShopping && selectedCurrency != null) {
                    Box(Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 9.dp)) {
                        Material3MenuGroup(items = buildList {
                            add(
                                Material3MenuItemData(
                                    title = { Text(stringResource(R.string.exchange_shopping_label, selectedCurrency)) },
                                    icon = { Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null
                                    ) },
                                    onClick = onShoppingDiscovery,
                                )
                            )
                        })
                    }
                }

                GridMenu(
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 16.dp + WindowInsets
                            .systemBars
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                ) {
                    GridMenuItem(
                        icon = R.drawable.ic_link,
                        title = R.string.enter_uri,
                        onClick = { onEnterUri(); onDismiss() },
                    )

                    GridMenuItem(
                        icon = R.drawable.transaction_deposit,
                        title = R.string.send_deposit_button_label,
                        onClick = { onDeposit(); onDismiss() },
                        enabled = !disableActions
                    )

                    GridMenuItem(
                        icon = R.drawable.ic_scan_qr,
                        title = R.string.button_scan_qr_code_label,
                        onClick = { onScanQr(); onDismiss() },
                    )

                    GridMenuItem(
                        icon = R.drawable.transaction_p2p_incoming,
                        title = R.string.transactions_receive_funds,
                        onClick = { onReceive(); onDismiss() },
                        enabled = !disableActions && !disablePeer,
                    )

                    GridMenuItem(
                        icon = R.drawable.transaction_withdrawal,
                        title = R.string.withdraw_button_label,
                        onClick = { onWithdraw(); onDismiss() },
                        enabled = !disableActions,
                    )

                    GridMenuItem(
                        icon = R.drawable.transaction_p2p_outgoing,
                        title = R.string.transactions_send_funds,
                        onClick = { onSend(); onDismiss() },
                        enabled = !disableActions && !disablePeer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun TalerActionsModalPreview() {
    TalerSurface {
        TalerActionsModal(
            showSheet = true,
            sheetState = rememberModalBottomSheetState(),
            selectedCurrency = "CHF",
            showShopping = true,
            disableActions = false,
            disablePeer = false,
            onDismiss = {},
            onSend = {},
            onReceive = {},
            onScanQr = {},
            onDeposit = {},
            onWithdraw = {},
            onEnterUri = {},
            onShoppingDiscovery = {},
        )
    }
}