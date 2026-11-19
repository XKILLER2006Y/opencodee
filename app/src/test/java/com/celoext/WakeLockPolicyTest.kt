package com.celoext

import com.celoext.runtime.needsWakeLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wake lock used to be taken when the service started and held until the user pressed Stop:
 * a phone next to a charging cable overnight kept its CPU up with nothing running. It now follows
 * work, so the cases that must and must not hold it are pinned here.
 */
class WakeLockPolicyTest {
    @Test fun anIdleServerDoesNotHoldTheCpu() {
        assertFalse(needsWakeLock(keepAwake = true, starting = false, busySessions = 0, sendsInFlight = 0))
    }

    @Test fun startingTheServerNeedsTheCpu() {
        assertTrue(needsWakeLock(keepAwake = true, starting = true, busySessions = 0, sendsInFlight = 0))
    }

    @Test fun aBusySessionNeedsTheCpu() {
        assertTrue(needsWakeLock(keepAwake = true, starting = false, busySessions = 1, sendsInFlight = 0))
        assertTrue(needsWakeLock(keepAwake = true, starting = false, busySessions = 3, sendsInFlight = 0))
    }

    @Test fun aPromptInFlightNeedsTheCpu() {
        // the gap between Send and the first busy event: nothing looks busy yet, but work started
        assertTrue(needsWakeLock(keepAwake = true, starting = false, busySessions = 0, sendsInFlight = 1))
    }

    @Test fun theSwitchWinsOverEverything() {
        assertFalse(needsWakeLock(keepAwake = false, starting = true, busySessions = 2, sendsInFlight = 1))
    }
}
