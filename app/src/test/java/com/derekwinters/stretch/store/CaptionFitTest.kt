package com.derekwinters.stretch.store

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirement IDs refer to docs/spec/store-listing.md. The limits on rendered line counts; the
 * counts themselves come from [CaptionMeasureTest].
 */
class CaptionFitTest {

    private val caption = Caption("STORE-SHOT-001", "Headline", "Supporting")

    // STORE-SHOT-012: up to 3 headline lines and 2 supporting lines fit.
    @Test
    fun captionAtTheLimitsFits() {
        CaptionFit.require(caption, CaptionLines(headline = 3, supporting = 2))
        CaptionFit.require(caption, CaptionLines(headline = 1, supporting = 1))
    }

    // STORE-SHOT-014 invariant: a 4th headline line fails rather than shrinking the text.
    @Test
    fun fourHeadlineLinesFail() {
        val error = assertThrows(AssertionError::class.java) {
            CaptionFit.require(caption, CaptionLines(headline = 4, supporting = 1))
        }
        assertTrue(error.message!!, error.message!!.contains("STORE-SHOT-001"))
        assertTrue(error.message!!, error.message!!.contains("headline"))
    }

    // STORE-SHOT-014 invariant: a 3rd supporting line fails rather than pushing the frame down.
    @Test
    fun threeSupportingLinesFail() {
        val error = assertThrows(AssertionError::class.java) {
            CaptionFit.require(caption, CaptionLines(headline = 2, supporting = 3))
        }
        assertTrue(error.message!!, error.message!!.contains("supporting"))
    }
}
