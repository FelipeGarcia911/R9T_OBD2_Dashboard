package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private val policy = ReconnectPolicy()

    @Test
    fun retriesALinkThatWasLiveUntilTheLimit() {
        assertTrue(policy.canRetry(DiagnosticError.CONNECTION_LOST, everLive = true, reconnectsSoFar = 0))
        assertTrue(policy.canRetry(DiagnosticError.TIMEOUT, everLive = true, reconnectsSoFar = MAX_RECONNECTS - 1))
        assertFalse(policy.canRetry(DiagnosticError.CONNECTION_LOST, everLive = true, reconnectsSoFar = MAX_RECONNECTS))
    }

    @Test
    fun reportsAFirstConnectionThatNeverWentLive() {
        assertFalse(policy.canRetry(DiagnosticError.ADAPTER_UNREACHABLE, everLive = false, reconnectsSoFar = 0))
    }

    @Test
    fun neverRetriesWhatOnlyTheRiderCanFix() {
        listOf(
            DiagnosticError.PERMISSION_DENIED,
            DiagnosticError.BLUETOOTH_DISABLED,
            DiagnosticError.LOG_WRITE_FAILED,
        ).forEach { error ->
            assertFalse(error.name, policy.canRetry(error, everLive = true, reconnectsSoFar = 0))
        }
    }

    @Test
    fun waitsAsTheBackoffSays() {
        assertEquals(4_000L, policy.delayBefore(3))
        assertEquals(7L, ReconnectPolicy(backoffMillis = { it + 4L }).delayBefore(3))
    }
}
