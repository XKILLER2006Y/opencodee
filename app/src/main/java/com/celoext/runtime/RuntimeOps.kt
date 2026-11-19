package com.celoext.runtime

/**
 * Serializes install and reset: both write the same directories, and a reset that starts in the
 * middle of an install deletes files the install is still creating. A reset that did that left
 * `runtimeReady` true with no bundle on disk, and every later start crashed and restarted.
 *
 * The flags are volatile because the UI and the service read them outside the lock.
 */
internal class RuntimeOps {
    @Volatile var installing = false
        private set
    @Volatile var resetting = false
        private set

    @Synchronized fun beginInstall(): Boolean {
        if (installing || resetting) return false
        installing = true
        return true
    }

    @Synchronized fun endInstall() {
        installing = false
    }

    @Synchronized fun beginReset(): Boolean {
        if (installing || resetting) return false
        resetting = true
        return true
    }

    @Synchronized fun endReset() {
        resetting = false
    }
}
