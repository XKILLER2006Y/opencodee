package com.celoext

import com.celoext.api.ApiException
import com.celoext.api.isRetryableCall
import com.celoext.api.retryDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A single dropped packet used to fail a screen load, and the list stayed empty until the user
 * pulled to refresh. Reads are now repeated, but only when repeating them cannot make things worse.
 */
class ApiRetryTest {
    @Test fun aDroppedReadIsRetried() {
        assertTrue(isRetryableCall("GET", IOException("unexpected end of stream")))
        assertTrue(isRetryableCall("GET", SocketTimeoutException("timeout")))
    }

    @Test fun aWriteIsNeverRetried() {
        // a prompt, a permission answer, a config patch: running these twice would be a bug
        assertFalse(isRetryableCall("POST", IOException("timeout")))
        assertFalse(isRetryableCall("PATCH", SocketTimeoutException()))
        assertFalse(isRetryableCall("DELETE", IOException("reset")))
    }

    @Test fun aRefusedOrUnresolvableConnectionIsNotRetried() {
        // three connect timeouts in a row would stall the screen for 15 s instead of 5
        assertFalse(isRetryableCall("GET", ConnectException("refused")))
        assertFalse(isRetryableCall("GET", UnknownHostException("phone offline")))
    }

    @Test fun anErrorAnswerFromTheAppIsFinal() {
        assertFalse(isRetryableCall("GET", ApiException(500, "boom")))
        assertFalse(isRetryableCall("GET", ApiException(429, "slow down")))
    }

    @Test fun aGatewayErrorDuringStartupIsRetried() {
        assertTrue(isRetryableCall("GET", ApiException(502, "bad gateway")))
        assertTrue(isRetryableCall("GET", ApiException(503, "starting")))
        assertTrue(isRetryableCall("GET", ApiException(504, "gateway timeout")))
    }

    @Test fun theWaitsStayShort() {
        assertEquals(250L, retryDelay(1))
        assertEquals(500L, retryDelay(2))
    }
}
