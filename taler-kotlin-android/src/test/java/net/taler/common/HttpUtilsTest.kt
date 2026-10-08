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

package net.taler.common

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpUtilsTest {
    @Test
    fun closingOwnerDoesNotCloseSharedClientOrNextOwner() = runBlocking {
        val shared = getDefaultHttpClient(followRedirect = true, logging = false)
        val first = createDefaultHttpClient(followRedirect = true, logging = false)
        try {
            assertNotSame(shared, first)
            first.close()
            first.coroutineContext[Job]!!.join()

            val reopened = createDefaultHttpClient(followRedirect = true, logging = false)
            try {
                assertNotSame(first, reopened)
                assertNotSame(first.engine, reopened.engine)
                assertTrue(reopened.coroutineContext[Job]!!.isActive)
                assertTrue(reopened.engine.coroutineContext[Job]!!.isActive)
                assertTrue(shared.coroutineContext[Job]!!.isActive)
                assertSame(shared, getDefaultHttpClient(followRedirect = true, logging = false))
            } finally {
                reopened.close()
            }
        } finally {
            first.close()
        }
    }
}
