package com.celoext

import com.celoext.runtime.shouldMigrateLegacyFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving from the proot runtime to the native one copies the old /root into the native home. The
 * old code used `copyRecursively(overwrite = false)`: Vault.restore() runs on the same start and
 * fills the native home first, so its older backup of auth.json, the chat database and AGENTS.md
 * silently won over the live files. The copy also stopped at the first error but still wrote the
 * "done" marker, leaving the rest behind for good.
 */
class LegacyHomeTest {
    @Test fun aMissingFileIsCopied() {
        assertTrue(shouldMigrateLegacyFile(targetExists = false, targetLength = 0, targetModified = 0, sourceLength = 40, sourceModified = 100))
    }

    @Test fun aNewerLiveFileWinsOverTheBackup() {
        assertTrue(shouldMigrateLegacyFile(targetExists = true, targetLength = 40, targetModified = 100, sourceLength = 40, sourceModified = 200))
    }

    @Test fun aNewerNativeFileIsKept() {
        assertFalse(shouldMigrateLegacyFile(targetExists = true, targetLength = 40, targetModified = 200, sourceLength = 40, sourceModified = 100))
    }

    @Test fun aTruncatedTargetIsCopiedAgain() {
        // a killed app left half a file; its timestamp is newer, but the length gives it away
        assertTrue(shouldMigrateLegacyFile(targetExists = true, targetLength = 12, targetModified = 200, sourceLength = 40, sourceModified = 100))
    }
}
