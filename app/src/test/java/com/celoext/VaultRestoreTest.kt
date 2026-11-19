package com.celoext

import com.celoext.data.shouldRestoreTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restoring the vault runs on every start and is allowed to overwrite the live config. Two rules
 * matter: a working file is never replaced by an empty or older copy, and a missing or placeholder
 * target is filled from the vault so keys survive a reset.
 */
class VaultRestoreTest {
    @Test fun aMissingTargetIsFilled() {
        assertTrue(shouldRestoreTarget(targetExists = false, targetEmpty = true, targetModified = 0, vaultModified = 0, vaultUseful = true))
    }

    @Test fun aMissingTargetIsNotFilledWithAnEmptyCopy() {
        assertFalse(shouldRestoreTarget(targetExists = false, targetEmpty = true, targetModified = 0, vaultModified = 0, vaultUseful = false))
    }

    @Test fun aPlaceholderNeverOverwritesWorkingKeys() {
        assertFalse(shouldRestoreTarget(targetExists = true, targetEmpty = false, targetModified = 200, vaultModified = 100, vaultUseful = false))
    }

    @Test fun aNewerVaultCopyWins() {
        assertTrue(shouldRestoreTarget(targetExists = true, targetEmpty = false, targetModified = 100, vaultModified = 200, vaultUseful = true))
    }

    @Test fun aUsefulVaultCopyReplacesAPlaceholder() {
        assertTrue(shouldRestoreTarget(targetExists = true, targetEmpty = true, targetModified = 300, vaultModified = 100, vaultUseful = true))
    }

    @Test fun anOlderVaultCopyLeavesAWorkingTargetAlone() {
        assertFalse(shouldRestoreTarget(targetExists = true, targetEmpty = false, targetModified = 300, vaultModified = 100, vaultUseful = true))
    }
}
