package com.celoext

import com.celoext.api.EventHub.Companion.nextBackoff
import com.celoext.api.SseFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The SSE reader used to inline its frame assembly, so a regression there would only show up as a
 * chat that quietly stops updating. These cover the framing rules the server relies on.
 */
class SseFramesTest {
    private fun feed(vararg lines: String): List<String> {
        val frames = SseFrames()
        return lines.mapNotNull { frames.line(it) }
    }

    @Test fun aDataLineIsDeliveredWhenTheFrameCloses() {
        assertEquals(listOf("""{"type":"session.idle"}"""), feed("data: {\"type\":\"session.idle\"}", ""))
    }

    @Test fun everyDataLineOfAFrameIsJoinedWithANewline() {
        assertEquals(listOf("line one\nline two"), feed("data: line one", "data: line two", ""))
    }

    @Test fun otherFieldsAndCommentsAreIgnored() {
        // `event:`, `id:` and `retry:` carry nothing the app reads; a comment is a keepalive
        assertEquals(listOf("payload"), feed(": keepalive", "event: message", "id: 42", "retry: 3000", "data: payload", ""))
    }

    @Test fun aBlankLineWithoutDataProducesNothing() {
        assertNull(SseFrames().line(""))
    }

    @Test fun twoFramesInOneReadAreDeliveredSeparately() {
        assertEquals(listOf("a", "b"), feed("data: a", "", "data: b", ""))
    }

    @Test fun anUnterminatedFrameIsDroppedWhenTheConnectionEnds() {
        // the reader makes a new SseFrames per connection, so half a frame cannot leak into the next one
        assertNull(SseFrames().line("data: half"))
    }

    @Test fun theReconnectDelayDoublesAndStopsAtEightSeconds() {
        var wait = 500L
        val seen = mutableListOf<Long>()
        repeat(6) { wait = nextBackoff(wait); seen += wait }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 8000L, 8000L), seen)
    }
}
