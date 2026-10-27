package com.derekwinters.stretch.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requirement IDs refer to docs/spec/store-listing.md. */
class CaptionsFileTest {

    // STORE-SHOT-007 / STORE-GRAPHIC-003: docs/store/captions.md holds exactly one entry per
    // shot, in listing order, then the feature graphic's text, and nothing else.
    @Test
    fun captionFileHasTheFiveShotIdsThenTheFeatureGraphic() {
        val ids = Captions.load().map { it.id }
        assertEquals(
            listOf(
                "STORE-SHOT-001",
                "STORE-SHOT-002",
                "STORE-SHOT-003",
                "STORE-SHOT-004",
                "STORE-SHOT-005",
                "STORE-GRAPHIC-003",
            ),
            ids,
        )
        assertEquals(StoreShot.entries.map { it.id } + FeatureGraphicSpec.TEXT_ID, ids)
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

    // STORE-GRAPHIC-003: a line's trailing tag is split off as the IDs it cites and is not part
    // of the text; a line without one cites nothing.
    @Test
    fun splitsTrailingTagsFromTheText() {
        val parsed = Captions.parse(
            """
            ## STORE-GRAPHIC-003

            Headline: Your stretch routine, on your schedule <!-- SCHED-001, SCHED-002 -->

            Supporting: Reminders and goals.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Caption(
                    "STORE-GRAPHIC-003",
                    "Your stretch routine, on your schedule",
                    "Reminders and goals.",
                    headlineCites = listOf("SCHED-001", "SCHED-002"),
                ),
            ),
            parsed,
        )
    }

    // STORE-GRAPHIC-003: a malformed tag or a comment that is not at the end of the line is an
    // error, not text.
    @Test
    fun rejectsMalformedTags() {
        assertThrows(IllegalStateException::class.java) {
            Captions.parse("## STORE-GRAPHIC-003\n\nHeadline: A <!-- not an id -->\n\nSupporting: B\n")
        }
        assertThrows(IllegalStateException::class.java) {
            Captions.parse("## STORE-GRAPHIC-003\n\nHeadline: A <!-- SCHED-001 --> more\n\nSupporting: B\n")
        }
    }

    // STORE-GRAPHIC-003: both of the feature graphic's lines are claims, each with a tag citing
    // only IDs a spec page defines and has not retired.
    @Test
    fun featureGraphicTextCitesDefinedRequirements() {
        val text = Captions.featureGraphic()
        assertTrue("the headline has no <!-- ID --> tag", text.headlineCites.isNotEmpty())
        assertTrue("the supporting line has no <!-- ID --> tag", text.supportingCites.isNotEmpty())
        SpecIds.requireCitable("${text.id} headline", text.headlineCites)
        SpecIds.requireCitable("${text.id} supporting line", text.supportingCites)
    }

    // STORE-GRAPHIC-003 (STORE-LIST-003's rule): an ID no spec page defines, or one the spec has
    // retired, cannot be cited.
    @Test
    fun undefinedOrRetiredIdsCannotBeCited() {
        val defined = SpecIds.load()
        assertEquals(false, defined["SCHED-001"])
        assertEquals(true, defined["NOTIF-004"])
        val undefined = assertThrows(AssertionError::class.java) {
            SpecIds.requireCitable("test", listOf("SCHED-999"), defined)
        }
        assertTrue(undefined.message!!, undefined.message!!.contains("SCHED-999"))
        val retired = assertThrows(AssertionError::class.java) {
            SpecIds.requireCitable("test", listOf("NOTIF-004"), defined)
        }
        assertTrue(retired.message!!, retired.message!!.contains("retired"))
    }
}
