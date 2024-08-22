/*
 * This file is part of GNU Taler
 * (C) 2024 Taler Systems S.A.
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

package net.taler.wallet.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableCard(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    setExpanded: (expanded: Boolean) -> Unit,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "Rotation state of expand icon button",
    )

    OutlinedCard(
        modifier = modifier.padding(8.dp),
        onClick = { setExpanded(!expanded) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize() // edit animation here
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end  = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                header()

                IconButton(
                    modifier = Modifier.rotate(rotationState),
                    onClick = { setExpanded(!expanded) }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Drop Down Arrow"
                    )
                }
            }

            if (expanded) {
                content()
            }
        }
    }
}

@Preview
@Composable
fun ExpandableCardPreview() {
    TalerSurface {
        var expanded by remember { mutableStateOf(true) }
        ExpandableCard(
            expanded = expanded,
            setExpanded = { expanded = it },
            header = { Text("Swiss QR") },
            content = {
                QrCodeUriComposable(
                    talerUri = "taler://withdraw-exchange",
                    clipBoardLabel = "",
                    showContents = false,
                )
            }
        )
    }
}