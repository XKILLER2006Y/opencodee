package com.celoext

import com.celoext.data.mergeStatuses
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A refresh reads the status of every directory over the network while live events keep arriving.
 * Naively replacing the map with the fetched snapshot dropped a status event that arrived during
 * the fetch: the chat showed "idle" (send button, no stop) while the agent was still working, or
 * kept showing "working" after the reply had finished.
 */
class StatusMergeTest {
    private val busyA = mapOf("ses_a" to "busy")

    @Test fun withoutLiveEventsTheSnapshotWins() {
        val fetched = mapOf("ses_b" to "busy")
        assertEquals(fetched, mergeStatuses(busyA, fetched, emptyMap()))
    }

    @Test fun aSessionThatWentBusyDuringTheFetchKeepsItsLiveStatus() {
        assertEquals(busyA, mergeStatuses(busyA, emptyMap(), mapOf("ses_a" to 5L)))
    }

    @Test fun aSessionThatWentIdleDuringTheFetchIsNotRevived() {
        // "idle" means the id is absent from the live map, so a stale "busy" from the snapshot must not come back
        assertEquals(emptyMap<String, String>(), mergeStatuses(emptyMap(), busyA, mapOf("ses_a" to 5L)))
    }

    @Test fun sessionsWithNoLiveEventStillComeFromTheFetch() {
        val fetched = mapOf("ses_b" to "busy")
        assertEquals(busyA + fetched, mergeStatuses(busyA, fetched, mapOf("ses_a" to 5L)))
    }

    @Test fun aSnapshotCannotBringBackASessionThatWasNotTouched() {
        // nothing arrived live: the snapshot is the truth, including a session that is gone now
        assertEquals(mapOf("ses_b" to "busy"), mergeStatuses(mapOf("ses_gone" to "busy"), mapOf("ses_b" to "busy"), emptyMap()))
    }
}
