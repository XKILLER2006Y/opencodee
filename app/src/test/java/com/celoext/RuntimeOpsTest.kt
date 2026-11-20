package com.celoext

import com.celoext.runtime.RuntimeOps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Install and reset write the same directories. A reset that starts mid-install deletes the files
 * the install is still creating, and the install then marks the runtime ready with nothing behind
 * it: state says stopped, starting the server fails, and the restart loop runs until the user
 * notices. The two operations now refuse to overlap.
 */
class RuntimeOpsTest {
    @Test fun aResetCannotStartWhileAnInstallRuns() {
        val ops = RuntimeOps()
        assertTrue(ops.beginInstall())
        assertFalse(ops.beginReset())
        assertTrue(ops.installing)
        assertFalse(ops.resetting)
    }

    @Test fun anInstallCannotStartWhileAResetRuns() {
        val ops = RuntimeOps()
        assertTrue(ops.beginReset())
        assertFalse(ops.beginInstall())
        assertTrue(ops.resetting)
        assertFalse(ops.installing)
    }

    @Test fun bothOpenAgainAfterTheyFinish() {
        val ops = RuntimeOps()
        assertTrue(ops.beginInstall())
        ops.endInstall()
        assertTrue(ops.beginReset())
        ops.endReset()
        assertTrue(ops.beginInstall())
    }

    @Test fun aSecondStartOfTheSameOperationIsRefused() {
        val ops = RuntimeOps()
        assertTrue(ops.beginInstall())
        assertFalse(ops.beginInstall())
        ops.endInstall()
        assertTrue(ops.beginInstall())
    }
}
