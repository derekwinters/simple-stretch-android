package com.derekwinters.stretch.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requirement IDs refer to docs/spec/store-listing.md. */
class CaptionsFileTest {

    // STORE-SHOT-007: docs/store/captions.md holds exactly one entry per shot, and nothing else.
    @Test
    fun captionFileHasExactlyTheFiveShotIds() {
        val ids = Captions.load().map { it.id }
        assertEquals(
            listOf("STORE-SHOT-001", "STORE-SHOT-002", "STORE-SHOT-003", "STORE-SHOT-004", "STORE-SHOT-005"),
            ids,
        )
        assertEquals(StoreShot.entries.map { it.id }, ids)
    }

    // STORE-SHOT-007: every entry has a headline and a supporting line.
    @Test
    fun everyCaptionHasBothLines() {
        Captions.load().forEach {
            assertTrue("${it.id} headline is blank", it.headline.isNotBlank())
            assertTrue("${it.id} supporting line is blank", it.supporting.isNotBlank())
        }
    }

    // STORE-SHOT-007: the parser reads the text as written, including non-ASCII characters.
    @Test
    fun parsesHeadlineAndSupportingLines() {
        val parsed = Captions.parse(
            """
            # Title

            Intro text, ignored.

            ## STORE-SHOT-003

            Headline: Set a daily goal for each stretch

            Supporting: Tap + or − and it saves.
            """.trimIndent(),
        )
        assertEquals(listOf(Caption("STORE-SHOT-003", "Set a daily goal for each stretch", "Tap + or − and it saves.")), parsed)
    }

    // STORE-SHOT-007: an entry missing a line, or a shot listed twice, is an error, not a blank caption.
    @Test
    fun rejectsIncompleteOrDuplicateEntries() {
        assertThrows(IllegalStateException::class.java) {
            Captions.parse("## STORE-SHOT-001\n\nHeadline: Only a headline\n")
        }
        assertThrows(IllegalStateException::class.java) {
            Captions.parse(
                "## STORE-SHOT-001\n\nHeadline: A\n\nSupporting: B\n\n" +
                    "## STORE-SHOT-001\n\nHeadline: C\n\nSupporting: D\n",
            )
        }
    }
}
