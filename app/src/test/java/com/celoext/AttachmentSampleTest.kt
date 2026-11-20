package com.celoext

import com.celoext.data.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Image attachments are decoded into an ARGB_8888 bitmap, so the decode size is the app's largest
 * single allocation. Sampling used to aim at twice the target edge, which decoded a 12 MP photo at
 * full size: 48 MB, plus a second bitmap to scale it. These pin the budget and, with it, the
 * screenshot case that must not be halved.
 */
class AttachmentSampleTest {
    private val budget = 4_000_000L

    private fun pixels(w: Int, h: Int, s: Int) = (w.toLong() / s) * (h.toLong() / s)

    @Test fun aTwelveMegapixelPhotoIsSampledInHalf() {
        assertEquals(2, sampleSizeFor(4032, 3024))
        assertTrue(pixels(4032, 3024, 2) <= budget)
    }

    @Test fun aTallScreenshotKeepsItsFullWidth() {
        // 2400x1080 is 2.6 MP: inside the budget, so no sampling and the text stays readable
        assertEquals(1, sampleSizeFor(2400, 1080))
    }

    @Test fun aFortyEightMegapixelPhotoIsSampledByFour() {
        assertEquals(4, sampleSizeFor(8000, 6000))
        assertTrue(pixels(8000, 6000, 4) <= budget)
    }

    @Test fun aSmallImageIsNotTouched() {
        assertEquals(1, sampleSizeFor(640, 480))
    }

    @Test fun everySizeStaysWithinTheBudget() {
        var w = 200
        while (w <= 6000) {
            var h = 200
            while (h <= 6000) {
                val s = sampleSizeFor(w, h)
                assertTrue("${w}x$h sampled by $s", pixels(w, h, s) <= budget)
                h += 700
            }
            w += 700
        }
    }
}
