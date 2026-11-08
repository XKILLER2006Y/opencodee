package com.celoext

import com.celoext.data.finishNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The notification that reaches the lock screen must say what actually happened, not "done" for a broken run. */
class FinishNoteTest {
    @Test fun normalReplyUsesTheSessionTitleAndTheReply() {
        val n = finishNote(reply = "Fixed the build.", error = null, completed = true, sessionTitle = "Fix CI")
        assertEquals("Fix CI", n.title)
        assertEquals("Fixed the build.", n.text)
        assertEquals("Fix CI", n.sessionTitle)
    }

    @Test fun aSessionWithoutATitleFallsBackToOpenCode() {
        val n = finishNote(reply = null, error = null, completed = true, sessionTitle = "")
        assertEquals("OpenCode", n.title)
        assertEquals("The task is done", n.text)
        assertNull(n.sessionTitle)
    }

    @Test fun anErrorWinsOverEverything() {
        val n = finishNote(reply = "partial", error = "The model returned 500", completed = false, sessionTitle = "Chat")
        assertEquals("OpenCode hit an error", n.title)
        assertEquals("The model returned 500", n.text)
    }

    @Test fun aCutOffReplyIsNotReportedAsDone() {
        val n = finishNote(reply = "I was saying", error = null, completed = false, sessionTitle = "Chat")
        assertEquals("OpenCode stopped", n.title)
        assertEquals("The reply was cut off before it finished. Tap to review.", n.text)
    }

    @Test fun noReplyYetStillCountsAsDone() {
        val n = finishNote(reply = null, error = null, completed = true, sessionTitle = "Chat")
        assertEquals("Chat", n.title)
        assertEquals("The task is done", n.text)
    }
}
