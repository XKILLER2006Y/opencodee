package com.celoext

import com.celoext.api.ModelRef
import com.celoext.api.Session
import com.celoext.api.TimeInfo
import com.celoext.api.Tokens
import com.celoext.data.decodeSessions
import com.celoext.data.encodeSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last session list is shown on a cold start while the local server boots, so the cache has to
 * survive a round trip exactly and must never put a partial or corrupt list on screen.
 */
class SessionCacheTest {
    private val chat = Session(
        id = "ses_1",
        title = "Fix the wake lock",
        directory = "/root/work",
        time = TimeInfo(created = 1000, updated = 2000),
        model = ModelRef(id = "anthropic", modelID = "claude", providerID = "anthropic"),
        tokens = Tokens(input = 10, output = 20, total = 30),
    )

    @Test fun aSessionListSurvivesTheRoundTrip() {
        assertEquals(listOf(chat), decodeSessions(encodeSessions(listOf(chat))))
    }

    @Test fun anEmptyListIsFine() {
        assertEquals(emptyList<Session>(), decodeSessions(encodeSessions(emptyList())))
    }

    @Test fun aCorruptCacheIsNoCache() {
        assertTrue(decodeSessions("not json").isEmpty())
        assertTrue(decodeSessions("""[{"id":}]""").isEmpty())
    }

    @Test fun anOlderFileWithExtraFieldsStillLoads() {
        val json = """[{"id":"ses_2","title":"Old","unknown":true,"time":{"created":1,"updated":2}}]"""
        val back = decodeSessions(json)
        assertEquals(1, back.size)
        assertEquals("Old", back[0].title)
        assertEquals(2L, back[0].time.updated)
    }
}
