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

package net.taler.merchantpos.payment

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import net.taler.merchantpos.R
import net.taler.merchantpos.MainActivity
import net.taler.merchantpos.compose.PosTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter

class PaymentSuccessFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PaymentSuccessScreen(
                onContinue = {
                    (requireActivity() as MainActivity).apply {
                        navigateBack()
                        navigateBack()
                    }
                },
            )
        }
    }
}

@Composable
private fun PaymentSuccessScreen(
    onContinue: () -> Unit,
) {
    PosTheme {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val verticalPadding = (maxHeight * 0.045f).coerceIn(16.dp, 32.dp)
            val heroSpacing = (maxHeight * 0.035f).coerceIn(12.dp, 24.dp)
            val iconSize = (maxHeight * 0.22f).coerceIn(84.dp, 164.dp)
            val buttonMinHeight = (maxHeight * 0.11f).coerceIn(52.dp, 72.dp)
            val buttonWidthFraction = if (maxWidth < 600.dp) 0.78f else 0.6f
            val titleStyle = when {
                maxHeight < 560.dp -> MaterialTheme.typography.titleMedium
                maxHeight < 720.dp -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.headlineMedium
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = verticalPadding, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(heroSpacing),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = stringResource(R.string.payment_received),
                            modifier = Modifier.fillMaxWidth(),
                            style = titleStyle,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth(buttonWidthFraction)
                        .heightIn(min = buttonMinHeight),
                ) {
                    Text(
                        text = stringResource(R.string.payment_back_button),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
