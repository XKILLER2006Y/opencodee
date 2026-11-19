package com.celoext

import com.celoext.runtime.bundleNeedsInstall
import com.celoext.runtime.debNeedsInstall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two install steps used to trust "the file is there" as proof that the step finished.
 *
 * The OpenCode bundle was never replaced once its launcher existed, so a new OCT_VERSION shipped
 * with an app update stayed on disk forever. A Termux package was skipped as soon as one key file
 * existed, so an install killed mid-extraction was treated as complete and the missing libraries
 * were never restored.
 */
class RuntimeInstallTest {
    private fun bundle(
        binExists: Boolean = true,
        indexExists: Boolean = true,
        hasPlaceholders: Boolean = false,
        installed: String? = "2.2.7",
        wanted: String = "2.2.7",
    ) = bundleNeedsInstall(binExists, indexExists, hasPlaceholders, installed, wanted)

    @Test fun aCurrentBundleIsKept() {
        assertFalse(bundle())
    }

    @Test fun aNewerVersionReplacesTheOldBundle() {
        assertTrue(bundle(installed = "2.2.7", wanted = "2.3.0"))
    }

    @Test fun aBundleFromBeforeTheMarkerIsReplaced() {
        assertTrue(bundle(installed = null))
    }

    @Test fun aMissingLauncherIsInstalled() {
        assertTrue(bundle(binExists = false))
    }

    @Test fun aBundleWithoutItsEntryPointIsInstalled() {
        assertTrue(bundle(indexExists = false))
    }

    @Test fun aHalfPatchedBundleIsInstalled() {
        assertTrue(bundle(hasPlaceholders = true))
    }

    @Test fun aFinishedPackageIsSkipped() {
        assertFalse(debNeedsInstall(markerVersion = "abc123", wantedVersion = "abc123", keyFileExists = true))
    }

    @Test fun aPackageWithoutAMarkerIsUnpackedAgain() {
        // killed during extraction: the key file is there but the unpack never finished
        assertTrue(debNeedsInstall(markerVersion = null, wantedVersion = "abc123", keyFileExists = true))
    }

    @Test fun aNewerPackageReplacesTheOldOne() {
        assertTrue(debNeedsInstall(markerVersion = "old", wantedVersion = "abc123", keyFileExists = true))
    }

    @Test fun aDeletedKeyFileTriggersAnotherUnpack() {
        assertTrue(debNeedsInstall(markerVersion = "abc123", wantedVersion = "abc123", keyFileExists = false))
    }
}
