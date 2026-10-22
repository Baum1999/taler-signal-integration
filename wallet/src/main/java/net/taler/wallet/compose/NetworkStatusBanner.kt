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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.backend.ProgressPhase
import net.taler.wallet.main.RequestProgress
import net.taler.wallet.ui.theme.TalerTheme

@Composable
fun NetworkStatusBanner(
    offline: Boolean,
    progress: RequestProgress?,
) {
    when {
        offline -> StatusBanner(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(stringResource(R.string.offline_banner))
        }

        progress != null -> StatusBanner(
            containerColor = TalerTheme.extraColors.warningContainer,
            contentColor = TalerTheme.extraColors.onWarningContainer,
        ) {
            Text(stringResource(R.string.request_progress_slow))
            RetryStatus(progress)
        }
    }
}

@Composable
private fun StatusBanner(
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides MaterialTheme.typography.bodyLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .animateContentSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
private fun RetryStatus(progress: RequestProgress) {
    val text = when {
        progress.retrying -> R.string.request_progress_retrying
        progress.phase == ProgressPhase.Stalled -> R.string.request_progress_waiting
        else -> return
    }
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelMediumEmphasized,
        )
    }
}

@Preview
@Composable
private fun NetworkStatusBannerOfflinePreview() {
    TalerTheme {
        TalerSurface {
            NetworkStatusBanner(offline = true, progress = null)
        }
    }
}

@Preview
@Composable
private fun NetworkStatusBannerSlowPreview() {
    TalerTheme {
        TalerSurface {
            NetworkStatusBanner(
                offline = false,
                progress = RequestProgress(
                    operation = "waitTransactionState",
                    progressToken = "txn:payment:1",
                    phase = ProgressPhase.Delayed,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun NetworkStatusBannerStalledPreview() {
    TalerTheme {
        TalerSurface {
            NetworkStatusBanner(
                offline = false,
                progress = RequestProgress(
                    operation = "waitTransactionState",
                    progressToken = "txn:payment:1",
                    phase = ProgressPhase.Stalled,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun NetworkStatusBannerRetryingPreview() {
    TalerTheme {
        TalerSurface {
            NetworkStatusBanner(
                offline = false,
                progress = RequestProgress(
                    operation = "waitTransactionState",
                    progressToken = "txn:payment:1",
                    phase = ProgressPhase.Stalled,
                    retrying = true,
                ),
            )
        }
    }
}
