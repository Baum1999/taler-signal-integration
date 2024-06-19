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

package net.taler.wallet.transactions

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.wallet.R
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.TransactionMajorState.Aborted
import net.taler.wallet.transactions.TransactionMajorState.Aborting
import net.taler.wallet.transactions.TransactionMajorState.Done
import net.taler.wallet.transactions.TransactionMajorState.Expired
import net.taler.wallet.transactions.TransactionMajorState.Failed
import net.taler.wallet.transactions.TransactionMajorState.Suspended
import net.taler.wallet.transactions.TransactionMajorState.Pending

@Composable
fun TransactionStateComposable(
    modifier: Modifier = Modifier,
    state: TransactionState,
) {
    val message = when (state.major) {
        Pending -> stringResource(R.string.transaction_state_pending)
        Aborted -> stringResource(R.string.transaction_state_aborted)
        Aborting -> stringResource(R.string.transaction_state_aborting)
        Suspended -> stringResource(R.string.transaction_state_suspended)
        Failed -> stringResource(R.string.transaction_state_failed)
        Expired -> stringResource(R.string.transaction_state_expired)
        else -> return
    }

    val cardColor = when (state.major) {
        Aborted, Aborting, Failed, Expired -> MaterialTheme.colorScheme.errorContainer
        Pending, Suspended -> MaterialTheme.colorScheme.surfaceVariant
        else -> return
    }

    val textColor = when (state.major) {
        Aborted, Aborting, Failed, Expired -> MaterialTheme.colorScheme.onErrorContainer
        Pending, Suspended -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }

    Card(
        modifier = modifier
            .padding(horizontal = 9.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
        ),
        shape = ShapeDefaults.ExtraSmall,
    ) {
        Text(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            text = message,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
fun TransactionStateComposablePreview() {
    TalerSurface {
        Column {
            val modifier = Modifier.padding(vertical = 6.dp)
            TransactionStateComposable(modifier, state = TransactionState(Pending))
            TransactionStateComposable(modifier, state = TransactionState(Aborted))
            TransactionStateComposable(modifier, state = TransactionState(Aborting))
            TransactionStateComposable(modifier, state = TransactionState(Suspended))
            TransactionStateComposable(modifier, state = TransactionState(Failed))
            TransactionStateComposable(modifier, state = TransactionState(Expired))
            TransactionStateComposable(modifier, state = TransactionState(Done))
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TransactionStateComposableNightPreview() {
    TransactionStateComposablePreview()
}