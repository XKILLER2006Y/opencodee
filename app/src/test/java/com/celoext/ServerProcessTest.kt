package com.celoext

import com.celoext.runtime.isServerCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port cleanup kills processes by command line. A server started through su runs as root, so the
 * app can't read its /proc entry; the old scan saw an empty command line and left exactly that
 * process holding the port. The pidof path now reads the command line through su, and this
 * predicate decides what may be killed: other bun processes on the phone must never match.
 */
class ServerProcessTest {
    private val bin = "/data/user/0/com.celoext/files/native/opencode/bin/opencode"

    @Test fun theBunBundleProcessMatches() {
        assertTrue(isServerCommand("bun $bin/../lib/opencode/index.js serve --port 4096", bin))
        assertTrue(isServerCommand("bun /data/user/0/com.celoext/files/native/opencode/lib/opencode/index.js serve", bin))
    }

    @Test fun theLauncherMatches() {
        assertTrue(isServerCommand("$bin serve --hostname 127.0.0.1 --port 4096", bin))
    }

    @Test fun theProotEraBinaryMatches() {
        assertTrue(isServerCommand("/usr/bin/proot --rootfs=/data/rootfs /usr/local/bin/opencode serve", bin))
    }

    @Test fun anotherBunProcessDoesNotMatch() {
        assertFalse(isServerCommand("bun /data/data/com.termux/files/home/script.js", bin))
        assertFalse(isServerCommand("node /opt/9router/app/custom-server.js", bin))
    }

    @Test fun anEmptyCommandLineDoesNotMatch() {
        assertFalse(isServerCommand("", bin))
    }
}
