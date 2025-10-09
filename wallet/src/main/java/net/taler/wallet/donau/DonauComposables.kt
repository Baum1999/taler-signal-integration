/*
 * This file is part of GNU Taler
 * (C) 2025 Taler Systems S.A.
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

package net.taler.wallet.donau

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.cleanExchange
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.compose.WarningLabel
import net.taler.wallet.payment.DonauStatus

@Composable
fun DonauToggle(
    donauStatus: DonauStatus,
    useDonau: Boolean = false,
    onToggleDonau: (Boolean) -> Unit,
) {
    if (donauStatus !is DonauStatus.Unavailable) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(9.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.donau_toggle_label),
                )

                Switch(
                    checked = useDonau,
                    onCheckedChange = onToggleDonau,
                )
            }

            if (useDonau) when (donauStatus) {
                is DonauStatus.Mismatch -> {
                    WarningLabel(stringResource(
                        R.string.donau_warning_mismatch,
                        cleanExchange(donauStatus.donauInfo.donauBaseUrl)))
                }

                is DonauStatus.Unset -> {
                    WarningLabel(stringResource(R.string.donau_warning_unset))
                }

                // no need to show anything
                else -> {}
            }
        }
    }
}

@Composable
fun DonauSelector(
    donauStatus: DonauStatus,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onSetup: (donauBaseUrl: String) -> Unit,
) {
    var selectedDonauIndex by remember { mutableIntStateOf(0) }

    val donauUrls = when(donauStatus) {
        is DonauStatus.Mismatch -> donauStatus.donauUrls
        is DonauStatus.Unset -> donauStatus.donauUrls
        else -> return
    }

    if (showDialog) AlertDialog(
        title = { Text(stringResource(R.string.donau_selector_title)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(donauUrls) { i, url ->
                    DonauSelectorItem(
                        donauBaseUrl = url,
                        selected = i == selectedDonauIndex,
                        onSelected = { selectedDonauIndex = i },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSetup(donauUrls[selectedDonauIndex])
                onDismiss()
            }) {
                Text(stringResource(R.string.donau_selector_confirm))
            }
        },
        dismissButton = {
            TextButton (onClick = { onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        },
        onDismissRequest = {
            onDismiss()
        },
    )
}

@Composable
fun DonauSelectorItem(
    donauBaseUrl: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(Modifier
        .padding(bottom = 5.dp)
        .fillMaxWidth()
        .clickable { onSelected() },
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected,
            onClick = onSelected,
        )

        Text(
            modifier = Modifier.weight(1f),
            text = cleanExchange(donauBaseUrl)
        )
    }
}

@Preview
@Composable
fun DonauSelectorPreview() {
    TalerSurface {
        val donauInfo = DonauInfo(donauBaseUrl = "https://donau.test.taler.net/", taxPayerId = "1234")
        val donauUrls = listOf(
            "https://donau.head.taler.net/",
            "https://donau.test.taler.net/",
            "https://donau.demo.taler.net/",
        )

        var donauStatus: DonauStatus by remember { mutableStateOf(DonauStatus.Unset(donauUrls)) }

        Column(Modifier.fillMaxSize()) {
            Button(onClick = { donauStatus = DonauStatus.Unset(donauUrls) }) {
                Text("DonauStatus.Unset")
            }

            Button(onClick = { donauStatus = DonauStatus.Mismatch(donauInfo, donauUrls) }) {
                Text("DonauStatus.Mismatch")
            }
        }

        DonauSelector(donauStatus,
            showDialog = donauStatus !is DonauStatus.Unavailable,
            onDismiss = { donauStatus = DonauStatus.Unavailable },
            onSetup = { donauStatus = DonauStatus.Unavailable }
        )
    }
}