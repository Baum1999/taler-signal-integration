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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun DemandAttention(content: @Composable () -> Unit) {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(400)

        offsetX.animateTo(
            targetValue = -5f,
            animationSpec = tween(90, easing = LinearEasing),
        )

        offsetX.animateTo(
            targetValue = 5f,
            animationSpec = repeatable(
                iterations = 5,
                animation = tween(80, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            )
        )

        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(90, easing = LinearEasing),
        )
    }

    Box(Modifier.offset(offsetX.value.dp, 0.dp)) {
        content()
    }
}