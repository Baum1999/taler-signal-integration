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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.taler.wallet.backend.MigrateDatabaseResponse
import net.taler.wallet.backend.NotificationPayload
import net.taler.wallet.backend.TalerErrorCode
import net.taler.wallet.backend.TalerErrorInfo
import net.taler.wallet.backend.WalletDatabaseBackend
import net.taler.wallet.backend.WalletRunConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun nativeDatabasePreferenceIsSerialized() {
        val config = WalletRunConfig(
            features = WalletRunConfig.Features(
                enableV1Contracts = true,
                useNativeDb = true,
            ),
            logLevel = "INFO",
        )

        val encoded = json.encodeToString(config)

        assertTrue(encoded.contains("\"useNativeDb\":true"))
        assertFalse(encoded.contains("migrateNativeDb"))
    }

    @Test
    fun migrationResponseIsDecoded() {
        val response = json.decodeFromString(
            MigrateDatabaseResponse.serializer(),
            """{"migrated":true,"databaseBackend":"sqlite"}""",
        )

        assertTrue(response.migrated)
        assertEquals(WalletDatabaseBackend.Sqlite, response.databaseBackend)
        assertEquals(DatabaseMigrationState.Complete, response.toDatabaseMigrationState())
    }

    @Test
    fun maintenanceProgressIsDecodedWithOptionalFields() {
        val payload = json.decodeFromString(
            NotificationPayload.serializer(),
            """
                {
                  "type":"database-maintenance-progress",
                  "operation":"indexeddb-to-native-migration",
                  "phase":"copy",
                  "progressToken":"migration-1",
                  "completionPercent":42,
                  "completedSteps":4,
                  "totalSteps":10
                }
            """.trimIndent(),
        ) as NotificationPayload.DatabaseMaintenanceProgress

        assertEquals("migration-1", payload.progressToken)
        assertEquals(42, payload.completionPercent)
        assertEquals(null, payload.error)
    }

    @Test
    fun progressOnlyAcceptsTheActiveMigrationToken() {
        val state = DatabaseMigrationState.Migrating("active", 10)
        val stale = NotificationPayload.DatabaseMaintenanceProgress(
            operation = "indexeddb-to-native-migration",
            phase = "copy",
            progressToken = "stale",
            completionPercent = 75,
        )
        val unrelated = stale.copy(
            operation = "indexeddb-fixup",
            progressToken = "active",
        )
        val active = stale.copy(
            progressToken = "active",
            completionPercent = 150,
        )

        assertSame(state, state.withProgress(stale))
        assertSame(state, state.withProgress(unrelated))
        assertEquals(
            DatabaseMigrationState.Migrating("active", 100),
            state.withProgress(active),
        )
    }

    @Test
    fun cancellationErrorDefersWithoutReportingFailure() {
        val state = DatabaseMigrationState.Cancelling("active", 35)
        val cancellation = TalerErrorInfo(
            code = TalerErrorCode.WALLET_CORE_REQUEST_CANCELLED,
        )

        assertEquals(DatabaseMigrationState.Deferred, state.withMigrationError(cancellation))
    }

    @Test
    fun aCompletedMigrationWinsTheCancellationRace() {
        val response = MigrateDatabaseResponse(
            migrated = true,
            databaseBackend = WalletDatabaseBackend.Sqlite,
        )

        assertEquals(DatabaseMigrationState.Complete, response.toDatabaseMigrationState())
    }

    @Test
    fun ordinaryMigrationErrorsRemainVisible() {
        val state = DatabaseMigrationState.Migrating("active", 35)
        val error = TalerErrorInfo(code = TalerErrorCode.WALLET_DB_UNAVAILABLE)

        assertEquals(DatabaseMigrationState.Failed(error), state.withMigrationError(error))
    }
}
