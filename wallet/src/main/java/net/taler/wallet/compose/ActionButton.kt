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

package net.taler.wallet.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ActionButton(
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    labelColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = if (enabled) onClick else {{}},
            shape = shape,
            containerColor = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            content = {
                icon()
            },
        )

        Spacer(modifier = Modifier.height(6.dp))

        ProvideTextStyle(
            MaterialTheme.typography.labelLarge
                .copy(color = if (enabled) labelColor else Color.Gray, textAlign = TextAlign.Center)
        ) {
            label()
        }
    }
}