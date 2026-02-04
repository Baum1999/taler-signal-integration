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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.common.Amount
import net.taler.wallet.BottomInsetsSpacer
import net.taler.wallet.R
import net.taler.wallet.compose.QrCodeUriComposable
import net.taler.wallet.compose.TalerSurface
import net.taler.wallet.transactions.AmountType
import net.taler.wallet.transactions.TransactionAmountComposable
import net.taler.wallet.transactions.TransactionInfoComposable

@Composable
fun DonauStatementComposable(
    statements: List<DonauStatement>,
    selectedIndex: Int,
    onSelectIndex: (index: Int) -> Unit,
) {
    val statement = remember(selectedIndex) {
        statements[selectedIndex]
    }

    // TODO: create common instruction + QR composable
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = CenterHorizontally,
    ) {
        // only show tab row if more than one year
        if (statements.size > 1) ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 8.dp,
        ) {
            statements.forEachIndexed { i, statement ->
                Tab(
                    selected = selectedIndex == i,
                    onClick = { onSelectIndex(i) },
                    text = {
                        Text(statement.year.toString())
                    }
                )
            }
        }

        Text(
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            text = stringResource(R.string.donau_statement_instruction),
            textAlign = TextAlign.Center,
        )

        QrCodeUriComposable(
            talerUri = statement.uri,
            clipBoardLabel = "Donau",
            buttonText = stringResource(id = R.string.copy),
            shareAsQrCode = true,
        )

        TransactionAmountComposable(
            label = stringResource(id = R.string.amount_total),
            amount = statement.total,
            amountType = AmountType.Neutral,
        )

        TransactionInfoComposable(
            label = stringResource(R.string.donau_statement_legal_domain),
            info = statement.legalDomain,
        )

        val host = statement.host
        if (host != null) TransactionInfoComposable(
            label = stringResource(R.string.donau_statement_tax_authority),
            info = host,
            marquee = true,
        )

        BottomInsetsSpacer()
    }
}

@Preview
@Composable
fun DonauStatementComposablePreview() {
    TalerSurface {
        var selectedIndex by remember { mutableIntStateOf(0) }
        DonauStatementComposable(
            statements = listOf(
                DonauStatement(
                    total = Amount.fromJSONString("KUDOS:0.1"),
                    year = 2025,
                    legalDomain = "Gnuland",
                    uri = "donau://donau.test.taler.net/",
                    donationStatementSig = "123456",
                    donauPub = "123456",
                ),
                DonauStatement(
                    total = Amount.fromJSONString("KUDOS:100.0"),
                    year = 2024,
                    legalDomain = "Gnuland",
                    uri = "donau://donau.test.taler.net/",
                    donationStatementSig = "123456",
                    donauPub = "123456",
                ),
            ),
            selectedIndex = selectedIndex,
            onSelectIndex = { selectedIndex = it },
        )
    }
}