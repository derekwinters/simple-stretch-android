package com.derekwinters.stretch.store

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Captions are measured as the screenshot
 * draws them: real text layout (native graphics) at the caption's width, font and size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [StoreShotDevice.SDK], qualifiers = StoreShotDevice.QUALIFIERS, application = Application::class)
class CaptionMeasureTest {

    private fun measure(headline: String, supporting: String = "Short.") =
        CaptionMeasure.lines(ApplicationProvider.getApplicationContext(), Caption("STORE-SHOT-001", headline, supporting))

    // STORE-SHOT-012: a short caption is one headline line and one supporting line.
    @Test
    fun shortCaptionIsOneLineEach() {
        assertEquals(CaptionLines(headline = 1, supporting = 1), measure("Stretch now"))
    }

    // STORE-SHOT-014 invariant: the limit is on rendered lines, not characters. Sixty narrow
    // letters fit in the headline's 3 lines; sixty wide ones do not.
    @Test
    fun linesAreMeasuredNotCounted() {
        val narrow = measure("i".repeat(60))
        val wide = measure("W".repeat(60))
        assertTrue("narrow: $narrow", narrow.headline <= CaptionFit.MAX_HEADLINE_LINES)
        assertTrue("wide: $wide", wide.headline > CaptionFit.MAX_HEADLINE_LINES)
    }

    // STORE-SHOT-014 invariant: a caption that is too long fails instead of shrinking or moving.
    @Test
    fun tooLongCaptionFails() {
        val headline = "Stretch reminders that fit your day, every day, at home, at work and anywhere else you go"
        val caption = Caption("STORE-SHOT-001", headline, "Short.")
        assertThrows(AssertionError::class.java) {
            CaptionFit.require(caption, CaptionMeasure.lines(ApplicationProvider.getApplicationContext(), caption))
        }

        val supporting = "Today's reminders and goal progress at a glance, with every stretch you have done so far, " +
            "your streak, your weekly and monthly scores, your schedules and much, much more besides."
        val longSupporting = Caption("STORE-SHOT-001", "Short", supporting)
        assertThrows(AssertionError::class.java) {
            CaptionFit.require(longSupporting, CaptionMeasure.lines(ApplicationProvider.getApplicationContext(), longSupporting))
        }
    }

    // STORE-SHOT-012: every committed caption fits.
    @Test
    fun committedCaptionsFit() {
        Captions.load().forEach { caption ->
            CaptionFit.require(caption, CaptionMeasure.lines(ApplicationProvider.getApplicationContext(), caption))
        }
    }
}
