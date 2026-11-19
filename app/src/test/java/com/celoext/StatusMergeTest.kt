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
    @Test fun withoutLiveEventsTheSnapshotWins() {
        val current = mapOf("ses_a" to "busy")
        val fetched = mapOf("ses_b" to "busy")
        assertEquals(fetched, mergeStatuses(current, fetched, unchanged = true))
    }

    @Test fun aSessionThatWentBusyDuringTheFetchKeepsItsLiveStatus() {
        val current = mapOf("ses_a" to "busy")
        val fetched = emptyMap<String, String>()
        assertEquals(mapOf("ses_a" to "busy"), mergeStatuses(current, fetched, unchanged = false))
    }

    @Test fun aSessionThatWentIdleDuringTheFetchIsNotRevived() {
        val current = emptyMap<String, String>()
        val fetched = mapOf("ses_a" to "busy")
        assertEquals(emptyMap<String, String>(), mergeStatuses(current, fetched, unchanged = false))
    }

    @Test fun sessionsWithNoLiveEventStillComeFromTheFetch() {
        val current = mapOf("ses_a" to "busy")
        val fetched = mapOf("ses_b" to "busy")
        assertEquals(mapOf("ses_a" to "busy", "ses_b" to "busy"), mergeStatuses(current, fetched, unchanged = false))
    }
}
