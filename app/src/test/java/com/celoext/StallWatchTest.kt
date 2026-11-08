package com.celoext

import com.celoext.data.stallDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A run that stops producing events must raise one "stuck" alert, but a long tool, a pending
 * permission or a question must not be mistaken for a stall.
 */
class StallWatchTest {
    private val noTool = 5 * 60_000L
    private val tool = 15 * 60_000L

    @Test fun quietModelRunAlertsAfterFiveMinutes() {
        assertFalse(stallDue(noTool - 1, 0, null, waiting = false, alerted = false))
        assertTrue(stallDue(noTool, 0, null, waiting = false, alerted = false))
    }

    @Test fun aRunningToolGetsFifteenMinutes() {
        assertFalse(stallDue(noTool, 0, toolSince = 0, waiting = false, alerted = false))
        assertFalse(stallDue(tool - 1, 0, toolSince = 0, waiting = false, alerted = false))
        assertTrue(stallDue(tool, 0, toolSince = 0, waiting = false, alerted = false))
    }

    @Test fun waitingForTheUserIsNotAStall() {
        assertFalse(stallDue(60 * 60_000L, 0, null, waiting = true, alerted = false))
        assertFalse(stallDue(60 * 60_000L, 0, toolSince = 0, waiting = true, alerted = false))
    }

    @Test fun oneAlertPerRun() {
        assertFalse(stallDue(60 * 60_000L, 0, null, waiting = false, alerted = true))
    }

    @Test fun freshOutputRearmsTheClock() {
        assertFalse(stallDue(noTool + 1, noTool, null, waiting = false, alerted = false))
    }
}
